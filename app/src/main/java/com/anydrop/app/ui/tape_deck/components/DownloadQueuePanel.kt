package com.anydrop.app.ui.tape_deck.components

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
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.queue_title),
                color = White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = tracked(2f),
                modifier = Modifier.weight(1f)
            )
            if (tasks.any { !it.isActive }) {
                Text(
                    stringResource(R.string.queue_clear),
                    color = Amber,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onClearFinished)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val ordered = tasks.sortedWith(compareBy<DownloadTask> { if (it.isActive) 0 else 1 }.thenByDescending { it.startedAt })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ordered.take(12).forEach { task ->
                TaskCard(task, onStop, onRetry, onOpen, onDismiss)
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
    onDismiss: (String) -> Unit
) {
    val statusColor = when (task.status) {
        TaskStatus.RUNNING -> Amber
        TaskStatus.DONE -> StatusGreen
        TaskStatus.FAILED -> StatusRed
        TaskStatus.CANCELLED -> White.copy(alpha = 0.45f)
    }
    val progress by animateFloatAsState((task.percent / 100f).coerceIn(0f, 1f), tween(400), label = "task-progress")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Black)
            .border(1.dp, if (task.isActive) Amber.copy(alpha = 0.35f) else Hairline, RoundedCornerShape(12.dp))
            .clickable(enabled = task.status == TaskStatus.DONE) { onOpen(task.tag) }
            .padding(12.dp)
            .animateContentSize()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        task.status == TaskStatus.DONE -> Icons.Filled.CheckCircle
                        task.kind == TaskKind.AUDIO -> Icons.Filled.MusicNote
                        task.kind == TaskKind.IMAGE -> Icons.Filled.Image
                        else -> Icons.Filled.Videocam
                    },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    color = White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // English titles read left-to-right even inside the Arabic UI, and vice versa.
                    style = TextStyle(textDirection = TextDirection.Content)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf(task.quality.ltr(), statusLabel(task)).filter { it.isNotBlank() }.joinToString(" · "),
                    color = statusColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            when (task.status) {
                TaskStatus.RUNNING -> ActionChip(stringResource(R.string.task_stop), StatusRed) { onStop(task.tag) }
                TaskStatus.FAILED, TaskStatus.CANCELLED -> ActionChip(stringResource(R.string.btn_retry), Amber) { onRetry(task.tag) }
                TaskStatus.DONE -> ActionChip(stringResource(R.string.task_open), StatusGreen) { onOpen(task.tag) }
            }
            if (!task.isActive) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.task_dismiss),
                    tint = White.copy(alpha = 0.4f),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss(task.tag) }
                        .padding(5.dp)
                )
            }
        }

        if (task.status == TaskStatus.RUNNING) {
            Spacer(Modifier.height(10.dp))
            if (task.percent <= 0) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = Amber,
                    trackColor = MediumGray,
                    strokeCap = StrokeCap.Round
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                    color = Amber,
                    trackColor = MediumGray,
                    strokeCap = StrokeCap.Round
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOf(task.sizeText.ltr(), task.speed.ltr(), task.eta.takeIf { it.isNotBlank() }?.let { "⏱ ${it.ltr()}" }.orEmpty())
                        .filter { it.isNotBlank() }.joinToString("  ·  ")
                        .ifBlank { task.phase },
                    color = White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (task.percent > 0) {
                    Text(
                        "${task.percent}%",
                        color = Amber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        if (task.status == TaskStatus.FAILED && task.error.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                task.error,
                color = White.copy(alpha = 0.75f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
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

@Composable
private fun statusLabel(task: DownloadTask): String = when (task.status) {
    // While running, say which step it's on ("Downloading audio…") once sizes are showing.
    TaskStatus.RUNNING -> if (task.sizeText.isNotBlank() || task.speed.isNotBlank()) task.phase else ""
    TaskStatus.DONE -> listOf(stringResource(R.string.task_saved), task.sizeText.ltr()).filter { it.isNotBlank() }.joinToString(" · ")
    TaskStatus.FAILED -> stringResource(R.string.task_failed)
    TaskStatus.CANCELLED -> stringResource(R.string.task_stopped)
}

/** Keeps "39.2 MB" / "1080p" in reading order inside Arabic text (Unicode LTR isolate). */
private fun String.ltr(): String = if (isBlank()) this else "⁦$this⁩"

@Composable
private fun ActionChip(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
