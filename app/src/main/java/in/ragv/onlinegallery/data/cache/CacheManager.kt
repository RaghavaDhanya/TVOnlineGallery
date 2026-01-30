package `in`.ragv.onlinegallery.data.cache

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import `in`.ragv.onlinegallery.data.models.Album
import `in`.ragv.onlinegallery.data.models.MediaItem

/**
 * Manages caching of albums and media items using SharedPreferences
 */
class CacheManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        CACHE_PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    companion object {
        private const val TAG = "CacheManager"
        private const val CACHE_PREFS_NAME = "OnlineGalleryCache"
        private const val KEY_ALBUMS = "cached_albums"
        private const val KEY_ALBUMS_TIMESTAMP = "cached_albums_timestamp"
        private const val KEY_MEDIA_PREFIX = "cached_media_"
        private const val KEY_MEDIA_TIMESTAMP_PREFIX = "cached_media_timestamp_"
    }

    /**
     * Save albums to cache
     */
    fun saveAlbums(albums: List<Album>) {
        try {
            val json = gson.toJson(albums)
            prefs.edit()
                .putString(KEY_ALBUMS, json)
                .putLong(KEY_ALBUMS_TIMESTAMP, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Saved ${albums.size} albums to cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving albums to cache", e)
        }
    }

    /**
     * Get cached albums
     * @return Cached albums or null if not available
     * Note: Cache never expires - fresh data is always fetched in background
     */
    fun getCachedAlbums(): List<Album>? {
        try {
            val json = prefs.getString(KEY_ALBUMS, null) ?: return null

            val type = object : TypeToken<List<Album>>() {}.type
            val albums = gson.fromJson<List<Album>>(json, type)
            Log.d(TAG, "Retrieved ${albums.size} albums from cache")
            return albums
        } catch (e: Exception) {
            Log.e(TAG, "Error reading albums from cache", e)
            return null
        }
    }

    /**
     * Save media items for an album to cache
     */
    fun saveMediaItems(albumId: String, mediaItems: List<MediaItem>) {
        try {
            val json = gson.toJson(mediaItems)
            prefs.edit()
                .putString(KEY_MEDIA_PREFIX + albumId, json)
                .putLong(KEY_MEDIA_TIMESTAMP_PREFIX + albumId, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Saved ${mediaItems.size} media items for album $albumId to cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving media items to cache", e)
        }
    }

    /**
     * Get cached media items for an album
     * @return Cached media items or null if not available
     * Note: Cache never expires - fresh data is always fetched in background
     */
    fun getCachedMediaItems(albumId: String): List<MediaItem>? {
        try {
            val json = prefs.getString(KEY_MEDIA_PREFIX + albumId, null) ?: return null

            val type = object : TypeToken<List<MediaItem>>() {}.type
            val mediaItems = gson.fromJson<List<MediaItem>>(json, type)
            Log.d(TAG, "Retrieved ${mediaItems.size} media items from cache for album $albumId")
            return mediaItems
        } catch (e: Exception) {
            Log.e(TAG, "Error reading media items from cache", e)
            return null
        }
    }

    /**
     * Clear all cached data
     */
    fun clearCache() {
        try {
            prefs.edit().clear().apply()
            Log.d(TAG, "Cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }

    /**
     * Clear cached albums
     */
    fun clearAlbumsCache() {
        try {
            prefs.edit()
                .remove(KEY_ALBUMS)
                .remove(KEY_ALBUMS_TIMESTAMP)
                .apply()
            Log.d(TAG, "Albums cache cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing albums cache", e)
        }
    }

    /**
     * Clear cached media items for an album
     */
    fun clearMediaItemsCache(albumId: String) {
        try {
            prefs.edit()
                .remove(KEY_MEDIA_PREFIX + albumId)
                .remove(KEY_MEDIA_TIMESTAMP_PREFIX + albumId)
                .apply()
            Log.d(TAG, "Media items cache cleared for album $albumId")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing media items cache", e)
        }
    }
}
