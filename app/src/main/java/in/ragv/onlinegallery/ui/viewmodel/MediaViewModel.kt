package `in`.ragv.onlinegallery.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val albumName: String = ""
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OneDriveRepository(application)

    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    fun loadMediaItems(albumId: String, albumName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                albumName = albumName
            )
            try {
                repository.initialize()
                val items = repository.getMediaItems(albumId)
                _uiState.value = _uiState.value.copy(
                    mediaItems = items,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
}
