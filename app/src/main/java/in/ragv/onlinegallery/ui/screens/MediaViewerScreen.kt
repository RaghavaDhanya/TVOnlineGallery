package `in`.ragv.onlinegallery.ui.screens

import android.net.Uri
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.*
import androidx.tv.material3.Border
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
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
    onBackClick: (finalIndex: Int) -> Unit
) {
    var currentIndex by remember { mutableStateOf(initialIndex) }
    val currentItem = mediaItems[currentIndex]
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var videoPosition by remember { mutableStateOf(0L) }
    var videoDuration by remember { mutableStateOf(0L) }
    var mediaPlayerRef by remember { mutableStateOf<Any?>(null) }

    // Focus requesters for all control buttons
    val previousButtonFocusRequester = remember { FocusRequester() }
    val nextButtonFocusRequester = remember { FocusRequester() }
    val playPauseButtonFocusRequester = remember { FocusRequester() }
    val imagePreviousButtonFocusRequester = remember { FocusRequester() }
    val imageNextButtonFocusRequester = remember { FocusRequester() }
    val backButtonFocusRequester = remember { FocusRequester() }
    var pendingFocusRequest by remember { mutableStateOf<String?>(null) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Get view to control screen on/off
    val view = LocalView.current

    // Keep screen on during video playback to prevent screensaver
    DisposableEffect(currentItem.isVideo, isPlaying) {
        if (currentItem.isVideo && isPlaying) {
            view.keepScreenOn = true
        } else {
            view.keepScreenOn = false
        }

        onDispose {
            view.keepScreenOn = false
        }
    }

    // Intercept system back button to ensure proper navigation
    BackHandler(onBack = { onBackClick(currentIndex) })

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(showControls, currentIndex, lastInteractionTime) {
        if (showControls) {
            val timeToWait = 4000L - (System.currentTimeMillis() - lastInteractionTime)
            if (timeToWait > 0) {
                delay(timeToWait)
            }
            showControls = false
        }
    }

    // Request initial focus when screen loads
    LaunchedEffect(Unit) {
        delay(100) // Small delay to ensure compose is ready
        showControls = true
        delay(50)
        if (currentItem.isVideo) {
            playPauseButtonFocusRequester.requestFocus()
        } else {
            if (currentIndex < mediaItems.size - 1) {
                imageNextButtonFocusRequester.requestFocus()
            } else if (currentIndex > 0) {
                imagePreviousButtonFocusRequester.requestFocus()
            }
        }
    }

    // Handle pending focus requests when controls become visible
    LaunchedEffect(showControls, pendingFocusRequest) {
        if (showControls && pendingFocusRequest != null) {
            // Small delay to ensure buttons are composed
            delay(50)
            when (pendingFocusRequest) {
                "previous" -> previousButtonFocusRequester.requestFocus()
                "next" -> nextButtonFocusRequester.requestFocus()
            }
            pendingFocusRequest = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    lastInteractionTime = System.currentTimeMillis()
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
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (currentItem.isVideo) {
                                // Show controls and mark previous button for focus
                                if (currentIndex > 0) {
                                    pendingFocusRequest = "previous"
                                }
                                showControls = true
                                true
                            } else {
                                showControls = true
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (currentItem.isVideo) {
                                // Show controls and mark next button for focus
                                if (currentIndex < mediaItems.size - 1) {
                                    pendingFocusRequest = "next"
                                }
                                showControls = true
                                true
                            } else {
                                showControls = true
                                false
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            isPlaying = !isPlaying
                            showControls = true
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
            // Progressive image loading: thumbnail underneath, full res on top with crossfade
            Box(modifier = Modifier.fillMaxSize()) {
                // Base layer: thumbnail (shows instantly from cache)
                if (currentItem.thumbnailUrl != null) {
                    AsyncImage(
                        model = currentItem.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Top layer: full resolution image with Coil's built-in crossfade
                // Crossfade will smoothly transition from transparent to opaque
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentItem.url)
                        .crossfade(500) // Smooth 500ms fade
                        .build(),
                    contentDescription = currentItem.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
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
                    Surface(
                        onClick = { onBackClick(currentIndex) },
                        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            contentColor = Color.White,
                            focusedContentColor = Color.White
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(4.dp, MaterialTheme.colorScheme.primary),
                                shape = CircleShape
                            )
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .focusRequester(backButtonFocusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.hasFocus) {
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_arrow_back_24),
                                contentDescription = "Back",
                                modifier = Modifier.size(32.dp),
                                tint = Color.White
                            )
                        }
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

        // Center playback controls (for videos) - Vertical layout
        if (currentItem.isVideo) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Previous button (top - D-pad UP)
                    Surface(
                        onClick = {
                            if (currentIndex > 0) {
                                currentIndex--
                                isPlaying = true
                                showControls = true
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        },
                        enabled = currentIndex > 0,
                        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            contentColor = Color.White,
                            focusedContentColor = Color.White
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(6.dp, MaterialTheme.colorScheme.primary),
                                shape = CircleShape
                            )
                        ),
                        modifier = Modifier
                            .size(80.dp)
                            .focusRequester(previousButtonFocusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.hasFocus) {
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_skip_previous_24),
                                contentDescription = "Previous",
                                modifier = Modifier.size(48.dp),
                                tint = Color.White
                            )
                        }
                    }

                    // Play/Pause button (center - D-pad CENTER/ENTER)
                    Surface(
                        onClick = {
                            isPlaying = !isPlaying
                            showControls = true
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            contentColor = Color.White,
                            focusedContentColor = Color.White
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(6.dp, MaterialTheme.colorScheme.primary),
                                shape = CircleShape
                            )
                        ),
                        modifier = Modifier
                            .size(96.dp)
                            .focusRequester(playPauseButtonFocusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.hasFocus) {
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
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
                    }

                    // Next button (bottom - D-pad DOWN)
                    Surface(
                        onClick = {
                            if (currentIndex < mediaItems.size - 1) {
                                currentIndex++
                                isPlaying = true
                                showControls = true
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        },
                        enabled = currentIndex < mediaItems.size - 1,
                        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            contentColor = Color.White,
                            focusedContentColor = Color.White
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(6.dp, MaterialTheme.colorScheme.primary),
                                shape = CircleShape
                            )
                        ),
                        modifier = Modifier
                            .size(80.dp)
                            .focusRequester(nextButtonFocusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.hasFocus) {
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
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
                Surface(
                    onClick = {
                        if (currentIndex > 0) {
                            currentIndex--
                            showControls = true
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    },
                    shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        contentColor = Color.White,
                        focusedContentColor = Color.White
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(6.dp, MaterialTheme.colorScheme.primary),
                            shape = CircleShape
                        )
                    ),
                    modifier = Modifier
                        .padding(48.dp)
                        .size(80.dp)
                        .focusRequester(imagePreviousButtonFocusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.hasFocus) {
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_skip_previous_24),
                            contentDescription = "Previous",
                            modifier = Modifier.size(48.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showControls && currentIndex < mediaItems.size - 1,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Surface(
                    onClick = {
                        if (currentIndex < mediaItems.size - 1) {
                            currentIndex++
                            showControls = true
                            lastInteractionTime = System.currentTimeMillis()
                        }
                    },
                    shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        contentColor = Color.White,
                        focusedContentColor = Color.White
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(6.dp, MaterialTheme.colorScheme.primary),
                            shape = CircleShape
                        )
                    ),
                    modifier = Modifier
                        .padding(48.dp)
                        .size(80.dp)
                        .focusRequester(imageNextButtonFocusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.hasFocus) {
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
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
    var isBuffering by remember { mutableStateOf(false) }

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
                // Add event listener to detect buffering
                player.setEventListener { event ->
                    when (event.type) {
                        MediaPlayer.Event.Buffering -> {
                            val bufferPercent = event.buffering
                            android.util.Log.d("VLCVideoPlayer", "Buffering: $bufferPercent%")
                            isBuffering = bufferPercent < 100f
                        }
                        MediaPlayer.Event.Playing -> {
                            android.util.Log.d("VLCVideoPlayer", "Event: Playing")
                            isBuffering = false
                        }
                        MediaPlayer.Event.Paused -> {
                            android.util.Log.d("VLCVideoPlayer", "Event: Paused")
                        }
                        MediaPlayer.Event.EndReached -> {
                            android.util.Log.d("VLCVideoPlayer", "Event: End Reached")
                        }
                        MediaPlayer.Event.EncounteredError -> {
                            android.util.Log.e("VLCVideoPlayer", "Event: Encountered Error")
                            isBuffering = false
                        }
                        else -> {}
                    }
                }
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
                // Increased network caching from 1000ms to 3000ms for better buffering
                // This helps prevent stuttering when streaming from OneDrive
                media.addOption(":network-caching=3000")
                media.addOption(":file-caching=1000")
                media.addOption(":live-caching=1000")

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

    Box(modifier = modifier) {
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
            modifier = Modifier.fillMaxSize()
        )

        // Buffering indicator
        if (isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = Color.White,
                        strokeWidth = 6.dp
                    )
                    Text(
                        text = "Buffering...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}
