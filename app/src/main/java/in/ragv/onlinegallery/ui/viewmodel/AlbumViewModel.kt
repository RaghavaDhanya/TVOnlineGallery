package `in`.ragv.onlinegallery.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.ragv.onlinegallery.data.auth.AuthManager
import `in`.ragv.onlinegallery.data.models.Album
import `in`.ragv.onlinegallery.data.repository.OneDriveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceCodeData(
    val userCode: String,
    val verificationUrl: String,
    val deviceCode: String,
    val interval: Int
)

data class AlbumUiState(
    val albums: List<Album> = emptyList(),
    val isLoading: Boolean = true, // Start with loading to avoid flashing login screen
    val error: String? = null,
    val isAuthenticated: Boolean? = null, // null = checking, true = authenticated, false = not authenticated
    val deviceCodeData: DeviceCodeData? = null,
    val isAuthenticating: Boolean = false,
    val lastViewedAlbumIndex: Int = 0
)

class AlbumViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OneDriveRepository(application)

    private val _uiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    init {
        initialize()
    }

    private fun initialize() {
        viewModelScope.launch {
            try {
                // First, load cached albums immediately (instant display)
                val cachedAlbums = repository.getCachedAlbums()
                if (cachedAlbums != null && cachedAlbums.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        albums = cachedAlbums,
                        isLoading = false,
                        isAuthenticated = true // Assume authenticated if we have cached data
                    )
                    android.util.Log.d("AlbumViewModel", "Loaded ${cachedAlbums.size} cached albums")
                }

                // Then check credentials and refresh in background
                val hasCredentials = repository.hasCredentials()

                if (hasCredentials) {
                    // Try to initialize (this will auto-refresh token if needed)
                    val initialized = repository.initialize()
                    val isAuth = repository.isAuthenticated()

                    _uiState.value = _uiState.value.copy(
                        isAuthenticated = isAuth,
                        isLoading = false
                    )

                    if (isAuth && initialized) {
                        // Load fresh albums from API (will update cache)
                        loadAlbums()
                    } else if (!isAuth) {
                        // Token refresh failed, need to re-authenticate
                        _uiState.value = _uiState.value.copy(
                            isAuthenticated = false,
                            isLoading = false,
                            albums = emptyList() // Clear cached albums as auth failed
                        )
                    }
                } else {
                    // No credentials at all, user needs to sign in
                    _uiState.value = _uiState.value.copy(
                        isAuthenticated = false,
                        isLoading = false,
                        albums = emptyList()
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAuthenticated = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * Start the sign-in process
     * This initiates the device code flow
     */
    fun signIn() {
        android.util.Log.d("AlbumViewModel", "signIn() called")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            android.util.Log.d("AlbumViewModel", "Starting sign-in, isLoading=true")
            try {
                val result = repository.startSignIn()
                android.util.Log.d("AlbumViewModel", "startSignIn result: ${result.isSuccess}")
                if (result.isSuccess) {
                    val info = result.getOrNull()!!
                    android.util.Log.d("AlbumViewModel", "Device code: ${info.userCode}, URL: ${info.verificationUrl}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        deviceCodeData = DeviceCodeData(
                            userCode = info.userCode,
                            verificationUrl = info.verificationUrl,
                            deviceCode = info.deviceCode,
                            interval = info.interval
                        ),
                        isAuthenticating = true
                    )
                    // Start polling for completion
                    pollForAuthentication(info.deviceCode, info.interval)
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Failed to start sign-in"
                    android.util.Log.e("AlbumViewModel", "Sign-in failed: $errorMsg", result.exceptionOrNull())
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("AlbumViewModel", "Exception during sign-in", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    /**
     * Poll for authentication completion
     */
    private fun pollForAuthentication(deviceCode: String, interval: Int) {
        viewModelScope.launch {
            try {
                val result = repository.completeSignIn(deviceCode, interval)
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isAuthenticated = true,
                        isAuthenticating = false,
                        deviceCodeData = null
                    )
                    // Initialize repository and load albums
                    repository.initialize()
                    loadAlbums()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isAuthenticating = false,
                        deviceCodeData = null,
                        error = result.exceptionOrNull()?.message ?: "Authentication failed"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAuthenticating = false,
                    deviceCodeData = null,
                    error = e.message
                )
            }
        }
    }

    // Track if albums have been loaded to prevent unnecessary reloads
    private var albumsLoaded = false

    /**
     * Load albums from OneDrive
     * First shows cached data (if available), then updates with fresh data from API
     */
    fun loadAlbums() {
        // Skip reload if we already have albums loaded
        if (albumsLoaded && _uiState.value.albums.isNotEmpty()) {
            android.util.Log.d("AlbumViewModel", "Skipping reload - already have albums")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                var isFirstEmission = true
                repository.getAlbums().collect { albums ->
                    _uiState.value = _uiState.value.copy(
                        albums = albums,
                        isLoading = false
                    )
                    albumsLoaded = true
                    if (isFirstEmission) {
                        android.util.Log.d("AlbumViewModel", "First emission (cached or fresh): ${albums.size} albums")
                        isFirstEmission = false
                    } else {
                        android.util.Log.d("AlbumViewModel", "Second emission (fresh): ${albums.size} albums")
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

    /**
     * Force refresh albums from API
     */
    fun refreshAlbums() {
        albumsLoaded = false
        loadAlbums()
    }

    /**
     * Sign out
     */
    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            _uiState.value = AlbumUiState()
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Set the last viewed album index for focus restoration
     */
    fun setLastViewedAlbumIndex(index: Int) {
        _uiState.value = _uiState.value.copy(lastViewedAlbumIndex = index)
    }
}
