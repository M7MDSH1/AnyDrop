package com.anydrop.app.ui.tape_deck.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anydrop.app.R
import com.anydrop.app.data.model.DownloadState
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.AmberDim
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.LedGreen
import com.anydrop.app.ui.theme.LedRed
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.White

@Composable
fun DeckControls(
    modifier: Modifier = Modifier,
    url: String,
    downloadState: DownloadState,
    downloadEnabled: Boolean = true,
    onUrlChange: (String) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onPaste: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            placeholder = { Text(stringResource(R.string.paste_url_hint), fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Amber,
                unfocusedBorderColor = MediumGray,
                cursorColor = Amber,
                focusedTextColor = White,
                unfocusedTextColor = White,
                focusedPlaceholderColor = White.copy(alpha = 0.4f),
                unfocusedPlaceholderColor = White.copy(alpha = 0.4f)
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDownload() }),
            trailingIcon = {
                OutlinedButton(
                    onClick = onPaste,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber)
                ) {
                    Text(stringResource(R.string.paste_btn), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = downloadState) {
            is DownloadState.Progress -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.recording_indicator),
                            color = LedRed,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${state.percentage}%",
                            color = LedGreen,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.percentage / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Amber,
                        trackColor = MediumGray,
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 2nd field carries a phase label ("Starting…") or live speed.
                        Text(
                            text = state.speed.ifBlank { stringResource(R.string.downloading_status) },
                            color = White.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        if (state.eta.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.eta_format, state.eta),
                                color = White.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LedRed,
                        contentColor = White
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(stringResource(R.string.btn_stop), fontFamily = FontFamily.Monospace)
                }
            }
            is DownloadState.Completed -> {
                Text(
                    text = stringResource(R.string.download_complete),
                    color = LedGreen,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            is DownloadState.Error -> {
                Text(
                    text = stringResource(R.string.error_with_message, state.message),
                    color = LedRed,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            else -> {
                Button(
                    onClick = onDownload,
                    enabled = downloadEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Amber,
                        contentColor = Black
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.btn_record),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}
