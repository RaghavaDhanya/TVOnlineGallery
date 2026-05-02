package `in`.ragv.onlinegallery.data.repository

import android.content.Context
import android.util.Log
import `in`.ragv.onlinegallery.data.api.DriveItemListResponse
import `in`.ragv.onlinegallery.data.api.DriveItemResponse
import `in`.ragv.onlinegallery.data.api.GraphApiClient
import `in`.ragv.onlinegallery.data.auth.AuthManager
import `in`.ragv.onlinegallery.data.cache.CacheManager
import `in`.ragv.onlinegallery.data.models.Album
import `in`.ragv.onlinegallery.data.models.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
     * Get cached albums synchronously (for instant display on startup)
     */
    fun getCachedAlbums(): List<Album>? {
        return cacheManager.getCachedAlbums()
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
        // DIAGNOSTIC #2: log cache state at the very start of every getAlbums() invocation
        // so we can tell if a re-trigger (e.g. refreshAlbums on ON_RESUME) is overwriting
        // a previously good save.
        val preCheck = cacheManager.getCachedAlbums()
        Log.w(
            "ThumbnailDebug",
            "getAlbums() ENTER — disk cache has ${preCheck?.size ?: 0} albums, " +
                "${preCheck?.count { it.thumbnailUrl != null } ?: 0} with non-null thumbnailUrl"
        )

        // First, emit cached data if available
        val cachedAlbums = cacheManager.getCachedAlbums()
        if (cachedAlbums != null && cachedAlbums.isNotEmpty()) {
            val nullThumbs = cachedAlbums.count { it.thumbnailUrl == null }
            Log.d(TAG, "Emitting ${cachedAlbums.size} cached albums ($nullThumbs with null thumbnailUrl)")
            if (nullThumbs > 0) {
                val names = cachedAlbums.filter { it.thumbnailUrl == null }.joinToString(", ") { it.name }
                Log.w("ThumbnailDebug", "CACHED albums with null thumbnailUrl: $names")
            }
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

            // Get all children of the root folder path (with pagination)
            val allItems = fetchAllPages(client.getFolderChildren(ROOT_FOLDER_PATH)) { nextLink ->
                client.fetchNextPage(nextLink)
            }

            if (allItems == null) {
                Log.e(TAG, "Failed to get albums")
                if (cachedAlbums == null) {
                    emit(emptyList())
                }
                return@flow
            }

            // Filter for folders only and map to Album objects
            val folders = allItems.filter { it.folder != null }

            // For each folder, try to get a thumbnail from cached media or API.
            // Fall back to the previously-cached URL on null so a transient resolve
            // failure can never downgrade a known-good URL.
            val previousById = cachedAlbums?.associateBy { it.id }.orEmpty()
            val freshAlbums = folders.map { item ->
                val resolved = getAlbumCoverThumbnail(item.id, item.name)
                val carriedOver = previousById[item.id]?.thumbnailUrl
                Album(
                    id = item.id,
                    name = item.name,
                    thumbnailUrl = resolved ?: carriedOver,
                    itemCount = item.folder?.childCount ?: 0
                )
            }

            // Save to cache
            val freshNonNull = freshAlbums.count { it.thumbnailUrl != null }
            Log.w(
                "ThumbnailDebug",
                "About to saveAlbums: ${freshAlbums.size} albums, $freshNonNull with non-null thumbnailUrl"
            )
            cacheManager.saveAlbums(freshAlbums)

            // DIAGNOSTIC #1: read back immediately to verify what actually persisted.
            // If this disagrees with the input, save is silently dropping URLs.
            val readback = cacheManager.getCachedAlbums()
            val readbackNonNull = readback?.count { it.thumbnailUrl != null } ?: 0
            Log.w(
                "ThumbnailDebug",
                "POST-saveAlbums readback: ${readback?.size ?: 0} albums, $readbackNonNull with non-null thumbnailUrl"
            )

            val nullThumbs = freshAlbums.count { it.thumbnailUrl == null }
            Log.d(TAG, "Emitting ${freshAlbums.size} fresh albums from API ($nullThumbs with null thumbnailUrl)")
            if (nullThumbs > 0) {
                val names = freshAlbums.filter { it.thumbnailUrl == null }.joinToString(", ") { it.name }
                Log.w("ThumbnailDebug", "FRESH albums with null thumbnailUrl: $names")
            }
            emit(freshAlbums)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting albums", e)
            if (cachedAlbums == null) {
                emit(emptyList())
            }
        }
    }.flowOn(Dispatchers.IO)

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

            // Get all children of the folder (with pagination)
            val allItems = fetchAllPages(client.getFolderChildrenById(albumId)) { nextLink ->
                client.fetchNextPage(nextLink)
            }

            if (allItems == null) {
                Log.e(TAG, "Failed to get media items")
                if (cachedMedia == null) {
                    emit(emptyList())
                }
                return@flow
            }

            // Filter for media files and map to MediaItem objects
            val freshMedia = allItems
                .filter { it.file != null && isMediaFile(it) } // Only media files
                .mapNotNull { item ->
                    // downloadUrl should be included in the response now
                    val downloadUrl: String? = if (item.downloadUrl != null) {
                        item.downloadUrl
                    } else {
                        // Fallback: fetch individual item (should rarely happen now)
                        Log.w(TAG, "downloadUrl missing for ${item.name}, fetching individually")
                        client.getItem(item.id).getOrNull()?.downloadUrl
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
    }.flowOn(Dispatchers.IO)

    /**
     * Sign out and clear credentials
     */
    suspend fun signOut() {
        authManager.signOut()
        graphClient = null
        cacheManager.clearCache()
    }

    /**
     * Get a thumbnail for an album cover
     * First tries cached media items, then fetches from API if needed
     */
    private suspend fun getAlbumCoverThumbnail(albumId: String, albumName: String = ""): String? {
        val tag = "ThumbnailDebug"
        Log.d(tag, "[$albumName] resolve start (id=$albumId)")

        // Note: we intentionally do NOT short-circuit on cacheManager.getCachedMediaItems() here.
        // SharePoint thumbnail URLs carry signed `tempauth` JWTs that expire in days/weeks,
        // so a cached MediaItem.thumbnailUrl is often dead. Always fetch a fresh URL; Coil's
        // disk cache (keyed by album.id in AlbumCard) keeps the bytes across sessions.

        try {
            val client = graphClient
            if (client == null) {
                Log.w(tag, "[$albumName] graphClient is null, returning null")
                return null
            }

            // Use the slim cover-lookup endpoint (top=5, minimal $select) so we
            // don't pull a multi-hundred-KB response per folder just to pick a
            // single thumbnail. Reduces GC pressure dramatically on low-RAM TVs.
            val result = client.getFirstFolderItems(albumId, top = 5)
            if (result.isFailure) {
                Log.e(tag, "[$albumName] getFirstFolderItems FAILED", result.exceptionOrNull())
                return null
            }

            val response = result.getOrNull()
            if (response == null) {
                Log.w(tag, "[$albumName] getFolderChildrenById succeeded but body was null")
                return null
            }

            val items = response.items
            val mediaItems = items.filter { it.file != null && isMediaFile(it) }
            val mediaWithThumbs = mediaItems.filter { it.thumbnails?.isNotEmpty() == true }
            Log.d(
                tag,
                "[$albumName] API page: total=${items.size}, mediaFiles=${mediaItems.size}, mediaWithThumbnails=${mediaWithThumbs.size}, hasNextPage=${response.nextLink != null}"
            )

            if (mediaItems.isNotEmpty() && mediaWithThumbs.isEmpty()) {
                val sample = mediaItems.take(3).joinToString(", ") { "${it.name}(${it.file?.mimeType})" }
                Log.w(tag, "[$albumName] media files exist but none have thumbnails. Sample: $sample")
            }

            val mediaItem = mediaWithThumbs.firstOrNull()
            // Prefer the medium (~176px) thumbnail for album-cover cards. The large
            // variant is up to 800x800 and decodes to ~2.5MB ARGB bitmaps; with 20+
            // albums that pins main-thread time on bitmap upload. Medium is plenty
            // for grid cards and roughly 20x smaller in memory.
            val thumbnailUrl = mediaItem?.thumbnails?.firstOrNull()?.medium?.url
                ?: mediaItem?.thumbnails?.firstOrNull()?.large?.url

            if (thumbnailUrl == null) {
                Log.w(tag, "[$albumName] RETURNING NULL — no usable thumbnail in first page")
            } else {
                Log.d(tag, "[$albumName] resolved via API from item '${mediaItem?.name}'")
            }
            return thumbnailUrl
        } catch (e: CancellationException) {
            // Cooperative cancellation must propagate, otherwise the outer
            // folders.map keeps running, every remaining call returns null,
            // and saveAlbums later persists a corrupted (mostly-null) list,
            // overwriting good cached URLs.
            throw e
        } catch (e: Exception) {
            Log.e(tag, "[$albumName] exception in getAlbumCoverThumbnail", e)
        }

        return null
    }

    /**
     * Follow pagination to collect all items across pages
     */
    private suspend fun fetchAllPages(
        firstResult: Result<DriveItemListResponse>,
        fetchNext: suspend (String) -> Result<DriveItemListResponse>
    ): List<DriveItemResponse>? {
        if (firstResult.isFailure) return null
        val firstResponse = firstResult.getOrNull() ?: return null

        val allItems = mutableListOf<DriveItemResponse>()
        allItems.addAll(firstResponse.items)

        var nextLink = firstResponse.nextLink
        while (nextLink != null) {
            Log.d(TAG, "Fetching next page: $nextLink")
            val nextResult = fetchNext(nextLink)
            if (nextResult.isFailure) {
                Log.e(TAG, "Failed to fetch next page", nextResult.exceptionOrNull())
                break
            }
            val nextResponse = nextResult.getOrNull() ?: break
            allItems.addAll(nextResponse.items)
            nextLink = nextResponse.nextLink
        }

        Log.d(TAG, "Fetched ${allItems.size} total items across all pages")
        return allItems
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
