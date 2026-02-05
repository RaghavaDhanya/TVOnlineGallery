package `in`.ragv.onlinegallery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import `in`.ragv.onlinegallery.data.models.MediaItem
import `in`.ragv.onlinegallery.ui.viewmodel.MediaViewModel
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import `in`.ragv.onlinegallery.R
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaGridScreen(
    viewModel: MediaViewModel,
    albumId: String,
    albumName: String,
    onMediaClick: (MediaItem, Int) -> Unit,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Preserve scroll position across navigation and configuration changes
    val gridState = rememberLazyGridState()

    LaunchedEffect(albumId) {
        viewModel.loadMediaItems(albumId, albumName)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Error: ${uiState.error}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadMediaItems(albumId, albumName) }) {
                        Text("Retry")
                    }
                }
            }
            uiState.mediaItems.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No media found in this album",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBackClick) {
                        Text("Go Back")
                    }
                }
            }
            else -> {
                // Focus requesters - create one for each media item index
                val mediaFocusRequesters = remember(uiState.mediaItems.size) {
                    List(uiState.mediaItems.size) { FocusRequester() }
                }
                val backButtonFocusRequester = remember { FocusRequester() }

                // Request focus on last viewed media item when screen loads
                LaunchedEffect(uiState.mediaItems.isNotEmpty(), uiState.lastViewedIndex) {
                    if (uiState.mediaItems.isNotEmpty()) {
                        delay(100) // Small delay to ensure compose is ready
                        try {
                            val indexToFocus = uiState.lastViewedIndex.coerceIn(0, uiState.mediaItems.size - 1)
                            mediaFocusRequesters[indexToFocus].requestFocus()
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
                    Row(
                        modifier = Modifier.padding(bottom = 32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onBackClick,
                            modifier = Modifier.focusRequester(backButtonFocusRequester)
                        ) {
                            Text("Back")
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        Text(
                            text = uiState.albumName,
                            style = MaterialTheme.typography.displayMedium
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(uiState.mediaItems) { index, item ->
                            MediaCard(
                                mediaItem = item,
                                onClick = {
                                    viewModel.setLastViewedIndex(index)
                                    onMediaClick(item, index)
                                },
                                modifier = Modifier.focusRequester(mediaFocusRequesters[index])
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
fun MediaCard(
    mediaItem: MediaItem,
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
            AsyncImage(
                model = mediaItem.thumbnailUrl ?: mediaItem.url,
                contentDescription = mediaItem.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (mediaItem.isVideo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_play_circle_filled_24),
                        contentDescription = "Video",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}
