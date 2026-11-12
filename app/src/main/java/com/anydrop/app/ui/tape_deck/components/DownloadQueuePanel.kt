package com.anydrop.app.ui.tape_deck.components

import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.anydrop.app.ui.components.PillButton
import com.anydrop.app.ui.components.PillStyle
import com.anydrop.app.ui.theme.NumberFont
import com.anydrop.app.ui.theme.TextFaint
import com.anydrop.app.ui.theme.TextMuted

import com.anydrop.app.ui.theme.AccentText
import com.anydrop.app.ui.theme.AppFont
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anydrop.app.R
import com.anydrop.app.data.repository.DownloadTask
import com.anydrop.app.data.repository.TaskKind
import com.anydrop.app.data.repository.TaskStatus
import com.anydrop.app.ui.components.CopyErrorDetailsButton
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.Hairline
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.StatusGreen
import com.anydrop.app.ui.theme.StatusRed
import com.anydrop.app.ui.theme.White
import com.anydrop.app.ui.theme.tracked

/**
 * The download tracker: every running, finished and failed download with live progress,
 * speed, size and time left. Downloads started from the share sheet show up here too.
 */
@Composable
fun DownloadQueuePanel(
    tasks: List<DownloadTask>,
    onStop: (String) -> Unit,
    onRetry: (String) -> Unit,
    onOpen: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onClearFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onPauseAll: () -> Unit = {},
    onResumeAll: () -> Unit = {},
    onUseMobileData: (String) -> Unit = {}
) {
    if (tasks.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.queue_title),
                color = White,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            // Several running: pause them together; several paused: resume them together.
            if (tasks.count { it.isActive } > 1) PillButton(stringResource(R.string.queue_pause_all), onPauseAll, style = PillStyle.Tonal, small = true)
            if (tasks.count { it.status == TaskStatus.CANCELLED } > 1) PillButton(stringResource(R.string.queue_resume_all), onResumeAll, style = PillStyle.Tonal, small = true)
            if (tasks.any { !it.isActive }) PillButton(stringResource(R.string.queue_clear), onClearFinished, style = PillStyle.Ghost, small = true)
        }
        Spacer(Modifier.height(12.dp))
        val ordered = tasks.sortedWith(compareBy<DownloadTask> { if (it.isActive) 0 else 1 }.thenByDescending { it.startedAt })
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ordered.take(12).forEach { task ->
                TaskCard(task, onStop, onRetry, onOpen, onDismiss, onUseMobileData)
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: DownloadTask,
    onStop: (String) -> Unit,
    onRetry: (String) -> Unit,
    onOpen: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onUseMobileData: (String) -> Unit
) {
    val statusColor = when {
        task.status == TaskStatus.DONE -> StatusGreen
        task.status == TaskStatus.FAILED -> StatusRed
        task.waitingForWifi -> AccentText
        else -> TextMuted
    }
    val progress by animateFloatAsState((task.percent / 100f).coerceIn(0f, 1f), tween(400), label = "task-progress")
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Black)
            .clickable(enabled = task.status == TaskStatus.DONE) { onOpen(task.tag) }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .animateContentSize()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MediumGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        task.status == TaskStatus.DONE -> Icons.Filled.CheckCircle
                        task.kind == TaskKind.AUDIO -> Icons.Filled.MusicNote
                        task.kind == TaskKind.IMAGE -> Icons.Filled.Image
                        else -> Icons.Filled.Movie
                    },
                    contentDescription = null,
                    tint = if (task.status == TaskStatus.DONE) StatusGreen else TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    color = White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // English titles read left-to-right even inside the Arabic UI, and vice versa.
                    style = TextStyle(textDirection = TextDirection.Content)
                )
                Text(
                    listOf(task.quality.ltr(), statusLabel(task)).filter { it.isNotBlank() }.joinToString(", "),
                    color = statusColor,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            when (task.status) {
                TaskStatus.RUNNING -> IconButton(onClick = { onStop(task.tag) }) {
                    Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.task_stop), tint = White)
                }
                // Paused: RESUME carries on from the partial file.
                TaskStatus.CANCELLED -> PillButton(stringResource(R.string.task_resume), { onRetry(task.tag) }, style = PillStyle.Tonal, small = true)
                TaskStatus.DONE -> PillButton(stringResource(R.string.task_open), { onOpen(task.tag) }, style = PillStyle.Tonal, small = true)
                TaskStatus.FAILED -> Unit
            }
            if (!task.isActive) {
                IconButton(onClick = { onDismiss(task.tag) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.task_dismiss), tint = TextMuted)
                }
            }
        }

        if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.CANCELLED) {
            Spacer(Modifier.height(12.dp))
            val paused = task.status == TaskStatus.CANCELLED
            val track = Hairline
            val fill = if (paused) TextFaint else Amber
            if (!paused && task.queued) {
                // Waiting: an empty track, not the "busy" animation.
                LinearProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = fill, trackColor = track, strokeCap = StrokeCap.Round
                )
            } else if (!paused && task.percent <= 0) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = fill, trackColor = track, strokeCap = StrokeCap.Round
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = fill, trackColor = track, strokeCap = StrokeCap.Round
                )
            }
            if (!paused) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.percent > 0) {
                        Text("${task.percent}%", color = White, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = NumberFont)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        listOf(task.sizeText.ltr(), task.speed.ltr(), task.eta.takeIf { it.isNotBlank() }?.ltr().orEmpty())
                            .filter { it.isNotBlank() }.joinToString("   ")
                            .ifBlank { task.phase },
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = NumberFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (task.waitingForWifi) {
                Spacer(Modifier.height(10.dp))
                PillButton(stringResource(R.string.task_use_mobile_data), { onUseMobileData(task.tag) }, style = PillStyle.Tonal, small = true)
            }
        }

        if (task.status == TaskStatus.FAILED) {
            if (task.error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    task.error,
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.btn_retry), { onRetry(task.tag) }, small = true)
                if (task.error.isNotBlank()) {
                    CopyErrorDetailsButton(
                        message = task.error,
                        detail = task.errorDetail,
                        link = task.request.webpageUrl.ifBlank { task.request.url },
                        color = StatusRed,
                        quality = task.quality
                    )
                }
            }
        }
    }
}

@Composable
private fun statusLabel(task: DownloadTask): String = when (task.status) {
    // While running, say which step it's on ("Downloading audio…") once sizes are showing.
    TaskStatus.RUNNING -> if (task.sizeText.isNotBlank() || task.speed.isNotBlank()) task.phase else ""
    TaskStatus.DONE -> listOf(stringResource(R.string.task_saved), task.sizeText.ltr()).filter { it.isNotBlank() }.joinToString(", ")
    TaskStatus.FAILED -> stringResource(R.string.task_failed)
    TaskStatus.CANCELLED -> stringResource(R.string.task_stopped)
}

/** Keeps "39.2 MB" / "1080p" in reading order inside Arabic text (Unicode LTR isolate). */
private fun String.ltr(): String = if (isBlank()) this else "⁦$this⁩"
