package com.anydrop.app.ui.player

import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.anydrop.app.R
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.service.PlaybackService
import com.anydrop.app.service.SleepTimer
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkPalette
import com.anydrop.app.ui.theme.LedRed
import com.anydrop.app.ui.theme.White
import kotlinx.coroutines.delay
import java.io.File
import androidx.media3.common.MediaItem as Media3Item

/**
 * Songs play from [queue] through the background [PlaybackService]: one after another (also
 * with the screen off), with shuffle, repeat, an "Up next" list and a sleep timer. A video can
 * be played here too, sound only ([videoAsAudio]).
 *
 * @param onCurrentChange the service moved on to another song by itself; the screen follows.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun AudioPlayerContent(
    queue: List<MediaItem>,
    current: MediaItem,
    onCurrentChange: (String) -> Unit,
    onDeleteRequest: () -> Unit = {},
    videoAsAudio: Boolean = false,
    startPositionMs: Long = 0L
) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var hasNext by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var showQueue by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    val currentId by rememberUpdatedState(current.id)
    val followSong by rememberUpdatedState(onCurrentChange)

    // Connect to the background PlaybackService once; it outlives this screen.
    DisposableEffect(Unit) {
        val token = SessionToken(context, android.content.ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: Media3Item?, reason: Int) {
                val id = mediaItem?.mediaId ?: return
                if (id != currentId) followSong(id)
            }
        }
        future.addListener({
            val c = future.get()
            c.addListener(listener)
            controller = c
        }, com.google.common.util.concurrent.MoreExecutors.directExecutor())

        onDispose {
            // Do NOT release the player here — the service keeps it alive for
            // background playback. Just release this controller handle.
            controller?.removeListener(listener)
            controller?.release()
            controller = null
        }
    }

    // Play [current]: just a jump when the service already has this queue, else load it.
    LaunchedEffect(controller, current.id, queue.map { it.id }) {
        val c = controller ?: return@LaunchedEffect
        if (c.currentMediaItem?.mediaId == current.id && c.mediaItemCount == queue.size) {
            if (!c.isPlaying) c.play()
            return@LaunchedEffect
        }
        val index = queue.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        val sameQueue = c.mediaItemCount == queue.size &&
            (0 until c.mediaItemCount).all { c.getMediaItemAt(it).mediaId == queue[it].id }
        if (sameQueue) {
            c.seekTo(index, 0L)
        } else {
            c.setMediaItems(queue.map { it.toPlayable() }, index, startPositionMs)
        }
        // A video played as sound only: no picture to decode.
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, videoAsAudio)
            .build()
        c.prepare()
        c.play()
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
            hasNext = c.hasNextMediaItem()
            shuffle = c.shuffleModeEnabled
            repeatMode = c.repeatMode
        }
    }

    val timerEndsAt by SleepTimer.endsAt.collectAsState()
    val timerEndOfTrack by SleepTimer.endOfTrack.collectAsState()
    var timerLeft by remember { mutableLongStateOf(0L) }
    LaunchedEffect(timerEndsAt) {
        val endsAt = timerEndsAt ?: return@LaunchedEffect
        while (true) {
            timerLeft = (endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            delay(1_000)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Black),
        contentAlignment = Alignment.Center
    ) {
        Row(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            if (queue.size > 1) {
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, stringResource(R.string.audio_up_next), tint = White)
                }
            }
            IconButton(onClick = { showTimer = true }) {
                Icon(
                    Icons.Filled.Bedtime,
                    stringResource(R.string.sleep_timer),
                    tint = if (timerEndsAt != null || timerEndOfTrack) Amber else White
                )
            }
            IconButton(onClick = onDeleteRequest) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.menu_delete), tint = LedRed)
            }
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
                    if (videoAsAudio) Icons.Filled.Headphones else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Amber,
                    modifier = Modifier.size(72.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                current.title,
                color = White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                style = TextStyle(textDirection = TextDirection.Content)
            )
            if (videoAsAudio) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.video_as_audio_hint), color = White.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            if (timerEndsAt != null || timerEndOfTrack) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (timerEndOfTrack) stringResource(R.string.sleep_timer_end_of_track_on)
                    else stringResource(R.string.sleep_timer_left, timerLeft.toTimeString()),
                    color = Amber,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val several = queue.size > 1
                    IconButton(onClick = { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }, enabled = several) {
                        Icon(
                            Icons.Filled.Shuffle,
                            stringResource(R.string.audio_shuffle),
                            tint = if (shuffle && several) Amber else White.copy(alpha = if (several) 0.5f else 0.2f)
                        )
                    }
                    // "Previous" restarts the song when it has played a few seconds, like other players.
                    IconButton(onClick = { controller?.seekToPrevious() }) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.player_previous),
                            tint = White,
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
                    IconButton(onClick = { controller?.seekToNextMediaItem() }, enabled = hasNext) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.player_next),
                            tint = if (hasNext) White else White.copy(alpha = 0.3f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    // Off → repeat the list → repeat this song.
                    IconButton(onClick = {
                        controller?.let {
                            it.repeatMode = when (it.repeatMode) {
                                Player.REPEAT_MODE_OFF -> if (several) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_ONE
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        }
                    }) {
                        Icon(
                            if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            stringResource(R.string.audio_repeat),
                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) Amber else White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }

    if (showQueue) {
        UpNextSheet(
            queue = queue,
            currentId = current.id,
            onPick = { index -> controller?.seekTo(index, 0L); showQueue = false },
            onDismiss = { showQueue = false }
        )
    }
    if (showTimer) {
        SleepTimerDialog(
            isSet = timerEndsAt != null || timerEndOfTrack,
            onPick = { minutes ->
                when (minutes) {
                    null -> SleepTimer.cancel()
                    0 -> SleepTimer.stopAtEndOfTrack()
                    else -> SleepTimer.stopIn(minutes)
                }
                showTimer = false
            },
            onDismiss = { showTimer = false }
        )
    }
}

/** The queue, the song playing now marked; a tap plays another one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpNextSheet(queue: List<MediaItem>, currentId: String, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val playing = queue.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = playing)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = DarkPalette.surface) {
        Text(
            stringResource(R.string.audio_up_next),
            color = Amber,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            itemsIndexed(queue, key = { _, item -> item.id }) { index, item ->
                val now = item.id == currentId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(index) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (now) Icons.Filled.PlayArrow else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = if (now) Amber else White.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.title,
                        color = if (now) Amber else White,
                        fontWeight = if (now) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(textDirection = TextDirection.Content),
                        modifier = Modifier.weight(1f)
                    )
                    if (item.durationMs > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(item.durationMs.toTimeString(), color = White.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Stop playback after a while: [onPick] gets minutes, 0 = end of this song, null = off. */
@Composable
private fun SleepTimerDialog(isSet: Boolean, onPick: (Int?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        containerColor = Black,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer), color = Amber, fontFamily = FontFamily.Monospace) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    TextButton(onClick = { onPick(minutes) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sleep_timer_minutes, minutes), color = White, fontFamily = FontFamily.Monospace)
                    }
                }
                TextButton(onClick = { onPick(0) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sleep_timer_end_of_track), color = White, fontFamily = FontFamily.Monospace)
                }
                if (isSet) {
                    TextButton(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sleep_timer_off), color = LedRed, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
            }
        }
    )
}

/** A song (or a video's sound) as the service plays it, titled for its notification. */
private fun MediaItem.toPlayable(): Media3Item {
    val uri = localUri ?: Uri.fromFile(File(localFilePath))
    return Media3Item.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(creator.takeIf { it.isNotBlank() })
                .build()
        )
        .build()
}
