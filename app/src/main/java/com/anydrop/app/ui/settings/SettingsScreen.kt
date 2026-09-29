package com.anydrop.app.ui.settings

import android.app.Activity
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anydrop.app.BuildConfig
import com.anydrop.app.R
import com.anydrop.app.data.repository.AppLanguage
import com.anydrop.app.data.repository.SettingsRepository
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkGray
import com.anydrop.app.ui.theme.White
import com.anydrop.app.util.LocaleHelper
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onCookieImport: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val maxQualityHeight by viewModel.maxQualityHeight.collectAsState()
    val autoDetectLinks by viewModel.autoDetectLinks.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val language by viewModel.language.collectAsState()

    var showQualityDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGray)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Amber,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsCard(
            title = stringResource(R.string.settings_session_signal),
            subtitle = stringResource(R.string.settings_session_signal_sub),
            onClick = onCookieImport
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard(
            title = stringResource(R.string.settings_appearance),
            subtitle = stringResource(
                if (darkMode) R.string.settings_dark_sub else R.string.settings_light_sub
            ),
            onClick = { viewModel.toggleDarkMode() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard(
            title = stringResource(R.string.settings_language),
            subtitle = stringResource(languageLabelRes(language)),
            onClick = { showLanguageDialog = true }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard(
            title = stringResource(R.string.settings_max_quality),
            subtitle = stringResource(maxQualityLabelRes(maxQualityHeight)),
            onClick = { showQualityDialog = true }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard(
            title = stringResource(R.string.settings_auto_detect),
            subtitle = stringResource(
                if (autoDetectLinks) R.string.auto_detect_on else R.string.auto_detect_off
            ),
            onClick = { viewModel.toggleAutoDetectLinks() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard(
            title = stringResource(R.string.settings_download_path),
            subtitle = stringResource(R.string.settings_download_path_sub),
            onClick = null
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            color = White.copy(alpha = 0.3f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_tagline),
            color = White.copy(alpha = 0.2f),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_creator),
            color = Amber.copy(alpha = 0.5f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
    }

    if (showQualityDialog) {
        AlertDialog(
            containerColor = Black,
            titleContentColor = Amber,
            textContentColor = White,
            onDismissRequest = { showQualityDialog = false },
            title = {
                Text(
                    stringResource(R.string.settings_max_quality),
                    fontFamily = FontFamily.Monospace,
                    color = Amber
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    qualityOptions.forEach { (height, labelRes) ->
                        TextButton(
                            onClick = {
                                viewModel.updateMaxQualityHeight(height)
                                showQualityDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                color = if (maxQualityHeight == height) Amber else White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text(
                        stringResource(R.string.btn_cancel),
                        color = White.copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            containerColor = Black,
            titleContentColor = Amber,
            textContentColor = White,
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    stringResource(R.string.settings_language),
                    fontFamily = FontFamily.Monospace,
                    color = Amber
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    languageOptions.forEach { option ->
                        TextButton(
                            onClick = {
                                scope.launch {
                                    viewModel.setLanguage(option)
                                    (context as? Activity)?.let { activity ->
                                        LocaleHelper.applyAppLocaleNow(activity, option)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(languageLabelRes(option)),
                                color = if (language == option) Amber else White,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(
                        stringResource(R.string.btn_cancel),
                        color = White.copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        )
    }
}

private val languageOptions = listOf(
    AppLanguage.SYSTEM,
    AppLanguage.ENGLISH,
    AppLanguage.ARABIC
)

@StringRes
private fun languageLabelRes(language: AppLanguage): Int = when (language) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.ARABIC -> R.string.language_arabic
}

private val qualityOptions = listOf(
    SettingsRepository.MAX_QUALITY_BEST to R.string.quality_best,
    SettingsRepository.MAX_QUALITY_4K to R.string.quality_4k,
    SettingsRepository.MAX_QUALITY_1080P to R.string.quality_1080,
    SettingsRepository.MAX_QUALITY_720P to R.string.quality_720,
    SettingsRepository.MAX_QUALITY_480P to R.string.quality_480
)

@StringRes
private fun maxQualityLabelRes(height: Int): Int =
    qualityOptions.firstOrNull { it.first == height }?.second ?: R.string.quality_best

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
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Amber.copy(alpha = 0.4f),
                    modifier = Modifier.padding(0.dp)
                )
            }
        }
    }
}
