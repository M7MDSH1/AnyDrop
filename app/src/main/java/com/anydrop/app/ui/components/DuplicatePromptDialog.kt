package com.anydrop.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.anydrop.app.R
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.White

/**
 * Shown when the same video (same source URL) is downloaded again. Shared by the
 * deck screen and the share sheet so both entry points handle duplicates.
 *
 * @param copyCount how many copies of this video already exist on-device.
 */
@Composable
fun DuplicatePromptDialog(
    title: String,
    copyCount: Int,
    onReplace: () -> Unit,
    onDownloadAnyway: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MediumGray,
        title = { Text(stringResource(R.string.duplicate_title), color = Amber, fontFamily = FontFamily.Monospace) },
        text = {
            Text(
                text = if (copyCount > 1) {
                    stringResource(R.string.duplicate_message_multi, title, copyCount)
                } else {
                    stringResource(R.string.duplicate_message_single, title)
                },
                color = White,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onReplace) {
                Text(stringResource(R.string.btn_replace), color = Amber, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDownloadAnyway) {
                    Text(
                        stringResource(R.string.btn_download_anyway),
                        color = White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = onCancel) {
                    Text(
                        stringResource(R.string.btn_cancel),
                        color = White.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    )
}