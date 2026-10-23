package com.anydrop.app.ui.update

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.anydrop.app.R
import com.anydrop.app.data.repository.AppUpdate
import com.anydrop.app.data.repository.UpdateDownload
import com.anydrop.app.data.repository.UpdateRepository
import com.anydrop.app.data.repository.UpdateStatus
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.LedRed
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.White
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Update prompts. Scoped to MainActivity so the dialog it drives sits above every
 * screen; Settings reaches the same instance to check on demand.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updates: UpdateRepository
) : ViewModel() {

    val status: StateFlow<UpdateStatus> = updates.status
    val download: StateFlow<UpdateDownload> = updates.download
    val currentVersion: String get() = updates.currentVersion

    // The update the dialog shows; null while it is closed.
    private val _dialog = MutableStateFlow<AppUpdate?>(null)
    val dialog: StateFlow<AppUpdate?> = _dialog.asStateFlow()

    private var launchChecked = false
    private var downloadJob: Job? = null

    /** App start: a quiet check (GitHub is asked at most every few hours); offers the update unless skipped. */
    fun checkOnLaunch() {
        if (launchChecked) return
        launchChecked = true
        viewModelScope.launch(Dispatchers.IO) { updates.cleanUp() }
        viewModelScope.launch {
            val update = updates.check(force = false) ?: return@launch
            if (!updates.isSkipped(update.version) && !updates.isSnoozed(update.version)) {
                _dialog.value = update
                // Seen here already: the background check won't also notify about it.
                updates.markNotified(update.version)
            }
        }
    }

    /** From the notification or Settings: show what is available, even a skipped version. */
    fun showUpdate() {
        viewModelScope.launch {
            val known = (status.value as? UpdateStatus.Available)?.update
            (known ?: updates.check(force = false))?.let { _dialog.value = it }
        }
    }

    /**
     * Settings opened: learn whether an update is waiting (from the last answer when it's
     * recent), without opening the dialog.
     */
    fun refreshStatus() {
        if (status.value !is UpdateStatus.Idle) return
        viewModelScope.launch { updates.check(force = false) }
    }

    /** Settings: ask GitHub now. */
    fun checkNow() {
        viewModelScope.launch {
            updates.check(force = true)?.let { _dialog.value = it }
        }
    }

    fun later() {
        _dialog.value?.let { updates.snooze(it.version) }
        close()
    }

    fun skip() {
        _dialog.value?.let { updates.skip(it.version) }
        close()
    }

    private fun close() {
        cancelDownload()
        _dialog.value = null
    }

    fun startDownload() {
        val update = _dialog.value ?: return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch { updates.downloadApk(update) }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        updates.resetDownload()
    }

    fun canInstallApps() = updates.canInstallApps()
    fun allowInstallsIntent(): Intent = updates.allowInstallsIntent()
    fun installIntent(download: UpdateDownload.Ready): Intent = updates.installIntent(download.apk)
}

