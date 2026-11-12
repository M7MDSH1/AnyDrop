package com.anydrop.app.ui.components

import com.anydrop.app.ui.theme.AppFont
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anydrop.app.BuildConfig
import com.anydrop.app.R

/**
 * "Copy error details": what a bug report needs (app and Android version, the link, the
 * message shown and the tool's own error), on the clipboard, ready to paste.
 */
object ErrorDetails {

    fun report(message: String, detail: String, link: String, quality: String = ""): String = buildString {
        appendLine("AnyDrop ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        if (link.isNotBlank()) appendLine("Link: $link")
        if (quality.isNotBlank()) appendLine("Quality: $quality")
        appendLine("Error: $message")
        if (detail.isNotBlank() && detail != message) {
            appendLine("Details:")
            append(detail.trim())
        }
    }.trim()

    private const val CLIP_LABEL = "AnyDrop error report"

    /** The clipboard holds a report copied here (the deck's link detection leaves it alone). */
    fun isReport(description: ClipDescription?): Boolean = description?.label == CLIP_LABEL

    fun copy(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
        // Android 13+ shows its own "copied" confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.error_details_copied, Toast.LENGTH_SHORT).show()
        }
    }
}

/** A small "Copy error details" button for an error box. */
@Composable
fun CopyErrorDetailsButton(
    message: String,
    detail: String,
    link: String,
    @Suppress("UNUSED_PARAMETER") color: Color,
    modifier: Modifier = Modifier,
    quality: String = ""
) {
    val context = LocalContext.current
    PillButton(
        text = stringResource(R.string.error_copy_details),
        onClick = { ErrorDetails.copy(context, ErrorDetails.report(message, detail, link, quality)) },
        modifier = modifier,
        style = PillStyle.Ghost,
        small = true
    )
}
