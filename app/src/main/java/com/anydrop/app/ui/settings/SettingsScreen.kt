package com.anydrop.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anydrop.app.BuildConfig
import com.anydrop.app.data.repository.SettingsRepository
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkGray
import com.anydrop.app.ui.theme.White

@Composable
fun SettingsScreen(
    onCookieImport: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val potServerUrl by viewModel.potServerUrl.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val maxQualityHeight by viewModel.maxQualityHeight.collectAsState()
    val autoDetectLinks by viewModel.autoDetectLinks.collectAsState()

    var showUrlDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGray)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SETTINGS",
            style = MaterialTheme.typography.headlineMedium,
            color = Amber,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsCard(title = "SESSION SIGNAL", subtitle = "Import cookies for restricted content", onClick = onCookieImport)
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard(
            title = "PO TOKEN SERVER",
            subtitle = if (potServerUrl.contains("127.0.0.1")) "Not configured — tap to set"
                        else potServerUrl,
            onClick = { showUrlDialog = true }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard(
            title = "API KEY",
            subtitle = if (apiKey.isBlank()) "Not configured — tap to set"
                        else apiKey.take(8) + "…" + apiKey.takeLast(4),
            onClick = { showApiKeyDialog = true }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard(
            title = "MAXIMUM QUALITY",
            subtitle = maxQualityLabel(maxQualityHeight),
            onClick = { showQualityDialog = true }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard(
            title = "AUTO-DETECT LINKS",
            subtitle = if (autoDetectLinks) "Enabled — links copied to the clipboard are offered on the deck"
                        else "Disabled",
            onClick = { viewModel.toggleAutoDetectLinks() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard(
            title = "DOWNLOAD LOCATION",
            subtitle = "Videos → Movies/AnyDrop · Images → Pictures/AnyDrop · Audio → Music/AnyDrop",
            onClick = null
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "ANYDROP v${BuildConfig.VERSION_NAME}",
            color = White.copy(alpha = 0.3f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "⚡ The Analog-Inspired Universal Media Transcriber",
            color = White.copy(alpha = 0.2f),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Created by M7MDSH · © 2026",
            color = Amber.copy(alpha = 0.5f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
    }

    if (showUrlDialog) {
        EditTextDialog(
            title = "PO TOKEN SERVER",
            currentValue = potServerUrl,
            hint = "https://pot.example.com",
            onDismiss = { showUrlDialog = false },
            onSave = {
                viewModel.updatePotServerUrl(it)
                showUrlDialog = false
            }
        )
    }

    if (showApiKeyDialog) {
        EditTextDialog(
            title = "API KEY",
            currentValue = apiKey,
            hint = "Your shared secret",
            isPassword = true,
            onDismiss = { showApiKeyDialog = false },
            onSave = {
                viewModel.updateApiKey(it)
                showApiKeyDialog = false
            }
        )
    }

    if (showQualityDialog) {
        AlertDialog(
            containerColor = Black,
            titleContentColor = Amber,
            textContentColor = White,
            onDismissRequest = { showQualityDialog = false },
            title = { Text("MAXIMUM QUALITY", fontFamily = FontFamily.Monospace, color = Amber) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    qualityOptions.forEach { (height, label) ->
                        TextButton(
                            onClick = {
                                viewModel.updateMaxQualityHeight(height)
                                showQualityDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = label,
                                color = if (maxQualityHeight == height) Amber else White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("CANCEL", color = White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}

private val qualityOptions = listOf(
    SettingsRepository.MAX_QUALITY_BEST to "Best available",
    SettingsRepository.MAX_QUALITY_4K to "Up to 4K (2160p)",
    SettingsRepository.MAX_QUALITY_1080P to "Up to 1080p",
    SettingsRepository.MAX_QUALITY_720P to "Up to 720p",
    SettingsRepository.MAX_QUALITY_480P to "Up to 480p"
)

private fun maxQualityLabel(height: Int): String {
    return qualityOptions.firstOrNull { it.first == height }?.second ?: "Best available"
}

@Composable
private fun EditTextDialog(
    title: String,
    currentValue: String,
    hint: String,
    isPassword: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentValue) }
    AlertDialog(
        containerColor = Black,
        titleContentColor = Amber,
        textContentColor = White,
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = FontFamily.Monospace, color = Amber) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(hint, color = White.copy(alpha = 0.3f)) },
                singleLine = true,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = White,
                    unfocusedTextColor = White,
                    cursorColor = Amber,
                    focusedBorderColor = Amber,
                    unfocusedBorderColor = DarkGray
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }) {
                Text("SAVE", color = Amber, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
            }
        }
    )
}

@Composable
private fun SettingsCard(title: String, subtitle: String, onClick: (() -> Unit)?) {
    val base = Modifier.fillMaxWidth()
    val modifier = if (onClick != null) base.clickable(onClick = onClick) else base
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Black)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Amber, fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = subtitle, color = White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            if (onClick != null) {
                Spacer(modifier = Modifier.padding(8.dp))
                Text(
                    text = "▸",
                    color = Amber.copy(alpha = 0.4f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }
        }
    }
}
