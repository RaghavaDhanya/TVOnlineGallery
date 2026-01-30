package `in`.ragv.onlinegallery.data.repository

import android.content.Context
import android.util.Log
import `in`.ragv.onlinegallery.data.api.DriveItemResponse
import `in`.ragv.onlinegallery.data.api.GraphApiClient
import `in`.ragv.onlinegallery.data.auth.AuthManager
import `in`.ragv.onlinegallery.data.cache.CacheManager
import `in`.ragv.onlinegallery.data.models.Album
import `in`.ragv.onlinegallery.data.models.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Repository for accessing OneDrive data via Microsoft Graph API
 */
class OneDriveRepository(context: Context) {

    private val authManager = AuthManager(context)
    private val cacheManager = CacheManager(context)
    private var graphClient: GraphApiClient? = null

    companion object {
        private const val TAG = "OneDriveRepository"

        /**
         * Root folder path in OneDrive for the gallery
         * Change this to match your OneDrive folder structure
         * Examples:
         * - "Gallery" - looks for /Gallery folder in root
         * - "Pictures/Gallery" - looks for /Pictures/Gallery
         * - "" - uses root folder
         */
        const val ROOT_FOLDER_PATH = "Gallery"

        /**
         * Supported image MIME types
         */
        private val IMAGE_MIME_TYPES = setOf(
            "image/jpeg", "image/jpg", "image/png", "image/gif",
            "image/bmp", "image/webp", "image/heic", "image/heif"
        )

        /**
         * Supported video MIME types
         */
        private val VIDEO_MIME_TYPES = setOf(
            "video/mp4", "video/mpeg", "video/quicktime", "video/x-msvideo",
            "video/x-matroska", "video/webm", "video/3gpp"
        )
    }

    /**
     * Initialize the repository
     * Automatically attempts to refresh token if expired
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // getAccessToken() will automatically refresh if needed
            val token = authManager.getAccessToken()
            if (token != null) {
                graphClient = GraphApiClient(token)
                // Test the connection
                val testResult = graphClient?.testConnection()
                testResult?.isSuccess == true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            false
        }
    }

    /**
     * Check if user is authenticated
     */
    suspend fun isAuthenticated(): Boolean {
        return authManager.isAuthenticated()
    }

    /**
     * Check if user has stored credentials (may need token refresh)
     */
    fun hasCredentials(): Boolean {
        return authManager.hasCredentials()
    }

    /**
     * Start the device code authentication flow
     * Returns info to display to the user
     */
    suspend fun startSignIn(): Result<AuthManager.DeviceCodeInfo> {
        Log.d(TAG, "startSignIn() called")
        val result = authManager.startDeviceCodeFlow()
        Log.d(TAG, "startDeviceCodeFlow result: ${result.isSuccess}")
        if (result.isFailure) {
            Log.e(TAG, "startDeviceCodeFlow failed", result.exceptionOrNull())
        }
        return result
    }

    /**
     * Poll for authentication completion
     * @param deviceCode The device code from startSignIn()
     * @param interval Polling interval in seconds
     */
    suspend fun completeSignIn(deviceCode: String, interval: Int = 5): Result<Boolean> {
        val result = authManager.pollForToken(deviceCode, interval)
        if (result.isSuccess) {
            // Initialize the Graph client with the new token (auto-refresh not needed here as token is fresh)
            val token = authManager.getAccessToken()
            if (token != null) {
                graphClient = GraphApiClient(token)
            }
        }
        return result
    }

