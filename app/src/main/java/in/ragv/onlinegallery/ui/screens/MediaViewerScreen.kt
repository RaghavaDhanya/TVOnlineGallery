package `in`.ragv.onlinegallery.ui.screens

import android.net.Uri
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.*
import coil.compose.AsyncImage
import `in`.ragv.onlinegallery.data.models.MediaItem
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    mediaItems: List<MediaItem>,
    initialIndex: Int,
    onBackClick: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(initialIndex) }
    val currentItem = mediaItems[currentIndex]
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var videoPosition by remember { mutableStateOf(0L) }
    var videoDuration by remember { mutableStateOf(0L) }

    // Auto-hide controls after 4 seconds
    LaunchedEffect(showControls, currentIndex) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .onKeyEvent { keyEvent ->
                // Handle key events
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            // Navigate to next without showing controls
                            if (currentIndex < mediaItems.size - 1) {
                                currentIndex++
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            // Navigate to previous without showing controls
                            if (currentIndex > 0) {
                                currentIndex--
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            // Center button toggles controls
                            showControls = !showControls
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            // Play/pause button
                            isPlaying = !isPlaying
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            onBackClick()
                            true
                        }
                        else -> {
                            // Any other button shows controls
                            showControls = true
                            false
                        }
                    }
                } else {
                    false
                }
            }
    ) {
        // Media content
        if (currentItem.isVideo) {
            VLCVideoPlayer(
                url = currentItem.url,
                modifier = Modifier.fillMaxSize(),
                isPlaying = isPlaying,
                onPlayingStateChange = { isPlaying = it },
                onPositionUpdate = { position, duration ->
                    videoPosition = position
                    videoDuration = duration
                }
            )
        } else {
            AsyncImage(
                model = currentItem.url,
                contentDescription = currentItem.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Top gradient overlay for title
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Transparent
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 32.dp)
                ) {
                    Text(
                        text = currentItem.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${currentIndex + 1} of ${mediaItems.size}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }

        // Bottom gradient overlay for controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 32.dp)
                ) {
                    // Video progress bar (only for videos)
                    if (currentItem.isVideo && videoDuration > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = formatTime(videoPosition),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { videoPosition.toFloat() / videoDuration },
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                            Text(
                                text = formatTime(videoDuration),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }

                    // Control buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    // Back button
                    Button(
                        onClick = {
                            onBackClick()
                        },
                        modifier = Modifier.width(140.dp)
                    ) {
                        Text("← Back")
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Play/Pause button for videos
                    if (currentItem.isVideo) {
                        Button(
                            onClick = {
                                isPlaying = !isPlaying
                            },
                            modifier = Modifier.width(160.dp)
                        ) {
                            Text(if (isPlaying) "Pause" else "Play")
                        }
                    }

                    // Navigation hint
                    Text(
                        text = "Use ← → to navigate",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Previous button
                    Button(
                        onClick = {
                            if (currentIndex > 0) {
                                currentIndex--
                                showControls = true
                            }
                        },
                        enabled = currentIndex > 0,
                        modifier = Modifier.width(160.dp)
                    ) {
                        Text("← Previous")
                    }

                    // Next button
                    Button(
                        onClick = {
                            if (currentIndex < mediaItems.size - 1) {
                                currentIndex++
                                showControls = true
                            }
                        },
                        enabled = currentIndex < mediaItems.size - 1,
                        modifier = Modifier.width(160.dp)
                    ) {
                        Text("Next →")
                    }
                    }
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    val hours = (milliseconds / (1000 * 60 * 60))
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

@Composable
fun VLCVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    onPlayingStateChange: (Boolean) -> Unit = {},
    onPositionUpdate: (position: Long, duration: Long) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current

    // Create LibVLC instance
    val libVLC = remember {
        android.util.Log.d("VLCVideoPlayer", "Creating LibVLC")
        try {
            LibVLC(context, arrayListOf("--no-drop-late-frames", "--no-skip-frames"))
        } catch (e: Exception) {
            android.util.Log.e("VLCVideoPlayer", "Error creating LibVLC", e)
            null
        }
    }

    // Create MediaPlayer
    val mediaPlayer = remember {
        libVLC?.let {
            android.util.Log.d("VLCVideoPlayer", "Creating MediaPlayer")
            MediaPlayer(it)
        }
    }

    // Update media when URL changes
    LaunchedEffect(url) {
        android.util.Log.d("VLCVideoPlayer", "Loading video URL: $url")
        mediaPlayer?.let { player ->
            try {
                val media = Media(libVLC, Uri.parse(url))
                media.setHWDecoderEnabled(true, false)
                media.addOption(":network-caching=1000")

                player.media = media
                media.release()

                player.play()
                onPlayingStateChange(true)
            } catch (e: Exception) {
                android.util.Log.e("VLCVideoPlayer", "Error setting media", e)
            }
        }
    }

    // Handle play/pause state
    LaunchedEffect(isPlaying) {
        mediaPlayer?.let { player ->
            if (isPlaying && !player.isPlaying) {
                android.util.Log.d("VLCVideoPlayer", "Playing")
                player.play()
            } else if (!isPlaying && player.isPlaying) {
                android.util.Log.d("VLCVideoPlayer", "Pausing")
                player.pause()
            }
        }
    }

    // Update position periodically and notify parent
    LaunchedEffect(Unit) {
        while (true) {
            mediaPlayer?.let { player ->
                onPositionUpdate(player.time, player.length)
            }
            delay(500)
        }
    }

    // Clean up when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            android.util.Log.d("VLCVideoPlayer", "Releasing MediaPlayer and LibVLC")
            mediaPlayer?.stop()
            mediaPlayer?.detachViews()
            mediaPlayer?.release()
            libVLC?.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            android.util.Log.d("VLCVideoPlayer", "Creating VLCVideoLayout")
            VLCVideoLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Attach the media player to this layout
                mediaPlayer?.attachViews(this, null, false, false)
            }
        },
        modifier = modifier
    )
}
