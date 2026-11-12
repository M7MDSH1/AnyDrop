package com.anydrop.app

import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import com.anydrop.app.ui.components.AppMark
import com.anydrop.app.ui.components.PillButton
import com.anydrop.app.ui.theme.AccentSoft
import com.anydrop.app.ui.theme.NumberFont
import com.anydrop.app.ui.theme.TextFaint
import com.anydrop.app.ui.theme.TextMuted

import com.anydrop.app.ui.theme.AccentText
import com.anydrop.app.ui.theme.AppFont
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.anydrop.app.R
import com.anydrop.app.engine.Magnet
import com.anydrop.app.engine.TorrentEngine
import com.anydrop.app.engine.TorrentException
import com.anydrop.app.engine.distinctPageVideos
import com.anydrop.app.engine.torrentFileList
import com.anydrop.app.ui.components.TorrentPicker
import com.anydrop.app.ui.components.AudioVoicePicker
import com.anydrop.app.ui.components.SubtitlePicker
import com.anydrop.app.ui.components.CopyErrorDetailsButton
import com.anydrop.app.engine.LinkList
import com.anydrop.app.engine.isLinkList
import com.anydrop.app.engine.isPlaylist
import com.anydrop.app.engine.isWholePage
import com.anydrop.app.ui.components.PlaylistOffer
import com.anydrop.app.engine.pageItems
import com.anydrop.app.engine.postItems
import com.anydrop.app.ui.components.FetchModeToggle
import com.anydrop.app.ui.components.MediaSummaryCard
import com.anydrop.app.ui.components.MediaThumb
import com.anydrop.app.ui.components.PageMediaPicker
import com.anydrop.app.ui.components.PostItemsPicker
import com.anydrop.app.data.model.DownloadState
import com.anydrop.app.data.repository.readAppLanguageSync
import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import com.anydrop.app.ui.components.DuplicatePromptDialog
import com.anydrop.app.ui.tape_deck.TapeDeckViewModel
import com.anydrop.app.util.LocaleHelper
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.AnyDropTheme
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkGray
import com.anydrop.app.ui.theme.LedGreen
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.StatusGreen
import com.anydrop.app.ui.theme.StatusRed
import com.anydrop.app.ui.theme.White
import com.anydrop.app.ui.settings.AppearanceViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import com.anydrop.app.ui.theme.tracked

