package com.anydrop.app.ui.components

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.LedRed
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.White
import java.io.File

/** The per-item 3-dot dropdown menu: Share / Delete / Info. */
@Composable
fun MediaItemDropdown(
    item: MediaItem,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit
) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Share", fontFamily = FontFamily.Monospace) },
            leadingIcon = { Icon(Icons.Default.Share, null, tint = Amber) },
            onClick = {
                onDismiss()
                shareMedia(context, item)
            }
        )
        DropdownMenuItem(
            text = { Text("Delete", fontFamily = FontFamily.Monospace) },
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = LedRed) },
            onClick = { onDismiss(); onDelete() }
        )
        DropdownMenuItem(
            text = { Text("Info", fontFamily = FontFamily.Monospace) },
            leadingIcon = { Icon(Icons.Default.Info, null, tint = White) },
            onClick = { onDismiss(); onInfo() }
        )
    }
}

/** Confirm-before-delete dialog. Handles single or multiple items. */
@Composable
fun DeleteConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MediumGray,
        title = { Text("DELETE", color = LedRed, fontFamily = FontFamily.Monospace) },
        text = {
            Text(
                if (count > 1) "Are you sure you want to delete these $count items?"
                else "Are you sure you want to delete this content?",
                color = White,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("DELETE", color = LedRed, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
            }
        }
    )
}

/** Info dialog: title, location, length, clickable source URL, clickable creator. */
@Composable
fun MediaInfoDialog(
    item: MediaItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MediumGray,
        title = { Text("INFO", color = Amber, fontFamily = FontFamily.Monospace) },
        text = {
            Column {
                InfoRow("Title", item.title)
                InfoRow("Saved to", item.localFilePath)
                if (!item.isImage) {
                    InfoRow("Length", formatDuration(item.durationMs))
                }
                if (item.creator.isNotBlank()) {
                    val creatorLink = item.creatorUrl.ifBlank { item.url }
                    InfoLinkRow("Creator", item.creator, creatorLink, context)
                }
                if (item.url.isNotBlank()) {
                    InfoLinkRow("Source", "Open original ↗", item.url, context)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = Amber, fontFamily = FontFamily.Monospace)
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label.uppercase(), color = Amber.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Text(value, color = White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun InfoLinkRow(label: String, value: String, url: String, context: android.content.Context) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label.uppercase(), color = Amber.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Text(
            value,
            color = Amber,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.fillMaxWidth().clickableNoRipple {
                if (url.isNotBlank()) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        )
        Spacer(Modifier.height(10.dp))
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick
    )

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "Unknown"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Share the media file via a FileProvider content URI. */
fun shareMedia(context: android.content.Context, item: MediaItem) {
    try {
        val file = File(item.localFilePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = when {
            item.isImage -> "image/*"
            item.isAudio -> "audio/*"
            else -> "video/*"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {}
}
