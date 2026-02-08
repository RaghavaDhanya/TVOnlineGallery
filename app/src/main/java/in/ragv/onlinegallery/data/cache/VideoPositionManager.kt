package `in`.ragv.onlinegallery.data.cache

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Manages video playback positions across sessions
 * Stores the last watched position for each video
 */
class VideoPositionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "video_positions",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val TAG = "VideoPositionManager"
        private const val MIN_POSITION_TO_SAVE = 5000L // Only save if watched at least 5 seconds
        private const val RESUME_THRESHOLD = 0.95f // Don't resume if watched > 95% (treat as completed)
    }

    /**
     * Save video playback position
     * @param videoId Unique identifier for the video
     * @param position Current playback position in milliseconds
     * @param duration Total video duration in milliseconds
     */
    fun savePosition(videoId: String, position: Long, duration: Long) {
        // Don't save if position is too early or too close to the end
        if (position < MIN_POSITION_TO_SAVE) {
            Log.d(TAG, "Position too early to save: $position")
            return
        }

        if (duration > 0) {
            val watchedPercentage = position.toFloat() / duration
            if (watchedPercentage > RESUME_THRESHOLD) {
                // Video is essentially finished, clear the saved position
                clearPosition(videoId)
                Log.d(TAG, "Video $videoId completed (${(watchedPercentage * 100).toInt()}%), clearing position")
                return
            }
        }

        prefs.edit().apply {
            putLong(videoId, position)
            putLong("${videoId}_duration", duration)
            apply()
        }
        Log.d(TAG, "Saved position for $videoId: $position / $duration")
    }

    /**
     * Get saved playback position for a video
     * @param videoId Unique identifier for the video
     * @return Saved position in milliseconds, or 0 if no position saved
     */
    fun getPosition(videoId: String): Long {
        val position = prefs.getLong(videoId, 0L)
        Log.d(TAG, "Retrieved position for $videoId: $position")
        return position
    }

    /**
     * Clear saved position for a video
     * @param videoId Unique identifier for the video
     */
    fun clearPosition(videoId: String) {
        prefs.edit().apply {
            remove(videoId)
            remove("${videoId}_duration")
            apply()
        }
        Log.d(TAG, "Cleared position for $videoId")
    }

    /**
     * Get all saved positions (for debugging/maintenance)
     */
    fun getAllPositions(): Map<String, Long> {
        return prefs.all
            .filter { !it.key.endsWith("_duration") }
            .mapValues { it.value as? Long ?: 0L }
    }

    /**
     * Clear all saved positions
     */
    fun clearAllPositions() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Cleared all positions")
    }
}