@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    @Inject lateinit var torrentEngine: TorrentEngine

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(
            LocaleHelper.wrapAppLocale(newBase, readAppLanguageSync(newBase))
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedUrl = extractUrlFromIntent(intent)
        if (sharedUrl.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            // Follows the app's appearance setting, like the rest of the app.
            val appearance: AppearanceViewModel = hiltViewModel()
            val darkMode by appearance.darkMode.collectAsState()
            AnyDropTheme(darkTheme = darkMode) {
                ShareSheet(
                    sharedUrl = sharedUrl,
                    onClose = { finish() }
                )
            }
        }
    }

    /**
     * The link to fetch: shared text (a web or magnet link in it), a magnet: link opened with
     * AnyDrop, or a .torrent file opened with AnyDrop (read here and handed on as its magnet).
     */
    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            // Several links shared at once (a note, a message) are listed together.
            val links = LinkList.extract(text)
            if (links.size >= 2) return LinkList.oneLine(links)
            return Magnet.find(text) ?: links.firstOrNull()
        }
        if (intent.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        if (data.scheme.equals("magnet", ignoreCase = true)) return data.toString()
        return try {
            val bytes = contentResolver.openInputStream(data)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0 || out.size() > 10 * 1024 * 1024) break
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            } ?: return null
            torrentEngine.open(bytes).second
        } catch (e: TorrentException) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            null
        } catch (e: Exception) {
            null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(
    sharedUrl: String,
    onClose: () -> Unit,
    viewModel: TapeDeckViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScopeCompat()

    // One-time POST_NOTIFICATIONS request at the first download attempt (Android
    // 13+); downloads proceed regardless, in-app feedback is unaffected.
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotificationPermission(run: () -> Unit) {
        run()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val isLoading by viewModel.isLoadingFormats.collectAsState()
    val formats by viewModel.availableFormats.collectAsState()
    val audioVoices by viewModel.audioVoices.collectAsState()
    val audioLanguage by viewModel.audioLanguage.collectAsState()
    val captions by viewModel.captions.collectAsState()
    val subtitleLanguage by viewModel.subtitleLanguage.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val duplicatePrompt by viewModel.duplicatePrompt.collectAsState()
    val fetchWholePage by viewModel.fetchWholePage.collectAsState()
    val pageTitle by viewModel.pageTitle.collectAsState()
    val resolving by viewModel.resolving.collectAsState()
    val playlistOffer by viewModel.playlistOffer.collectAsState()
    val fetchingPlaylist by viewModel.fetchingPlaylist.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // Kick off the fetch in the background as soon as the sheet appears.
    androidx.compose.runtime.LaunchedEffect(sharedUrl) {
        viewModel.handleSharedUrl(sharedUrl)
    }

    // "Download right away": a plain video goes straight to the best quality the settings
    // allow. Whatever needs a choice (a page, a post with several items, photos) still asks.
    val preselected by viewModel.selectedFormat.collectAsState()
    var quickStarted by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(isLoading, preselected) {
        val pick = preselected
        if (quickStarted || !viewModel.quickShare || isLoading || pick == null || pick.isImage) return@LaunchedEffect
        if (formats.isWholePage || formats.isLinkList || formats.postItems().size > 1) return@LaunchedEffect
        quickStarted = true
        ensureNotificationPermission { viewModel.startDownload() }
    }

    // Close shortly after a download starts.
    androidx.compose.runtime.LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Progress || downloadState is DownloadState.Completed) {
            Toast.makeText(context, R.string.downloading_ellipsis, Toast.LENGTH_SHORT).show()
            scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = Black,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        val images = formats.filter { it.isImage && it.imageUrl.isNotBlank() }
        val videoAudio = formats.filter { !it.isImage }
        val pageItems = remember(formats) { if (formats.isWholePage) formats.pageItems() else emptyList() }
        val torrentFiles = remember(formats) { formats.torrentFileList() }
        val isTorrent = remember(sharedUrl) { Magnet.isTorrentLink(sharedUrl) }
        val postItems = remember(formats) { if (formats.isWholePage) emptyList() else formats.postItems() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppMark(36.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.share_select_what),
                    color = White,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            if (!isTorrent && !LinkList.isList(sharedUrl)) {
                Spacer(Modifier.height(12.dp))
                FetchModeToggle(wholePage = fetchWholePage, onChange = viewModel::setFetchWholePage)
            }
            if (formats.any { it.isFromPage } && !formats.isWholePage) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.found_on_page),
                    color = StatusGreen,
                    fontSize = 12.sp,
                    fontFamily = AppFont,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(StatusGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            if (playlistOffer && !isLoading) {
                PlaylistOffer(onShowPlaylist = viewModel::loadPlaylist)
                Spacer(Modifier.height(12.dp))
            }

            when {
                isLoading || formats.isEmpty() && downloadState !is DownloadState.Error -> {
                    // Fetching
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AccentText, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(14.dp))
                            Text(
                                stringResource(
                                    when {
                                        isTorrent -> R.string.fetching_torrent
                                        fetchingPlaylist -> R.string.fetching_playlist
                                        fetchWholePage -> R.string.fetching_whole_page
                                        else -> R.string.share_fetching
                                    }
                                ),
                                color = TextMuted,
                                fontFamily = AppFont,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                downloadState is DownloadState.Error -> {
                    val error = downloadState as DownloadState.Error
                    Text(
                        error.message,
                        color = StatusRed,
                        fontFamily = AppFont,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    if (error.detail.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        CopyErrorDetailsButton(error.message, error.detail, link = sharedUrl, color = StatusRed)
                    }
                }

                // A torrent: pick its files.
                torrentFiles.isNotEmpty() -> {
                    TorrentPicker(
                        name = pageTitle,
                        files = torrentFiles,
                        onDownload = { chosen -> ensureNotificationPermission { viewModel.downloadTorrent(chosen) } },
                        maxListHeight = 320.dp
                    )
                }

                // Whole page: everything on it, to pick from.
                pageItems.isNotEmpty() -> {
                    AudioVoicePicker(voices = audioVoices, selected = audioLanguage, onSelect = viewModel::selectAudioLanguage)
                    if (audioVoices.isNotEmpty()) Spacer(Modifier.height(12.dp))
                    PageMediaPicker(
                        items = pageItems,
                        pageTitle = pageTitle,
                        pageUrl = sharedUrl,
                        resolving = resolving,
                        onResolve = viewModel::resolvePageItem,
                        onDownload = { chosen -> ensureNotificationPermission { viewModel.downloadPageItems(chosen) } },
                        maxListHeight = 380.dp,
                        playlist = formats.isPlaylist || formats.isLinkList,
                        onDownloadAudio = { chosen -> ensureNotificationPermission { viewModel.downloadPageItems(chosen, audioOnly = true) } },
                        linkList = formats.isLinkList
                    )
                }

                // A post with several photos/videos (a carousel): pick any of its slides, or all.
                postItems.isNotEmpty() -> {
                    PostItemsPicker(
                        items = postItems,
                        onDownload = { chosen -> ensureNotificationPermission { viewModel.downloadPostItems(chosen) } }
                    )
                }

                else -> {
                    // One video: its cover, length and title above the qualities. Several different
                    // video files from a page (the video, an ad...) each show their own instead.
                    val severalVideos = remember(formats) { formats.distinctPageVideos() > 1 }
                    val video = formats.firstOrNull { !it.isImage && !it.isAudioOnly }
                    if (!severalVideos && video != null &&
                        (video.previewUrl.isNotBlank() || video.durationSec > 0 || pageTitle.isNotBlank())
                    ) {
                        MediaSummaryCard(cover = video.previewUrl, durationSec = video.durationSec, title = pageTitle)
                        Spacer(Modifier.height(12.dp))
                    }
                    AudioVoicePicker(voices = audioVoices, selected = audioLanguage, onSelect = viewModel::selectAudioLanguage)
                    if (audioVoices.isNotEmpty()) Spacer(Modifier.height(12.dp))
                    SubtitlePicker(captions = captions, selected = subtitleLanguage, onSelect = viewModel::selectSubtitles)
                    if (captions.isNotEmpty()) Spacer(Modifier.height(12.dp))
                    ShareQualityList(
                        showCovers = severalVideos,
                        // Mixed carousels list their photos too; otherwise just video/audio.
                        formats = if (images.size > 1) formats else videoAudio.ifEmpty { formats },
                        onRecord = { fmt ->
                            ensureNotificationPermission {
                                viewModel.selectFormat(fmt)
                                viewModel.startDownload()
                            }
                        }
                    )
                }
            }
        }
    }

    // Same-video duplicate prompt, rendered over the sheet (the sheet previously
    // had no UI for this, so RECORD silently did nothing on a duplicate).
    duplicatePrompt?.let { prompt ->
        DuplicatePromptDialog(
            title = prompt.title,
            copyCount = prompt.copyCount,
            onReplace = { ensureNotificationPermission { viewModel.confirmReplaceDownload() } },
            onDownloadAnyway = { ensureNotificationPermission { viewModel.confirmDownloadAnyway() } },
            onCancel = { viewModel.dismissDuplicatePrompt() }
        )
    }
}

/** Compact quality list: select a row, then press RECORD to download. */
@Composable
private fun ShareQualityList(
    formats: List<FormatInfo>,
    onRecord: (FormatInfo) -> Unit,
    showCovers: Boolean = false
) {
    var selectedId by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    // Default-select the first (usually highest quality) so RECORD works with one tap.
    androidx.compose.runtime.LaunchedEffect(formats) {
        if (selectedId == null && formats.isNotEmpty()) selectedId = formats.first().uniqueKey()
    }
    val selected = formats.firstOrNull { it.uniqueKey() == selectedId }

    Column(modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(formats) { fmt ->
                val isSel = fmt.uniqueKey() == selectedId
                val shape = RoundedCornerShape(16.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(if (isSel) AccentSoft else MediumGray)
                        .then(if (isSel) Modifier.border(1.5.dp, Amber, shape) else Modifier)
                        .selectable(selected = isSel, role = Role.RadioButton, onClick = { selectedId = fmt.uniqueKey() })
                        .padding(horizontal = if (showCovers) 8.dp else 16.dp, vertical = if (showCovers) 8.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showCovers && !fmt.isImage && !fmt.isAudioOnly) {
                        MediaThumb(
                            cover = fmt.previewUrl,
                            durationSec = fmt.durationSec,
                            isVideo = true,
                            modifier = Modifier.width(78.dp).aspectRatio(16f / 9f)
                        )
                        Spacer(Modifier.width(10.dp))
                    } else {
                        Icon(
                            if (isSel) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSel) AccentText else TextFaint,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        fmt.resolution,
                        color = White,
                        fontFamily = AppFont,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        fmt.extension.uppercase(),
                        color = TextFaint,
                        fontFamily = NumberFont,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        PillButton(
            text = stringResource(R.string.share_record),
            onClick = { selected?.let(onRecord) },
            enabled = selected != null,
            icon = Icons.Filled.Download,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        )
    }
}

/** Stable key for selection (id if present, else resolution+ext). */
private fun FormatInfo.uniqueKey(): String =
    if (id.isNotBlank()) "$id-$directUrl" else "$resolution-$extension-$imageUrl"

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()