/** Shows the update dialog when [UpdateViewModel] has one to offer. Place once, above the app. */
@Composable
fun UpdatePrompt(viewModel: UpdateViewModel) {
    val update by viewModel.dialog.collectAsState()
    val download by viewModel.download.collectAsState()
    val context = LocalContext.current
    val shown = update ?: return

    // Re-read after returning from the "install unknown apps" settings screen.
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumes++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val canInstall = remember(resumes, download) { viewModel.canInstallApps() }

    // Once downloaded (and allowed), go straight to Android's installer.
    LaunchedEffect(download, canInstall) {
        val ready = download as? UpdateDownload.Ready ?: return@LaunchedEffect
        if (canInstall) runCatching { context.startActivity(viewModel.installIntent(ready)) }
    }

    UpdateDialog(
        update = shown,
        currentVersion = viewModel.currentVersion,
        download = download,
        canInstall = canInstall,
        onUpdate = viewModel::startDownload,
        onCancel = viewModel::cancelDownload,
        onLater = viewModel::later,
        onSkip = viewModel::skip,
        onInstall = { ready -> runCatching { context.startActivity(viewModel.installIntent(ready)) } },
        onAllowInstalls = { runCatching { context.startActivity(viewModel.allowInstallsIntent()) } },
        onOpenPage = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(shown.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    )
}

@Composable
private fun UpdateDialog(
    update: AppUpdate,
    currentVersion: String,
    download: UpdateDownload,
    canInstall: Boolean,
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
    onInstall: (UpdateDownload.Ready) -> Unit,
    onAllowInstalls: () -> Unit,
    onOpenPage: () -> Unit
) {
    val context = LocalContext.current
    val arabic = LocalConfiguration.current.locales[0]?.language == "ar"
    val notes = remember(update.notes, arabic) { UpdateRepository.notesFor(update.notes, arabic) }
    val accent = Amber
    val formattedNotes = remember(notes, accent) { markdownToText(notes, accent) }
    // No APK for this phone, or one that can't be installed over this app: send to the page.
    val pageOnly = update.apkUrl == null ||
        (download as? UpdateDownload.Failed)?.reason.let { it == UpdateDownload.Reason.DIFFERENT_KEY || it == UpdateDownload.Reason.NOT_ANYDROP }

    AlertDialog(
        onDismissRequest = { if (download !is UpdateDownload.Downloading) onLater() },
        containerColor = MediumGray,
        title = { Text(stringResource(R.string.update_title), color = Amber, fontFamily = FontFamily.Monospace) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.update_message, update.version, currentVersion), color = White, fontSize = 14.sp)
                if (update.apkSize > 0 && !pageOnly) {
                    Text(
                        stringResource(R.string.update_size, Formatter.formatShortFileSize(context, update.apkSize)),
                        color = White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                if (formattedNotes.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                        // Each paragraph reads in its own direction (English notes in the Arabic UI).
                        Text(
                            formattedNotes,
                            color = White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            style = TextStyle(textDirection = TextDirection.Content)
                        )
                    }
                }
                when (download) {
                    is UpdateDownload.Downloading -> {
                        LinearProgressIndicator(
                            progress = { download.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Amber,
                            trackColor = White.copy(alpha = 0.15f),
                            strokeCap = StrokeCap.Round
                        )
                        Text(stringResource(R.string.update_downloading, download.percent), color = White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    is UpdateDownload.Failed -> Text(
                        stringResource(
                            when (download.reason) {
                                UpdateDownload.Reason.NETWORK -> R.string.update_failed
                                UpdateDownload.Reason.NOT_ANYDROP -> R.string.update_not_anydrop
                                UpdateDownload.Reason.DIFFERENT_KEY -> R.string.update_signature_mismatch
                            }
                        ),
                        color = LedRed,
                        fontSize = 12.sp
                    )
                    is UpdateDownload.Ready -> if (!canInstall) {
                        Text(stringResource(R.string.update_allow_installs), color = White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                    UpdateDownload.Idle -> Unit
                }
            }
        },
        confirmButton = {
            when {
                pageOnly -> DialogButton(stringResource(R.string.update_open_page), Amber, onOpenPage)
                download is UpdateDownload.Downloading -> Unit
                download is UpdateDownload.Ready && !canInstall -> DialogButton(stringResource(R.string.update_allow), Amber, onAllowInstalls)
                download is UpdateDownload.Ready -> DialogButton(stringResource(R.string.update_install), Amber) { onInstall(download) }
                download is UpdateDownload.Failed -> DialogButton(stringResource(R.string.btn_retry), Amber, onUpdate)
                else -> DialogButton(stringResource(R.string.update_now), Amber, onUpdate)
            }
        },
        dismissButton = {
            Row {
                when {
                    download is UpdateDownload.Downloading -> DialogButton(stringResource(R.string.btn_cancel), White.copy(alpha = 0.6f), onCancel)
                    else -> {
                        if (download !is UpdateDownload.Ready) {
                            DialogButton(stringResource(R.string.update_skip), White.copy(alpha = 0.45f), onSkip)
                        }
                        DialogButton(stringResource(R.string.update_later), White.copy(alpha = 0.6f), onLater)
                    }
                }
            }
        }
    )
}

@Composable
private fun DialogButton(label: String, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

/**
 * Release notes are Markdown; show them as plain styled text: headings in the accent
 * colour, "-" bullets as "•", **bold** kept, links reduced to their text.
 */
private fun markdownToText(markdown: String, accent: Color): AnnotatedString = buildAnnotatedString {
    val link = Regex("""\[([^\]]+)]\([^)]*\)""")
    markdown.lines().forEachIndexed { index, raw ->
        if (index > 0) append('\n')
        var line = link.replace(raw.trimEnd()) { it.groupValues[1] }
        val heading = line.startsWith("#")
        if (heading) {
            line = line.trimStart('#').trim()
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(line.replace("**", "")) }
            return@forEachIndexed
        }
        if (line.startsWith("- ") || line.startsWith("* ")) line = "• " + line.drop(2)
        // **bold** runs.
        val parts = line.split("**")
        parts.forEachIndexed { i, part ->
            if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
        }
    }
}
