package com.anydrop.app.ui.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ContentPasteSearch
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Box
import com.anydrop.app.ui.components.AppSwitch
import com.anydrop.app.ui.components.PillButton
import com.anydrop.app.ui.components.PillStyle
import com.anydrop.app.ui.components.SectionLabel
import com.anydrop.app.ui.components.SegmentedControl
import com.anydrop.app.ui.theme.TextFaint
import com.anydrop.app.ui.theme.TextMuted
import com.anydrop.app.ui.theme.MediumGray

import com.anydrop.app.ui.theme.AccentText
import com.anydrop.app.ui.theme.AppFont
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anydrop.app.BuildConfig
import com.anydrop.app.R
import com.anydrop.app.data.repository.AppLanguage
import com.anydrop.app.data.repository.SettingsRepository
import com.anydrop.app.data.repository.UpdateStatus
import com.anydrop.app.ui.update.UpdateViewModel
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkGray
import com.anydrop.app.ui.theme.OnAccent
import com.anydrop.app.ui.theme.White
import com.anydrop.app.util.LocaleHelper
import kotlinx.coroutines.launch
import com.anydrop.app.ui.theme.tracked

@Composable
fun SettingsScreen(
    onCookieImport: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val maxQualityHeight by viewModel.maxQualityHeight.collectAsState()
    val autoDetectLinks by viewModel.autoDetectLinks.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()
    val audioLanguages by viewModel.audioLanguages.collectAsState()
    val subtitles by viewModel.subtitles.collectAsState()
    val maxParallel by viewModel.maxParallel.collectAsState()

    var showQualityDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showParallelDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The activity's instance, so "check now" opens the same dialog as the automatic check.
    val updateViewModel: UpdateViewModel = hiltViewModel(context.findActivity())
    val updateStatus by updateViewModel.status.collectAsState()

    LaunchedEffect(Unit) { updateViewModel.refreshStatus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGray)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = White,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Groups scroll on small screens; the version footer stays at the bottom.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SettingsGroup(stringResource(R.string.settings_group_downloads)) {
                SettingsRow(
                    icon = Icons.Filled.Wifi,
                    title = stringResource(R.string.settings_download_over),
                    subtitle = stringResource(if (wifiOnly) R.string.download_over_wifi else R.string.download_over_any),
                    onClick = { viewModel.toggleWifiOnly() }
                ) { AppSwitch(wifiOnly, { viewModel.toggleWifiOnly() }) }
                SettingsRow(
                    icon = Icons.Filled.Layers,
                    title = stringResource(R.string.settings_parallel),
                    subtitle = stringResource(R.string.parallel_value, maxParallel),
                    onClick = { showParallelDialog = true }
                ) { Chevron() }
                SettingsRow(
                    icon = Icons.Filled.HighQuality,
                    title = stringResource(R.string.settings_max_quality),
                    subtitle = stringResource(maxQualityLabelRes(maxQualityHeight)),
                    onClick = { showQualityDialog = true }
                ) { Chevron() }
                SettingsRow(
                    icon = Icons.Filled.FolderOpen,
                    title = stringResource(R.string.settings_download_path),
                    subtitle = stringResource(R.string.settings_download_path_sub),
                    onClick = null
                ) {}
            }

            SettingsGroup(stringResource(R.string.settings_group_media)) {
                SettingsRow(
                    icon = Icons.Filled.Translate,
                    title = stringResource(R.string.settings_audio_languages),
                    subtitle = stringResource(if (audioLanguages) R.string.audio_languages_on else R.string.audio_languages_off),
                    onClick = { viewModel.toggleAudioLanguages() }
                ) { AppSwitch(audioLanguages, { viewModel.toggleAudioLanguages() }) }
                SettingsRow(
                    icon = Icons.Filled.Subtitles,
                    title = stringResource(R.string.settings_subtitles),
                    subtitle = stringResource(if (subtitles) R.string.subtitles_setting_on else R.string.subtitles_setting_off),
                    onClick = { viewModel.toggleSubtitles() }
                ) { AppSwitch(subtitles, { viewModel.toggleSubtitles() }) }
                SettingsRow(
                    icon = Icons.Filled.ContentPasteSearch,
                    title = stringResource(R.string.settings_auto_detect),
                    subtitle = stringResource(if (autoDetectLinks) R.string.auto_detect_on else R.string.auto_detect_off),
                    onClick = { viewModel.toggleAutoDetectLinks() }
                ) { AppSwitch(autoDetectLinks, { viewModel.toggleAutoDetectLinks() }) }
            }

            SettingsGroup(stringResource(R.string.settings_group_app)) {
                SettingsRow(
                    icon = Icons.Filled.Contrast,
                    title = stringResource(R.string.settings_appearance),
                    subtitle = null,
                    onClick = null
                ) {
                    SegmentedControl(
                        labels = listOf(stringResource(R.string.appearance_light), stringResource(R.string.appearance_dark)),
                        selectedIndex = if (darkMode) 1 else 0,
                        onSelect = { if ((it == 1) != darkMode) viewModel.toggleDarkMode() },
                        modifier = Modifier.width(176.dp),
                        segmentHeight = 32.dp
                    )
                }
                SettingsRow(
                    icon = Icons.Filled.Language,
                    title = stringResource(R.string.settings_language),
                    subtitle = stringResource(languageLabelRes(language)),
                    onClick = { showLanguageDialog = true }
                ) { Chevron() }
                SettingsRow(
                    icon = Icons.Filled.Key,
                    title = stringResource(R.string.settings_session_signal),
                    subtitle = stringResource(R.string.settings_session_signal_sub),
                    onClick = onCookieImport
                ) { Chevron() }
            }

            UpdateCard(
                status = updateStatus,
                onUpdate = updateViewModel::showUpdate,
                onCheck = updateViewModel::checkNow
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            color = TextFaint,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_tagline),
            color = TextFaint,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_creator),
            color = AccentText,
            fontSize = 12.sp
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
                    fontFamily = AppFont,
                    color = AccentText
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
                                color = if (maxQualityHeight == height) AccentText else White,
                                fontFamily = AppFont
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
                        fontFamily = AppFont
                    )
                }
            }
        )
    }

    if (showParallelDialog) {
        AlertDialog(
            containerColor = Black,
            titleContentColor = Amber,
            textContentColor = White,
            onDismissRequest = { showParallelDialog = false },
            title = {
                Text(
                    stringResource(R.string.settings_parallel),
                    fontFamily = AppFont,
                    color = AccentText
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    parallelOptions.forEach { count ->
                        TextButton(
                            onClick = {
                                viewModel.setMaxParallel(count)
                                showParallelDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.parallel_option, count),
                                color = if (maxParallel == count) AccentText else White,
                                fontFamily = AppFont
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showParallelDialog = false }) {
                    Text(
                        stringResource(R.string.btn_cancel),
                        color = White.copy(alpha = 0.5f),
                        fontFamily = AppFont
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
                    fontFamily = AppFont,
                    color = AccentText
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
                                color = if (language == option) AccentText else White,
                                fontFamily = AppFont
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
                        fontFamily = AppFont
                    )
                }
            }
        )
    }
}

