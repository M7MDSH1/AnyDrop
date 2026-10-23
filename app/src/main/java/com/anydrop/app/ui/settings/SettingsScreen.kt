package com.anydrop.app.ui.settings

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

    var showQualityDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The activity's instance, so "check now" opens the same dialog as the automatic check.
    val updateViewModel: UpdateViewModel = hiltViewModel(context.findActivity())
    val updateStatus by updateViewModel.status.collectAsState()

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

        // Cards scroll on small screens; the version footer stays at the bottom.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
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
        Spacer(modifier = Modifier.height(8.dp))
        LaunchedEffect(Unit) { updateViewModel.refreshStatus() }
        UpdateCard(
            status = updateStatus,
            onUpdate = updateViewModel::showUpdate,
            onCheck = updateViewModel::checkNow
        )
        Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            color = White.copy(alpha = 0.3f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = tracked(2f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_tagline),
            color = White.copy(alpha = 0.2f),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = tracked(1f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.settings_creator),
            color = Amber.copy(alpha = 0.5f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = tracked(1f)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Black)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_updates),
                color = Amber,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                letterSpacing = tracked(2f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (status) {
                    UpdateStatus.Idle -> stringResource(R.string.update_status_idle, BuildConfig.VERSION_NAME)
                    UpdateStatus.Checking -> stringResource(R.string.update_checking)
                    UpdateStatus.UpToDate -> stringResource(R.string.update_up_to_date, BuildConfig.VERSION_NAME)
                    is UpdateStatus.Available -> stringResource(R.string.update_status_available, status.update.version, BuildConfig.VERSION_NAME)
                    UpdateStatus.Failed -> stringResource(R.string.update_status_failed)
                },
                color = White.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            val shape = RoundedCornerShape(8.dp)
            val available = status is UpdateStatus.Available
            val checking = status == UpdateStatus.Checking
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(shape)
                    .background(if (available) Amber else Color.Transparent)
                    .border(1.dp, Amber.copy(alpha = if (checking) 0.4f else 1f), shape)
                    .clickable(enabled = !checking) { if (available) onUpdate() else onCheck() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (checking) {
                    CircularProgressIndicator(color = Amber, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        if (available) Icons.Filled.SystemUpdate else Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = if (available) OnAccent else Amber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        available -> stringResource(R.string.update_button_update, (status as UpdateStatus.Available).update.version)
                        checking -> stringResource(R.string.update_button_checking)
                        else -> stringResource(R.string.update_button_check)
                    },
                    color = if (available) OnAccent else Amber.copy(alpha = if (checking) 0.6f else 1f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = tracked(1.5f)
                )
            }
        }
    }
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
                Text(text = title, color = Amber, fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = tracked(2f))
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
