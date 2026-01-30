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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.*
import coil.compose.AsyncImage
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.CircleShape
import `in`.ragv.onlinegallery.R
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
    var mediaPlayerRef by remember { mutableStateOf<Any?>(null) }

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
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (currentItem.isVideo) {
                                // For videos: seek forward 10 seconds
                                (mediaPlayerRef as? MediaPlayer)?.let { player ->
                                    val newPosition = (player.time + 10000).coerceAtMost(player.length)
                                    player.time = newPosition
                                }
                                showControls = true
                                true
                            } else {
                                // For images: navigate to next
                                if (currentIndex < mediaItems.size - 1) {
                                    currentIndex++
                                    true
                                } else false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (currentItem.isVideo) {
                                // For videos: seek backward 10 seconds
                                (mediaPlayerRef as? MediaPlayer)?.let { player ->
                                    val newPosition = (player.time - 10000).coerceAtLeast(0)
                                    player.time = newPosition
                                }
                                showControls = true
                                true
                            } else {
                                // For images: navigate to previous
                                if (currentIndex > 0) {
                                    currentIndex--
                                    true
                                } else false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (currentItem.isVideo) {
                                // For videos: toggle play/pause
                                isPlaying = !isPlaying
                                showControls = true
                                true
                            } else {
                                // For images: toggle controls
                                showControls = !showControls
                                true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            // Up/Down shows controls
                            showControls = true
                            false // Let it propagate for focus navigation
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            isPlaying = !isPlaying
                            showControls = true
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            onBackClick()
                            true
                        }
                        else -> {
                            showControls = true
                            false
                        }
                    }
                } else false
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
                },
                onMediaPlayerCreated = { mediaPlayerRef = it }
            )
        } else {
            AsyncImage(
                model = currentItem.url,
                contentDescription = currentItem.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Top bar overlay
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
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.IconButton(
                        onClick = { onBackClick() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_arrow_back_24),
                            contentDescription = "Back",
                            modifier = Modifier.size(32.dp),
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = currentItem.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Center playback controls (for videos)
        if (currentItem.isVideo) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Previous button
                    androidx.compose.material3.IconButton(
                        onClick = {
                            if (currentIndex > 0) {
                                currentIndex--
                                isPlaying = true
                                showControls = true
                            }
                        },
                        enabled = currentIndex > 0,
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.2f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_fast_rewind_24),
                            contentDescription = "Previous",
                            modifier = Modifier.size(48.dp),
                            tint = Color.White
                        )
                    }

                    // Play/Pause button (larger)
                    androidx.compose.material3.IconButton(
                        onClick = {
                            isPlaying = !isPlaying
                            showControls = true
                        },
                        modifier = Modifier
                            .size(96.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.3f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isPlaying) R.drawable.baseline_pause_circle_24
                                else R.drawable.baseline_play_circle_filled_24
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(56.dp),
                            tint = Color.White
                        )
                    }

                    // Next button
                    androidx.compose.material3.IconButton(
                        onClick = {
                            if (currentIndex < mediaItems.size - 1) {
                                currentIndex++
                                isPlaying = true
                                showControls = true
                            }
                        },
                        enabled = currentIndex < mediaItems.size - 1,
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.2f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_fast_forward_24),
                            contentDescription = "Next",
                            modifier = Modifier.size(48.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Side navigation buttons (only for images)
        if (!currentItem.isVideo) {
            AnimatedVisibility(
                visible = showControls && currentIndex > 0,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                androidx.compose.material3.IconButton(
                    onClick = {
                        if (currentIndex > 0) {
                            currentIndex--
                            showControls = true
                        }
                    },
                    modifier = Modifier
                        .padding(48.dp)
                        .size(80.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_skip_previous_24),
                        contentDescription = "Previous",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
            }

            AnimatedVisibility(
                visible = showControls && currentIndex < mediaItems.size - 1,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                androidx.compose.material3.IconButton(
                    onClick = {
                        if (currentIndex < mediaItems.size - 1) {
                            currentIndex++
                            showControls = true
                        }
                    },
                    modifier = Modifier
                        .padding(48.dp)
                        .size(80.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.baseline_skip_next_24),
                        contentDescription = "Next",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom progress bar (for videos)
        if (currentItem.isVideo) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 24.dp)
                    ) {
                        // Time display
                        if (videoDuration > 0) {
                            Text(
                                text = "${formatTime(videoPosition)} - ${formatTime(videoDuration)}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        // Progress bar
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { if (videoDuration > 0) videoPosition.toFloat() / videoDuration else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        } else {
            // Bottom info bar for images
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 24.dp)
                    ) {
                        Text(
                            text = "${currentIndex + 1} of ${mediaItems.size}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
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
    onPositionUpdate: (position: Long, duration: Long) -> Unit = { _, _ -> },
    onMediaPlayerCreated: (MediaPlayer?) -> Unit = {}
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
            MediaPlayer(it).also { player ->
                onMediaPlayerCreated(player)
            }
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