    /**
     * Get list of albums (folders) from OneDrive
     * Each folder in the root path becomes an album
     * Returns a Flow that emits cached data first (if available), then fresh data from API
     */
    fun getAlbums(): Flow<List<Album>> = flow {
        // First, emit cached data if available
        val cachedAlbums = cacheManager.getCachedAlbums()
        if (cachedAlbums != null && cachedAlbums.isNotEmpty()) {
            Log.d(TAG, "Emitting ${cachedAlbums.size} cached albums")
            emit(cachedAlbums)
        }

        // Then fetch fresh data from API
        try {
            val client = graphClient ?: run {
                Log.e(TAG, "Graph client not initialized")
                if (cachedAlbums == null) {
                    emit(emptyList())
                }
                return@flow
            }

            // Get children of the root folder path
            val result = client.getFolderChildren(ROOT_FOLDER_PATH)

            if (result.isFailure) {
                Log.e(TAG, "Failed to get albums", result.exceptionOrNull())
                if (cachedAlbums == null) {
                    emit(emptyList())
                }
                return@flow
            }

            val response = result.getOrNull()
            if (response == null) {
                if (cachedAlbums == null) {
                    emit(emptyList())
                }
                return@flow
            }

            // Filter for folders only and map to Album objects
            val freshAlbums = response.items
                .filter { it.folder != null } // Only folders
                .map { item ->
                    Album(
                        id = item.id,
                        name = item.name,
                        thumbnailUrl = item.thumbnails?.firstOrNull()?.large?.url,
                        itemCount = item.folder?.childCount ?: 0
                    )
                }

            // Save to cache
            cacheManager.saveAlbums(freshAlbums)

            // Emit fresh data
            Log.d(TAG, "Emitting ${freshAlbums.size} fresh albums from API")
            emit(freshAlbums)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting albums", e)
            if (cachedAlbums == null) {
                emit(emptyList())
            }
        }
    }

    /**
     * Get media items (photos and videos) from an album
     * @param albumId The ID of the album (folder)
     * Returns a Flow that emits cached data first (if available), then fresh data from API
     */
    fun getMediaItems(albumId: String): Flow<List<MediaItem>> = flow {
        // First, emit cached data if available
        val cachedMedia = cacheManager.getCachedMediaItems(albumId)
        if (cachedMedia != null && cachedMedia.isNotEmpty()) {
            Log.d(TAG, "Emitting ${cachedMedia.size} cached media items for album $albumId")
            emit(cachedMedia)
        }

        // Then fetch fresh data from API
        try {
            val client = graphClient ?: run {
                Log.e(TAG, "Graph client not initialized")
                if (cachedMedia == null) {
                    emit(emptyList())
                }
                return@flow
            }

            // Get children of the folder
            val result = client.getFolderChildrenById(albumId)

            if (result.isFailure) {
                Log.e(TAG, "Failed to get media items", result.exceptionOrNull())
                if (cachedMedia == null) {
                    emit(emptyList())
                }
                return@flow
            }

            val response = result.getOrNull()
            if (response == null) {
                if (cachedMedia == null) {
                    emit(emptyList())
                }
                return@flow
            }

            // Filter for media files and map to MediaItem objects
            val freshMedia = response.items
                .filter { it.file != null && isMediaFile(it) } // Only media files
                .mapNotNull { item ->
                    // downloadUrl should be included in the response now
                    val downloadUrl = item.downloadUrl ?: run {
                        // Fallback: fetch individual item (should rarely happen now)
                        Log.w(TAG, "downloadUrl missing for ${item.name}, fetching individually")
                        val itemResult = client.getItem(item.id)
                        itemResult.getOrNull()?.downloadUrl
                    }

                    if (downloadUrl == null) {
                        Log.w(TAG, "No download URL for item: ${item.name}")
                        return@mapNotNull null
                    }

                    MediaItem(
                        id = item.id,
                        name = item.name,
                        url = downloadUrl,
                        thumbnailUrl = item.thumbnails?.firstOrNull()?.large?.url
                            ?: item.thumbnails?.firstOrNull()?.medium?.url,
                        isVideo = isVideo(item),
                        mimeType = item.file?.mimeType
                    )
                }

            // Save to cache
            cacheManager.saveMediaItems(albumId, freshMedia)

            // Emit fresh data
            Log.d(TAG, "Emitting ${freshMedia.size} fresh media items from API for album $albumId")
            emit(freshMedia)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting media items", e)
            if (cachedMedia == null) {
                emit(emptyList())
            }
        }
    }

    /**
     * Sign out and clear credentials
     */
    suspend fun signOut() {
        authManager.signOut()
        graphClient = null
        cacheManager.clearCache()
    }

    /**
     * Check if a drive item is a media file (image or video)
     */
    private fun isMediaFile(item: DriveItemResponse): Boolean {
        val mimeType = item.file?.mimeType?.lowercase() ?: return false
        return IMAGE_MIME_TYPES.contains(mimeType) || VIDEO_MIME_TYPES.contains(mimeType)
    }

    /**
     * Check if a drive item is a video
     */
    private fun isVideo(item: DriveItemResponse): Boolean {
        val mimeType = item.file?.mimeType?.lowercase() ?: return false
        return VIDEO_MIME_TYPES.contains(mimeType) || item.video != null
    }
}
