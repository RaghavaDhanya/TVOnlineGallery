package `in`.ragv.onlinegallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import `in`.ragv.onlinegallery.data.models.Album
import `in`.ragv.onlinegallery.ui.viewmodel.AlbumViewModel
import `in`.ragv.onlinegallery.ui.viewmodel.DeviceCodeData

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AlbumListScreen(
    viewModel: AlbumViewModel,
    onAlbumClick: (Album) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Preserve scroll position across navigation and configuration changes
    val gridState = rememberLazyGridState()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            // Show login screen only when we know for sure user is not authenticated
            uiState.isAuthenticated == false -> {
                SignInScreen(
                    onSignIn = { viewModel.signIn() },
                    deviceCodeData = uiState.deviceCodeData,
                    isAuthenticating = uiState.isAuthenticating,
                    error = uiState.error
                )
            }
            // Show loading only if no albums and still checking auth
            uiState.isLoading && uiState.albums.isEmpty() -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            uiState.error != null && uiState.albums.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Error: ${uiState.error}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadAlbums() }) {
                        Text("Retry")
                    }
                }
            }
            uiState.albums.isEmpty() && uiState.isAuthenticated == true -> {
                Text(
                    text = "No albums found",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else -> {
                // Focus requester for first album card
                val firstAlbumFocusRequester = remember { FocusRequester() }

                // Request focus on first album when albums load
                LaunchedEffect(uiState.albums.isNotEmpty()) {
                    if (uiState.albums.isNotEmpty()) {
                        delay(100) // Small delay to ensure compose is ready
                        try {
                            firstAlbumFocusRequester.requestFocus()
                        } catch (e: Exception) {
                            // Ignore if focus request fails
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp)
                ) {
                    Text(
                        text = "Albums",
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        items(uiState.albums) { album ->
                            AlbumCard(
                                album = album,
                                onClick = { onAlbumClick(album) },
                                modifier = if (album == uiState.albums.firstOrNull()) {
                                    Modifier.focusRequester(firstAlbumFocusRequester)
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1.33f)
            .fillMaxWidth()
    ) {
        Box {
            if (album.thumbnailUrl != null) {
                AsyncImage(
                    model = album.thumbnailUrl,
                    contentDescription = album.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                )
            }
            // Gradient scrim for text visibility
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = "${album.itemCount} items",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SignInScreen(
    onSignIn: () -> Unit,
    deviceCodeData: DeviceCodeData? = null,
    isAuthenticating: Boolean = false,
    error: String? = null
) {
    val signInButtonFocusRequester = remember { FocusRequester() }

    // Request focus on sign-in button when screen loads
    LaunchedEffect(deviceCodeData == null) {
        if (deviceCodeData == null && !isAuthenticating) {
            delay(100) // Small delay to ensure compose is ready
            try {
                signInButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore if focus request fails
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Online Gallery",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Show error if present
        if (error != null) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium
                    )
            ) {
                Text(
                    text = "Error: $error",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (deviceCodeData != null) {
            // Show device code and instructions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "To sign in, follow these steps:",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Text(
                    text = "1. On your phone or computer, visit:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = deviceCodeData.verificationUrl,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Text(
                    text = "2. Enter this code:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium
                        )
                ) {
                    Text(
                        text = deviceCodeData.userCode,
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isAuthenticating) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    Text(
                        text = "Waiting for authentication...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            // Show initial sign-in button
            Text(
                text = "Sign in to view your OneDrive albums",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            Button(
                onClick = onSignIn,
                enabled = !isAuthenticating,
                modifier = Modifier.focusRequester(signInButtonFocusRequester)
            ) {
                Text("Sign In with Microsoft")
            }
            if (isAuthenticating) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}
