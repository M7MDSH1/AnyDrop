package com.anydrop.app.ui.player

import androidx.activity.ComponentActivity
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Rational
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.anydrop.app.PipLeaveHandler
import com.anydrop.app.R
import com.anydrop.app.ui.components.DeleteConfirmDialog
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkPalette
import com.anydrop.app.ui.theme.LedRed
import com.anydrop.app.ui.theme.White
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt
import com.anydrop.app.ui.theme.tracked

/**
 * @param externalFile true when playing a file another app opened with AnyDrop:
 *   the file name is shown and there is no delete (the file is not ours).
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onNavigateToMedia: (String) -> Unit = {},
    onExit: () -> Unit = {},
    externalFile: Boolean = false
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val currentMedia by viewModel.currentMedia.collectAsState()
    val nextMediaId by viewModel.nextMediaId.collectAsState()
    val previousMediaId by viewModel.previousMediaId.collectAsState()

    // ---- Delete flow (video / image / audio) -------------------------------
    // Confirm → delete → advance to the neighbouring item, or exit when the
    // deleted item was the last one. MediaStore rows owned by OTHER apps need
    // the OS consent flow (same pattern as the library/audio screens).
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteTarget by rememberSaveable { mutableStateOf<String?>(null) }

    fun advanceAfterDelete() {
        val target = pendingDeleteTarget
        pendingDeleteTarget = null
        if (target != null) onNavigateToMedia(target) else onExit()
    }

    val deleteConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            advanceAfterDelete()
        } else {
            // User declined the OS dialog — the row stays, remain on this item.
            pendingDeleteTarget = null
        }
    }

    fun performDelete() {
        val media = currentMedia ?: return
        showDeleteConfirm = false
        val result = viewModel.deleteCurrent()
        // Prefer the atomically-captured neighbour from the queue; fall back to
        // the live nav state, and exit only when the item was genuinely last.
        pendingDeleteTarget = result.advanceTargetId ?: nextMediaId ?: previousMediaId
        viewModel.refreshLibrary()
        if (result.consent.consentUris.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pending = MediaStore.createDeleteRequest(context.contentResolver, result.consent.consentUris)
                deleteConsentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            } else if (result.consent.consentSender != null) {
                deleteConsentLauncher.launch(IntentSenderRequest.Builder(result.consent.consentSender).build())
            } else {
                advanceAfterDelete()
            }
        } else {
            advanceAfterDelete()
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            count = 1,
            onConfirm = ::performDelete,
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (currentMedia == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Black),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.player_no_media), color = White.copy(alpha = 0.3f), fontSize = 16.sp)
        }
        return
    }

    val uri = currentMedia!!.localUri
        ?: Uri.fromFile(File(currentMedia!!.localFilePath))

    // Images open in a simple zoomable full-screen viewer, not the video player.
    if (currentMedia!!.isImage) {
        ImageViewerContent(
            uri = uri,
            hasNext = nextMediaId != null,
            hasPrevious = previousMediaId != null,
            onNext = { nextMediaId?.let(onNavigateToMedia) },
            onPrevious = { previousMediaId?.let(onNavigateToMedia) },
            onDeleteRequest = { showDeleteConfirm = true }
        )
        return
    }

    // Audio plays through a background MediaSessionService so it keeps going after
    // the app is exited, with a media notification.
    if (currentMedia!!.isAudio) {
        SwipeableContent(
            hasNext = nextMediaId != null,
            hasPrevious = previousMediaId != null,
            onNext = { nextMediaId?.let(onNavigateToMedia) },
            onPrevious = { previousMediaId?.let(onNavigateToMedia) }
        ) {
            AudioPlayerContent(
                uri = uri,
                title = currentMedia!!.title,
                hasNext = nextMediaId != null,
                hasPrevious = previousMediaId != null,
                onNext = { nextMediaId?.let(onNavigateToMedia) },
                onPrevious = { previousMediaId?.let(onNavigateToMedia) },
                onDeleteRequest = { showDeleteConfirm = true }
            )
        }
        return
    }

    SwipeableContent(
        hasNext = nextMediaId != null,
        hasPrevious = previousMediaId != null,
        onNext = { nextMediaId?.let(onNavigateToMedia) },
        onPrevious = { previousMediaId?.let(onNavigateToMedia) }
    ) {
        VideoPlayerContent(
            uri = uri,
            activity = activity,
            viewModel = viewModel,
            hasNext = nextMediaId != null,
            hasPrevious = previousMediaId != null,
            onNext = { nextMediaId?.let(onNavigateToMedia) },
            onPrevious = { previousMediaId?.let(onNavigateToMedia) },
            onDeleteRequest = if (externalFile) null else ({ showDeleteConfirm = true }),
            title = if (externalFile) currentMedia!!.title else null,
            shareItem = currentMedia!!
        )
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun AudioPlayerContent(
    uri: Uri,
    title: String,
    hasNext: Boolean,
    hasPrevious: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDeleteRequest: () -> Unit = {}
) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    // Connect to the background PlaybackService and start this track.
    DisposableEffect(uri) {
        val token = SessionToken(
            context,
            android.content.ComponentName(context, com.anydrop.app.service.PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.setMediaItem(Media3Item.fromUri(uri))
            c.prepare()
            c.playWhenReady = true
        }, com.google.common.util.concurrent.MoreExecutors.directExecutor())

        onDispose {
            // Do NOT release the player here — the service keeps it alive for
            // background playback. Just release this controller handle.
            controller?.release()
            controller = null
        }
    }

    var scrubbing by remember { mutableStateOf(false) }
    var scrubTarget by remember { mutableLongStateOf(0L) }

    // Frame clock for the timeline (MediaController extrapolates the position
    // locally, so this is cheap); the bar reads it in its draw phase.
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            isPlaying = c.isPlaying
            position = c.currentPosition
            buffered = c.bufferedPosition
            duration = c.duration.coerceAtLeast(0L)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Black),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onDeleteRequest,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.menu_delete),
                tint = LedRed
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Amber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Amber,
                    modifier = Modifier.size(72.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                title,
                color = White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Spacer(Modifier.height(24.dp))

            // Scrubber — kept LTR (together with its timestamps) in every locale.
            // Audio seeks once, on release; the bubble previews the target.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                ScrubBar(
                    position = { position },
                    buffered = { buffered },
                    duration = duration,
                    accent = Amber,
                    trackColor = White.copy(alpha = 0.15f),
                    bufferColor = White.copy(alpha = 0.3f),
                    onScrubStart = { scrubbing = true },
                    onScrub = { scrubTarget = it },
                    onScrubEnd = { target ->
                        controller?.seekTo(target)
                        position = target
                        scrubbing = false
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TimeLabel(
                        text = { (if (scrubbing) scrubTarget else position).toTimeString() },
                        color = White.copy(alpha = 0.6f)
                    )
                    TimeLabel(text = { duration.toTimeString() }, color = White.copy(alpha = 0.6f))
                }
            }

            Spacer(Modifier.height(16.dp))

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevious, enabled = hasPrevious) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.player_previous),
                            tint = if (hasPrevious) White else White.copy(alpha = 0.3f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    IconButton(onClick = {
                        controller?.let { if (it.isPlaying) it.pause() else it.play() }
                    }) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.player_play_pause),
                            tint = Amber,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    IconButton(onClick = onNext, enabled = hasNext) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.player_next),
                            tint = if (hasNext) White else White.copy(alpha = 0.3f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageViewerContent(
    uri: Uri,
    hasNext: Boolean = false,
    hasPrevious: Boolean = false,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onDeleteRequest: () -> Unit = {}
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 96.dp.toPx() }
    // Reset zoom/swipe state whenever a different media item opens.
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var swipeDrag by remember(uri) { mutableFloatStateOf(0f) }
    var isDragging by remember(uri) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .graphicsLayer {
                translationX = if (isDragging) swipeDrag * 0.35f else 0f
            }
            .pointerInput(hasNext, hasPrevious) {
                // One handler for everything: at 1× a single-finger drag swipes
                // to the next/previous item; pinch (or any drag while zoomed in)
                // transforms the image. A nested swipe detector can't work here —
                // the transform gesture consumes all drags first.
                detectTransformGestures { _, pan, zoom, _ ->
                    if (zoom != 1f || scale > 1f) {
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                        isDragging = false
                        swipeDrag = 0f
                    } else {
                        swipeDrag += pan.x
                        isDragging = true
                        when {
                            swipeDrag <= -swipeThresholdPx && hasNext -> {
                                onNext()
                                swipeDrag = 0f
                                isDragging = false
                            }
                            swipeDrag >= swipeThresholdPx && hasPrevious -> {
                                onPrevious()
                                swipeDrag = 0f
                                isDragging = false
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = uri,
            contentDescription = stringResource(R.string.image_viewer_desc),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )

        IconButton(
            onClick = onDeleteRequest,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.menu_delete),
                tint = LedRed
            )
        }

        // Affordance hint: show which direction is available while the drag moves.
        if (isDragging) {
            val showNext = swipeDrag < 0f && hasNext
            val showPrevious = swipeDrag > 0f && hasPrevious
            if (showNext || showPrevious) {
                Text(
                    text = when {
                        showNext -> stringResource(R.string.swipe_next)
                        showPrevious -> stringResource(R.string.swipe_prev)
                        else -> ""
                    },
                    color = Amber,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = tracked(2f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPlayerContent(
    uri: Uri,
    activity: ComponentActivity,
    viewModel: PlayerViewModel,
    hasNext: Boolean = false,
    hasPrevious: Boolean = false,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onDeleteRequest: (() -> Unit)? = null,
    title: String? = null,
    shareItem: com.anydrop.app.data.model.MediaItem? = null
) {
    val context: Context = activity

    val player = remember {
        // HW decoders first (default); fall back to bundled SW/FFmpeg decoders
        // for codecs a device can't decode in hardware (some HEVC/AV1/VP9).
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        ExoPlayer.Builder(context, renderersFactory)
            // handleAudioFocus=true → auto-pause on phone calls, duck on notifications.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            // Pause when headphones are unplugged / Bluetooth disconnects.
            .setHandleAudioBecomingNoisy(true)
            .build().also { exo ->
                exo.setMediaItem(Media3Item.fromUri(uri))
                exo.prepare()
                exo.playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(true) }
    // Timeline state. uiPosition/bufferedPosition move every frame while the
    // controls are up and are only read in draw/derived scopes (see ScrubBar).
    var uiPosition by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubTarget by remember { mutableLongStateOf(0L) }
    var resumeAfterScrub by remember { mutableStateOf(false) }
    var showRemaining by rememberSaveable { mutableStateOf(false) }
    // Double-tap seeks stack: each extra double-tap on the same side within a
    // second adds another 10s to the jump (and to the on-screen total).
    var seekStreakDirection by remember { mutableIntStateOf(0) }
    var seekStreakSeconds by remember { mutableIntStateOf(0) }
    var lastSeekTapAt by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var shareMenuOpen by remember { mutableStateOf(false) }
    var showSeekFeedback by remember { mutableStateOf<String?>(null) }
    // Which side the feedback shows on: -1 left (rewind), 1 right (forward), 0 centre.
    var seekFeedbackSide by remember { mutableIntStateOf(0) }
    var volume by remember { mutableFloatStateOf(context.getSystemVolume()) }
    var brightness by remember { mutableFloatStateOf(context.getScreenBrightness()) }
    var showVolumeFeedback by remember { mutableStateOf(false) }
    var showBrightnessFeedback by remember { mutableStateOf(false) }
    // Aspect-ratio / resize mode: cycles Fit -> Fill -> Zoom -> Fit.
    var resizeModeIndex by remember { mutableIntStateOf(0) }
    // Repeat-one: loops the current video instead of auto-advancing. Off by
    // default and reset automatically whenever a different item opens.
    var repeatOne by remember { mutableStateOf(false) }

    LaunchedEffect(repeatOne) {
        player.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    val speeds = listOf(0.5f, 1f, 1.25f, 1.5f, 2f, 3f, 4f)
    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to stringResource(R.string.resize_fit),
        AspectRatioFrameLayout.RESIZE_MODE_FILL to stringResource(R.string.resize_fill),
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to stringResource(R.string.resize_zoom)
    )

    val hideHandler = remember { Handler(Looper.getMainLooper()) }
    val hideRunnable = remember {
        // An open menu lives inside the controls; hiding them would close it under the finger.
        Runnable { if (!showSpeedMenu && !shareMenuOpen) showControls = false }
    }
    fun resetHideTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        showControls = true
        hideHandler.postDelayed(hideRunnable, 3000)
    }

    fun seekBy(direction: Int) {
        val now = SystemClock.uptimeMillis()
        seekStreakSeconds =
            if (direction == seekStreakDirection && now - lastSeekTapAt < 1_000L) seekStreakSeconds + 10 else 10
        seekStreakDirection = direction
        lastSeekTapAt = now
        val target = player.currentPosition + direction * 10_000L
        val end = player.duration
        player.seekTo(if (end > 0L) target.coerceIn(0L, end) else target.coerceAtLeast(0L))
        seekFeedbackSide = direction
        showSeekFeedback = context.getString(
            if (direction < 0) R.string.seek_back_feedback else R.string.seek_forward_feedback,
            seekStreakSeconds
        )
        resetHideTimer()
    }

    // Scrubbing pauses playback and previews frames with fast key-frame seeks;
    // lifting the finger does one exact seek and resumes if it was playing.
    fun beginScrub() {
        scrubTarget = player.currentPosition
        resumeAfterScrub = player.playWhenReady
        scrubbing = true
        player.pause()
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        hideHandler.removeCallbacks(hideRunnable)
        showControls = true
    }

    fun endScrub(targetMs: Long) {
        player.setSeekParameters(SeekParameters.DEFAULT)
        player.seekTo(targetMs)
        uiPosition = targetMs
        scrubbing = false
        if (resumeAfterScrub) player.play()
        viewModel.onSeek(targetMs)
        resetHideTimer()
    }

    LaunchedEffect(scrubbing) {
        if (!scrubbing) return@LaunchedEffect
        var shown = -1L
        while (scrubbing) {
            val target = scrubTarget
            if (target != shown) {
                player.seekTo(target)
                shown = target
            }
            delay(50)
        }
    }

    LaunchedEffect(player) {
        while (true) {
            duration = player.duration.coerceAtLeast(0L)
            // Watch-progress heartbeat: 5s save tick + completion detection
            // (completion is suppressed while repeat-one is looping).
            viewModel.onPlaybackTick(player.isPlaying, player.currentPosition, duration, repeatOne)
            delay(500)
        }
    }

    // Listen for buffering, playback errors, and end-of-video so we can show a
    // spinner / error message and auto-advance to the next item. Also drives
    // watch-progress: restore on first READY, save immediately on seek, and
    // mark completed on STATE_ENDED.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                when (state) {
                    Player.STATE_READY -> {
                        val seekTo = viewModel.onPlayerReady(player.duration)
                        if (seekTo > 0L) player.seekTo(seekTo)
                    }
                    Player.STATE_ENDED -> {
                        viewModel.complete()
                        if (hasNext) onNext()
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // Scrub previews are not saved; endScrub saves the final spot.
                if (reason == Player.DISCONTINUITY_REASON_SEEK && !scrubbing &&
                    newPosition.positionMs != C.TIME_UNSET
                ) {
                    viewModel.onSeek(newPosition.positionMs)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = error.localizedMessage ?: context.getString(R.string.player_error_generic)
                isBuffering = false
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(showSeekFeedback) {
        if (showSeekFeedback != null) {
            delay(700)
            showSeekFeedback = null
        }
    }

    LaunchedEffect(showVolumeFeedback) {
        if (showVolumeFeedback) { delay(1200); showVolumeFeedback = false }
    }
    LaunchedEffect(showBrightnessFeedback) {
        if (showBrightnessFeedback) { delay(1200); showBrightnessFeedback = false }
    }

    LaunchedEffect(playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }

    var isInPipMode by remember { mutableStateOf(activity.isInPictureInPictureModeCompat()) }

    // Keep the PiP action buttons in sync with play state / navigation availability.
    fun refreshPipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            updatePipParams(activity, player.pipAspectRatio(), isPlaying, hasPrevious, hasNext)
        }
    }
    LaunchedEffect(isPlaying, hasNext, hasPrevious, isInPipMode) {
        if (isInPipMode) refreshPipParams()
    }

    // Receive PiP control-button broadcasts (play/pause/next/previous).
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != PipActions.ACTION_CONTROL) return
                when (intent.getIntExtra(PipActions.EXTRA_CONTROL_TYPE, 0)) {
                    PipActions.CONTROL_PLAY -> player.play()
                    PipActions.CONTROL_PAUSE -> player.pause()
                    PipActions.CONTROL_NEXT -> onNext()
                    PipActions.CONTROL_PREVIOUS -> onPrevious()
                }
            }
        }
        val filter = IntentFilter(PipActions.ACTION_CONTROL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            activity.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { activity.unregisterReceiver(receiver) } }
    }

    // Frame clock for the timeline while it is on screen, so the bar glides instead
    // of stepping twice a second. Nothing recomposes: only ScrubBar's draw phase and
    // the time labels' derived text read these values.
    LaunchedEffect(player, showControls, isInPipMode) {
        if (!showControls || isInPipMode) return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            uiPosition = player.currentPosition
            bufferedPosition = player.bufferedPosition
        }
    }

    // Track PiP mode so we hide the in-app overlay controls while in PiP.
    LaunchedEffect(Unit) {
        while (true) {
            val inPip = activity.isInPictureInPictureModeCompat()
            if (inPip != isInPipMode) {
                isInPipMode = inPip
                if (inPip) showControls = false
            }
            delay(400)
        }
    }

    // Auto-enter PiP ONLY when the user genuinely leaves the app (Home/recents),
    // via MainActivity.onUserLeaveHint — never on Back or in-app navigation (skip).
    DisposableEffect(hasNext, hasPrevious) {
        PipLeaveHandler.onUserLeave = {
            if (player.isPlaying && !activity.isInPictureInPictureModeCompat() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ) {
                enterPip(activity, player.pipAspectRatio(), isPlaying = true, hasPrevious = hasPrevious, hasNext = hasNext)
            }
        }
        onDispose { PipLeaveHandler.onUserLeave = null }
    }

    // Pause when backgrounded (unless in PiP) and release on exit. ON_PAUSE is
    // the guaranteed exit hook (fires before ON_STOP, and even on a low-memory
    // kill defers nothing once the lifecycle dispatches) — save progress here.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        resetHideTimer()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onSeek(player.currentPosition)
                Lifecycle.Event.ON_STOP -> {
                    if (!activity.isInPictureInPictureModeCompat()) player.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            hideHandler.removeCallbacks(hideRunnable)
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    keepScreenOn = true
                    resizeMode = resizeModes[resizeModeIndex].first
                }
            },
            update = { view ->
                // Reapply resize mode whenever the aspect-ratio toggle changes.
                view.resizeMode = resizeModes[resizeModeIndex].first
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture halves kept LTR in every locale: double-tap left = back 10s,
        // right = forward 10s (coherent with the LTR seek bar), and the vertical
        // drags stay brightness-left / volume-right.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { seekBy(-1) },
                                onTap = { resetHideTimer() }
                            )
                        }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                val delta = -dragAmount / 800f
                                brightness = (brightness + delta).coerceIn(0.01f, 1f)
                                context.setScreenBrightness(activity, brightness)
                                showBrightnessFeedback = true
                            }
                        }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { seekBy(1) },
                                onTap = { resetHideTimer() }
                            )
                        }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                val delta = -dragAmount / 800f
                                volume = (volume + delta).coerceIn(0f, 1f)
                                context.setSystemVolume(volume)
                                showVolumeFeedback = true
                            }
                        }
                )
            }
        }

        // Buffering spinner — shown while ExoPlayer is loading and there's no error.
        if (isBuffering && playbackError == null) {
            CircularProgressIndicator(
                color = VideoAccent,
                modifier = Modifier.align(Alignment.Center).size(48.dp)
            )
        }

        // Playback error overlay.
        playbackError?.let { err ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠", fontSize = 28.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.player_playback_failed),
                        color = OnVideo,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        err,
                        color = OnVideo.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = {
                        playbackError = null
                        isBuffering = true
                        player.prepare()
                        player.play()
                    }) {
                        Text(stringResource(R.string.btn_retry), color = VideoAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSeekFeedback != null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            // Seek jumps show on the side that was double-tapped (the gesture halves
            // are LTR in every locale, hence the absolute alignment).
            modifier = Modifier
                .align(
                    when (seekFeedbackSide) {
                        -1 -> AbsoluteAlignment.CenterLeft
                        1 -> AbsoluteAlignment.CenterRight
                        else -> Alignment.Center
                    }
                )
                .padding(horizontal = 28.dp)
                // Side feedback sits above the previous/next buttons, not on them.
                .offset(y = if (seekFeedbackSide != 0) (-104).dp else 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    showSeekFeedback ?: "",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    // LTR so the arrows keep pointing the way the video moves; an RTL
                    // paragraph would mirror » into «.
                    style = TextStyle(textDirection = TextDirection.Ltr)
                )
            }
        }

        // Volume/brightness indicators anchored to the LTR gesture halves above.
        // align(CenterStart/CenterEnd) resolves against the PARENT's layout
        // direction, so the LTR override must wrap a full-size Box (the parent),
        // not just the indicators themselves.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = showVolumeFeedback,
                    enter = fadeIn(tween(100)),
                    exit = fadeOut(tween(300)),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)
                ) {
                    VolumeOrBrightnessIndicator(
                        value = volume,
                        label = stringResource(R.string.player_volume),
                        icon = "\uD83D\uDD0A"
                    )
                }

                AnimatedVisibility(
                    visible = showBrightnessFeedback,
                    enter = fadeIn(tween(100)),
                    exit = fadeOut(tween(300)),
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
                ) {
                    VolumeOrBrightnessIndicator(
                        value = brightness,
                        label = stringResource(R.string.player_brightness),
                        icon = "\u2600\uFE0F"
                    )
                }
            }
        }

        val chromeAlpha by animateFloatAsState(
            targetValue = if (scrubbing) 0f else 1f,
            animationSpec = tween(160),
            label = "chromeAlpha"
        )

        AnimatedVisibility(
            visible = showControls && !isInPipMode,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter)
                        .graphicsLayer { alpha = chromeAlpha },
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (title != null) {
                        Text(
                            title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // File names read in their own direction in the Arabic UI too.
                            style = TextStyle(textDirection = TextDirection.Content),
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                    }
                    shareItem?.let { item ->
                        PlayerShareButton(
                            item = item,
                            tint = OnVideo,
                            menuColor = VideoMenu,
                            onMenuOpenChange = { open ->
                                shareMenuOpen = open
                                resetHideTimer()
                            }
                        )
                    }
                    onDeleteRequest?.let { requestDelete ->
                        IconButton(onClick = { requestDelete(); resetHideTimer() }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.menu_delete), tint = LedRed)
                        }
                    }
                    IconButton(onClick = {
                        resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                        seekFeedbackSide = 0
                        showSeekFeedback = resizeModes[resizeModeIndex].second
                        hideHandler.postDelayed({ showSeekFeedback = null }, 700)
                        resetHideTimer()
                    }) {
                        Icon(Icons.Default.AspectRatio, contentDescription = stringResource(R.string.player_aspect_ratio), tint = OnVideo)
                    }
                    IconButton(onClick = { toggleOrientation(activity); resetHideTimer() }) {
                        Icon(Icons.Default.ScreenRotation, contentDescription = stringResource(R.string.player_rotate), tint = OnVideo)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        IconButton(onClick = {
                            enterPip(activity, player.pipAspectRatio(), isPlaying, hasPrevious, hasNext); resetHideTimer()
                        }) {
                            Icon(Icons.Default.PictureInPicture, contentDescription = stringResource(R.string.player_pip), tint = OnVideo)
                        }
                    }
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer { alpha = chromeAlpha },
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                onPrevious()
                                resetHideTimer()
                            },
                            enabled = hasPrevious,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hasPrevious) Color.Black.copy(alpha = 0.5f)
                                    else Color.Black.copy(alpha = 0.2f)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = stringResource(R.string.player_previous),
                                tint = if (hasPrevious) OnVideo else OnVideo.copy(alpha = 0.3f),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (player.isPlaying) player.pause() else player.play()
                                resetHideTimer()
                            },
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = OnVideo,
                                modifier = Modifier.size(42.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                onNext()
                                resetHideTimer()
                            },
                            enabled = hasNext,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hasNext) Color.Black.copy(alpha = 0.5f)
                                    else Color.Black.copy(alpha = 0.2f)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = stringResource(R.string.player_next),
                                tint = if (hasNext) OnVideo else OnVideo.copy(alpha = 0.3f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Time · seek bar · duration (tap it for time remaining). Kept
                    // LTR in every locale; the speed row below follows the layout.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TimeLabel(
                                text = { (if (scrubbing) scrubTarget else uiPosition).toTimeString() },
                                color = Color.White
                            )
                            ScrubBar(
                                position = { uiPosition },
                                buffered = { bufferedPosition },
                                duration = duration,
                                accent = VideoAccent,
                                onScrubStart = ::beginScrub,
                                onScrub = { scrubTarget = it },
                                onScrubEnd = ::endScrub,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            )
                            TimeLabel(
                                text = {
                                    if (showRemaining) "\u2212" + (duration - uiPosition).coerceAtLeast(0L).toTimeString()
                                    else duration.toTimeString()
                                },
                                color = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        showRemaining = !showRemaining
                                        resetHideTimer()
                                    }
                                    .padding(horizontal = 4.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                repeatOne = !repeatOne
                                resetHideTimer()
                            }
                        ) {
                            Icon(
                                Icons.Default.RepeatOne,
                                contentDescription = stringResource(R.string.player_repeat_one),
                                tint = if (repeatOne) VideoAccent else OnVideo.copy(alpha = 0.4f)
                            )
                        }
                        Box {
                            TextButton(
                                onClick = { showSpeedMenu = true; resetHideTimer() }
                            ) {
                                Text(
                                    text = if (playbackSpeed == 1f) stringResource(R.string.player_speed_1x) else "${playbackSpeed}\u00D7",
                                    color = VideoAccent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false; resetHideTimer() },
                                modifier = Modifier.background(VideoMenu)
                            ) {
                                speeds.forEach { speed ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (speed == 1f) stringResource(R.string.player_speed_normal) else "${speed}\u00D7",
                                                color = if (speed == playbackSpeed) VideoAccent else OnVideo.copy(alpha = 0.7f),
                                                fontWeight = if (speed == playbackSpeed) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            playbackSpeed = speed
                                            showSpeedMenu = false
                                            resetHideTimer()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// The video player always sits on black, so it keeps the dark-mode palette in both
// themes (the light palette's dark text and deep amber would vanish on video).
private val OnVideo = Color.White
private val VideoAccent = DarkPalette.accent
private val VideoMenu = DarkPalette.surface

@Composable
private fun SwipeableContent(
    hasNext: Boolean,
    hasPrevious: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    swipeEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    var dragTotal by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 96.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = if (isDragging) dragTotal * 0.35f else 0f
            }
            .pointerInput(swipeEnabled, hasNext, hasPrevious) {
                if (!swipeEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragTotal = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragTotal += dragAmount
                    },
                    onDragEnd = {
                        when {
                            dragTotal <= -swipeThresholdPx && hasNext -> onNext()
                            dragTotal >= swipeThresholdPx && hasPrevious -> onPrevious()
                        }
                        dragTotal = 0f
                        isDragging = false
                    },
                    onDragCancel = {
                        dragTotal = 0f
                        isDragging = false
                    }
                )
            }
    ) {
        content()

        // Affordance hint: show which direction is available while the drag moves.
        if (isDragging) {
            val showNext = dragTotal < 0f && hasNext
            val showPrevious = dragTotal > 0f && hasPrevious
            if (showNext || showPrevious) {
                Text(
                    text = when {
                        showNext -> stringResource(R.string.swipe_next)
                        showPrevious -> stringResource(R.string.swipe_prev)
                        else -> ""
                    },
                    color = Amber,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = tracked(2f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun VolumeOrBrightnessIndicator(value: Float, label: String, icon: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(100.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(value)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Amber)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "${(value * 100).toInt()}%",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
    }
}

private fun Context.getSystemVolume(): Float {
    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    return cur.toFloat() / max.toFloat()
}

private fun Context.setSystemVolume(fraction: Float) {
    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val target = (fraction.coerceIn(0f, 1f) * max).toInt()
    am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
}

private fun Context.getScreenBrightness(): Float {
    return try {
        val brightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        brightness / 255f
    } catch (_: Exception) { 0.5f }
}

private fun Context.setScreenBrightness(activity: ComponentActivity, fraction: Float) {
    val clamped = fraction.coerceIn(0.01f, 1f)
    val lp = activity.window.attributes
    lp.screenBrightness = clamped
    activity.window.attributes = lp
}

private fun ComponentActivity.isInPictureInPictureModeCompat(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
}

/**
 * The PiP window takes the video's own shape (portrait reels get a tall window)
 * instead of letterboxing everything into 16:9. Android only accepts ratios
 * between 1:2.39 and 2.39:1, so extreme videos are clamped just inside that.
 */
