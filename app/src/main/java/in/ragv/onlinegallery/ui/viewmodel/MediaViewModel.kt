package `in`.ragv.onlinegallery.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.ragv.onlinegallery.data.cache.VideoPositionManager
import `in`.ragv.onlinegallery.data.models.MediaItem
import `in`.ragv.onlinegallery.data.repository.OneDriveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MediaUiState(
    val mediaItems: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val albumName: String = "",
    val lastViewedIndex: Int = 0
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OneDriveRepository(application)
    private val videoPositionManager = VideoPositionManager(application)

    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    // Track the currently loaded album ID to prevent unnecessary reloads
    private var loadedAlbumId: String? = null
    private var loadedAlbumName: String = ""

    fun loadMediaItems(albumId: String, albumName: String) {
        // Skip reload if we already have data for this album
        if (loadedAlbumId == albumId && _uiState.value.mediaItems.isNotEmpty()) {
            android.util.Log.d("MediaViewModel", "Skipping reload - already have data for album $albumId")
            return
        }

        // If switching to a different album, clear old data immediately and show loading
        if (loadedAlbumId != albumId) {
            _uiState.value = _uiState.value.copy(
                mediaItems = emptyList(),
                lastViewedIndex = 0,
                isLoading = true,
                error = null,
                albumName = albumName
            )
        } else {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                albumName = albumName
            )
        }

        loadedAlbumId = albumId
        loadedAlbumName = albumName

        viewModelScope.launch {
            try {
                repository.initialize()
                var isFirstEmission = true
                repository.getMediaItems(albumId).collect { items ->
                    _uiState.value = _uiState.value.copy(
                        mediaItems = items,
                        isLoading = false
                    )
                    if (isFirstEmission) {
                        android.util.Log.d("MediaViewModel", "First emission (cached or fresh): ${items.size} items")
                        isFirstEmission = false
                    } else {
                        android.util.Log.d("MediaViewModel", "Second emission (fresh): ${items.size} items")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun setLastViewedIndex(index: Int) {
        _uiState.value = _uiState.value.copy(lastViewedIndex = index)
    }

    /**
     * Force refresh the current album data from API
     */
    fun refreshMediaItems() {
        val albumId = loadedAlbumId ?: return
        val albumName = loadedAlbumName
        loadedAlbumId = null // Clear cache flag to force reload
        loadMediaItems(albumId, albumName)
    }

    /**
     * Save video playback position
     */
    fun saveVideoPosition(videoId: String, position: Long, duration: Long) {
        videoPositionManager.savePosition(videoId, position, duration)
    }

    /**
     * Get saved video playback position
     */
    fun getVideoPosition(videoId: String): Long {
        return videoPositionManager.getPosition(videoId)
    }

    /**
     * Clear saved video position (when video is completed or user wants to restart)
     */
    fun clearVideoPosition(videoId: String) {
        videoPositionManager.clearPosition(videoId)
    }
}
