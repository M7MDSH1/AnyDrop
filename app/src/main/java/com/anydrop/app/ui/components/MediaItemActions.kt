package com.anydrop.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anydrop.app.R
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.LedRed
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.White
import java.io.File

/** The per-item 3-dot dropdown menu: Share / (Add to collection) / Delete / Info. */
@Composable
fun MediaItemDropdown(
    item: MediaItem,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onAddToCollection: (() -> Unit)? = null
) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_share), fontFamily = FontFamily.Monospace) },
            leadingIcon = { Icon(Icons.Default.Share, null, tint = Amber) },
            onClick = {
                onDismiss()
                shareMedia(context, item)
            }
        )
        onAddToCollection?.let { add ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_to_collection), fontFamily = FontFamily.Monospace) },
                leadingIcon = { Icon(Icons.Default.CreateNewFolder, null, tint = Amber) },
                onClick = { onDismiss(); add() }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_delete), fontFamily = FontFamily.Monospace) },
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = LedRed) },
            onClick = { onDismiss(); onDelete() }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_info), fontFamily = FontFamily.Monospace) },
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
        title = { Text(stringResource(R.string.delete_title), color = LedRed, fontFamily = FontFamily.Monospace) },
        text = {
            Text(
                if (count > 1) stringResource(R.string.delete_confirm_multi, count)
                else stringResource(R.string.delete_confirm_single),
                color = White,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete_title), color = LedRed, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
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
        title = { Text(stringResource(R.string.info_title), color = Amber, fontFamily = FontFamily.Monospace) },
        text = {
            Column {
                InfoRow(stringResource(R.string.info_title_label), item.title)
                InfoRow(stringResource(R.string.info_saved_to), item.localFilePath)
                if (!item.isImage) {
                    InfoRow(stringResource(R.string.info_length), formatDuration(context, item.durationMs))
                }
                if (item.creator.isNotBlank()) {
                    val creatorLink = item.creatorUrl.ifBlank { item.url }
                    InfoLinkRow(stringResource(R.string.info_creator), item.creator, creatorLink, context)
                }
                if (item.url.isNotBlank()) {
                    InfoLinkRow(stringResource(R.string.info_source), stringResource(R.string.info_open_original), item.url, context)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close), color = Amber, fontFamily = FontFamily.Monospace)
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

private fun formatDuration(context: android.content.Context, ms: Long): String {
    if (ms <= 0) return context.getString(R.string.info_unknown_duration)
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/**
 * Share the media via its MediaStore content URI (works for all library items
 * on Android 10+). App-private files fall back to a FileProvider URI.
 */
fun shareMedia(context: android.content.Context, item: MediaItem) {
    try {
        val mime = when {
            item.isImage -> "image/*"
            item.isAudio -> "audio/*"
            else -> "video/*"
        }
        val uri: Uri? = when {
            item.localUri?.scheme == "content" -> item.localUri
            else -> {
                val file = File(item.localFilePath)
                if (file.exists()) {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                } else {
                    null
                }
            }
        } ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(shareChooser(context, intent))
    } catch (_: Exception) {}
}

/**
 * The system share sheet for [intent]. NO_USER_ACTION tells Android this is not the
 * user leaving the app, so a playing video does not jump into picture-in-picture.
 */
private fun shareChooser(context: android.content.Context, intent: Intent): Intent =
    Intent.createChooser(intent, context.getString(R.string.share_via))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

/** The web page the item was downloaded from, or null when it has none (e.g. a device video). */
val MediaItem.sourceUrl: String?
    get() = url.trim().takeIf { it.startsWith("https://") || it.startsWith("http://") }

/** Share the link to the page the item was downloaded from, instead of the file. */
fun shareSourceLink(context: android.content.Context, item: MediaItem) {
    val link = item.sourceUrl ?: return
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, link)
            if (item.title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, item.title)
        }
        context.startActivity(shareChooser(context, intent))
    } catch (_: Exception) {}
}
