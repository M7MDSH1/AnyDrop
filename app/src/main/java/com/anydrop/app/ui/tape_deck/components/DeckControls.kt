package com.anydrop.app.ui.tape_deck.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anydrop.app.R
import com.anydrop.app.ui.components.CopyErrorDetailsButton
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.Hairline
import com.anydrop.app.ui.theme.LightGray
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.OnAccent
import com.anydrop.app.ui.theme.StatusRed
import com.anydrop.app.ui.theme.White
import com.anydrop.app.ui.theme.tracked

/** The link input: paste from the clipboard, clear, or press Go to record. */
@Composable
fun LinkField(
    url: String,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = url,
        onValueChange = { onUrlChange(it.replace("\n", "")) },
        placeholder = { Text(stringResource(R.string.paste_url_hint), fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Filled.Link, null, tint = if (url.isBlank()) White.copy(alpha = 0.4f) else Amber) },
        trailingIcon = {
            if (url.isBlank()) {
                IconButton(onClick = onPaste) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.paste_btn), tint = Amber)
                }
            } else {
                IconButton(onClick = { onUrlChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.btn_cancel), tint = White.copy(alpha = 0.6f))
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        // URLs are always left-to-right, even in the Arabic UI (otherwise the trailing "/" jumps to the front).
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            textDirection = androidx.compose.ui.text.style.TextDirection.Ltr
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Amber,
            unfocusedBorderColor = LightGray,
            focusedContainerColor = Black,
            unfocusedContainerColor = Black,
            cursorColor = Amber,
            focusedTextColor = White,
            unfocusedTextColor = White,
            focusedPlaceholderColor = White.copy(alpha = 0.4f),
            unfocusedPlaceholderColor = White.copy(alpha = 0.4f)
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() })
    )
}

/** RECORD. Several downloads can run at once; progress lives in the downloads list below. */
@Composable
fun RecordButton(
    enabled: Boolean,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onRecord,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Amber,
            contentColor = OnAccent,
            disabledContainerColor = MediumGray,
            disabledContentColor = White.copy(alpha = 0.38f)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth().height(52.dp)
    ) {
        Text(
            text = stringResource(R.string.btn_record),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = tracked(2f)
        )
    }
}

/**
 * Why the last fetch/download didn't work, in words — never a bare stack trace. With a [detail]
 * (the tool's own error), a button copies everything a bug report needs.
 */
@Composable
fun DeckError(message: String, modifier: Modifier = Modifier, detail: String = "", link: String = "") {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(StatusRed.copy(alpha = 0.10f))
            .border(BorderStroke(1.dp, StatusRed.copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(Icons.Filled.ErrorOutline, null, tint = StatusRed, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = message,
                color = White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = FontFamily.Monospace
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                CopyErrorDetailsButton(message, detail, link, color = StatusRed)
            }
        }
    }
}

/** A tape-deck style panel: recessed surface with a hairline edge. */
@Composable
fun DeckPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Black)
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}