private val parallelOptions = listOf(1, 2, 3, SettingsRepository.MAX_PARALLEL)

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

private tailrec fun android.content.Context.findActivity(): ComponentActivity = when (this) {
    is ComponentActivity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> error("Settings is not hosted in an activity")
}

/**
 * App updates: what's going on, and one button. An update that was put off ("Later" or
 * "Skip") is still offered here; otherwise the button checks GitHub for a new version.
 */
@Composable
private fun UpdateCard(status: UpdateStatus, onUpdate: () -> Unit, onCheck: () -> Unit) {
    val available = status is UpdateStatus.Available
    val checking = status == UpdateStatus.Checking
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Black)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_updates),
            color = White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = when (status) {
                UpdateStatus.Idle -> stringResource(R.string.update_status_idle, BuildConfig.VERSION_NAME)
                UpdateStatus.Checking -> stringResource(R.string.update_checking)
                UpdateStatus.UpToDate -> stringResource(R.string.update_up_to_date, BuildConfig.VERSION_NAME)
                is UpdateStatus.Available -> stringResource(R.string.update_status_available, status.update.version, BuildConfig.VERSION_NAME)
                UpdateStatus.Failed -> stringResource(R.string.update_status_failed)
            },
            color = TextMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        PillButton(
            text = when {
                available -> stringResource(R.string.update_button_update, (status as UpdateStatus.Available).update.version)
                checking -> stringResource(R.string.update_button_checking)
                else -> stringResource(R.string.update_button_check)
            },
            onClick = { if (available) onUpdate() else onCheck() },
            style = if (available) PillStyle.Primary else PillStyle.Tonal,
            icon = if (available) Icons.Filled.SystemUpdate else Icons.Filled.Refresh,
            enabled = !checking,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** A titled group of settings rows on one card. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title, Modifier.padding(start = 4.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Black),
            content = content
        )
    }
}

/** One setting: icon tile, title and a short line under it, and its control on the right. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MediumGray), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = TextMuted, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextFaint)
}