private fun Player.pipAspectRatio(): Rational {
    val size = videoSize
    if (size.width <= 0 || size.height <= 0) return Rational(16, 9)
    val ratio = (size.width * size.pixelWidthHeightRatio / size.height).coerceIn(0.42f, 2.38f)
    return Rational((ratio * 1000).roundToInt(), 1000)
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
private fun buildPipParams(
    activity: ComponentActivity,
    aspectRatio: Rational,
    isPlaying: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean
): android.app.PictureInPictureParams {
    return android.app.PictureInPictureParams.Builder()
        .setAspectRatio(aspectRatio)
        .setActions(PipActions.buildActions(activity, isPlaying, hasPrevious, hasNext))
        .build()
}

private fun enterPip(
    activity: ComponentActivity,
    aspectRatio: Rational,
    isPlaying: Boolean = true,
    hasPrevious: Boolean = false,
    hasNext: Boolean = false
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        activity.enterPictureInPictureMode(
            buildPipParams(activity, aspectRatio, isPlaying, hasPrevious, hasNext)
        )
    }
}

/** Update the PiP action buttons live (e.g. play<->pause) while already in PiP. */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
private fun updatePipParams(
    activity: ComponentActivity,
    aspectRatio: Rational,
    isPlaying: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean
) {
    runCatching {
        activity.setPictureInPictureParams(
            buildPipParams(activity, aspectRatio, isPlaying, hasPrevious, hasNext)
        )
    }
}

private fun toggleOrientation(activity: ComponentActivity) {
    activity.requestedOrientation = if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
}
