package com.anydrop.app.ui.tape_deck

import androidx.compose.foundation.selection.selectable
import com.anydrop.app.ui.theme.StatusRed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.ContentCut
import com.anydrop.app.ui.components.AppChip
import com.anydrop.app.engine.ClipRange
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.Role
import com.anydrop.app.ui.components.AppMark
import com.anydrop.app.ui.components.PillButton
import com.anydrop.app.ui.components.PillStyle
import com.anydrop.app.ui.components.SectionLabel
import com.anydrop.app.ui.theme.AccentSoft
import com.anydrop.app.ui.theme.NumberFont
import com.anydrop.app.ui.theme.TextFaint
import com.anydrop.app.ui.theme.TextMuted

import com.anydrop.app.ui.theme.AccentText
import com.anydrop.app.ui.theme.AppFont
import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.anydrop.app.data.model.DownloadState
import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import com.anydrop.app.ui.components.AudioVoicePicker
import com.anydrop.app.ui.components.SubtitlePicker
import com.anydrop.app.ui.components.DuplicatePromptDialog
import com.anydrop.app.ui.components.ErrorDetails
import com.anydrop.app.ui.tape_deck.components.LinkField
import com.anydrop.app.ui.tape_deck.components.RecordButton
import com.anydrop.app.ui.tape_deck.components.DeckError
import com.anydrop.app.ui.tape_deck.components.DownloadQueuePanel
import com.anydrop.app.ui.theme.StatusGreen
import com.anydrop.app.util.Constants
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.anydrop.app.R
import com.anydrop.app.showDownloadsRequest
import com.anydrop.app.engine.Magnet
import com.anydrop.app.engine.distinctPageVideos
import com.anydrop.app.engine.torrentFileList
import com.anydrop.app.ui.components.TorrentPicker
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
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.AmberBright
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkGray
import com.anydrop.app.ui.theme.Hairline
import com.anydrop.app.ui.theme.LedBlue
import com.anydrop.app.ui.theme.LedGreen
import com.anydrop.app.ui.theme.LedRed
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.OnAccent
import com.anydrop.app.ui.theme.VuGreen
import com.anydrop.app.ui.theme.VuRed
import com.anydrop.app.ui.theme.VuYellow
import com.anydrop.app.ui.theme.White
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import com.anydrop.app.ui.theme.tracked

@Composable
fun TapeDeckScreen(
    viewModel: TapeDeckViewModel = hiltViewModel()
) {
    val url by viewModel.url.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val isCookieImported by viewModel.isCookieImported.collectAsState()
    val availableFormats by viewModel.availableFormats.collectAsState()
    val audioVoices by viewModel.audioVoices.collectAsState()
    val audioLanguage by viewModel.audioLanguage.collectAsState()
    val captions by viewModel.captions.collectAsState()
    val subtitleLanguage by viewModel.subtitleLanguage.collectAsState()
    val selectedFormat by viewModel.selectedFormat.collectAsState()
    val isLoadingFormats by viewModel.isLoadingFormats.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val mediaInfo by viewModel.mediaInfo.collectAsState()
    val clipOn by viewModel.clipOn.collectAsState()
    val clipFrom by viewModel.clipFrom.collectAsState()
    val clipTo by viewModel.clipTo.collectAsState()
    val duplicatePrompt by viewModel.duplicatePrompt.collectAsState()
    val autoDetectLinks by viewModel.autoDetectLinks.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val fetchWholePage by viewModel.fetchWholePage.collectAsState()
    val pageTitle by viewModel.pageTitle.collectAsState()
    val resolving by viewModel.resolving.collectAsState()
    val playlistOffer by viewModel.playlistOffer.collectAsState()
    val fetchingPlaylist by viewModel.fetchingPlaylist.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

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

    // Foreground-only clipboard monitoring: while the deck is on screen, poll the
    // clipboard and offer any new link (Android forbids background clipboard reads
    // since API 29, so this only works while the app is visible — which is honest).
    var detectedLink by remember { mutableStateOf<String?>(null) }
    var lastSeenClip by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(autoDetectLinks) {
        if (!autoDetectLinks) return@LaunchedEffect
        while (true) {
            delay(1500)
            val clip = runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                // An error report AnyDrop just copied holds the link, but isn't one to load.
                if (ErrorDetails.isReport(cm.primaryClipDescription)) ""
                else cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
            }.getOrDefault("")
            if (clip.isBlank() || clip == lastSeenClip || clip == viewModel.url.value) continue
            if (clip.isLinkLike()) {
                lastSeenClip = clip
                detectedLink = clip
                viewModel.prefetch(clip)
            }
        }
    }

    // "Already downloaded" dialog when the same video is re-downloaded.
    duplicatePrompt?.let { prompt ->
        DuplicatePromptDialog(
            title = prompt.title,
            copyCount = prompt.copyCount,
            onReplace = { ensureNotificationPermission { viewModel.confirmReplaceDownload() } },
            onDownloadAnyway = { ensureNotificationPermission { viewModel.confirmDownloadAnyway() } },
            onCancel = { viewModel.dismissDuplicatePrompt() }
        )
    }

    // Read here so the chip shows up once the background check is done.
    val clipSupported by viewModel.clipSupported.collectAsState()
    val clipFormat = selectedFormat?.takeIf { clipSupported && viewModel.clipAllowed(it) }
    val clipping = clipOn && clipFormat != null
    val clipValid = !clipping ||
        ClipRange.isValid(ClipRange.parse(clipFrom), ClipRange.parse(clipTo), clipFormat?.durationSec ?: 0)
    val canDownload = selectedFormat != null && url.isNotBlank() && clipValid
    // Whole-page lists, carousels and torrents have their own download button; a single video
    // gets the one at the bottom of the screen.
    val pageItems = remember(availableFormats) { if (availableFormats.isWholePage) availableFormats.pageItems() else emptyList() }
    val postItems = remember(availableFormats) { if (availableFormats.isWholePage) emptyList() else availableFormats.postItems() }
    val torrentFiles = remember(availableFormats) { availableFormats.torrentFileList() }
    val multiItem = postItems.isNotEmpty() || pageItems.isNotEmpty() || torrentFiles.isNotEmpty()

    // A tapped download notification scrolls down to the downloads list.
    val scrollState = rememberScrollState()
    var downloadsTop by remember { mutableStateOf<Int?>(null) }
    val showDownloads by showDownloadsRequest.collectAsState()
    LaunchedEffect(showDownloads) {
        if (!showDownloads) return@LaunchedEffect
        val top = withTimeoutOrNull(2_000) { snapshotFlow { downloadsTop }.filterNotNull().first() }
        if (top != null) scrollState.animateScrollTo(top)
        showDownloadsRequest.value = false
    }

    Column(Modifier.fillMaxSize().background(DarkGray)) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        DeckHeader()

        Spacer(modifier = Modifier.height(16.dp))

        detectedLink?.let { link ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Black)
                    .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.link_detected),
                        color = StatusGreen,
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                    Text(
                        text = link,
                        color = TextMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PillButton(
                    text = stringResource(R.string.load_link),
                    onClick = {
                        viewModel.onUrlChange(link)
                        detectedLink = null
                    },
                    style = PillStyle.Tonal,
                    small = true
                )
                IconButton(onClick = { detectedLink = null }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.btn_cancel), tint = TextMuted)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        LinkField(
            url = url,
            onUrlChange = viewModel::onUrlChange,
            onPaste = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.let {
                    viewModel.onUrlChange(it)
                }
            },
            onGo = { if (canDownload) ensureNotificationPermission { viewModel.startDownload() } }
        )

        // A torrent has no page to read, and pasted links are a list already: the video/page
        // switch only shows for one web link.
        val isTorrent = remember(url) { Magnet.isTorrentLink(url) }
        val isLinkList = remember(url) { LinkList.isList(url) }
        if (!isTorrent && !isLinkList) {
            Spacer(modifier = Modifier.height(8.dp))
            FetchModeToggle(wholePage = fetchWholePage, onChange = viewModel::setFetchWholePage)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoadingFormats) {
            // What the link is, as soon as the site says: the qualities take longer.
            preview?.let {
                MediaSummaryCard(cover = it.cover, durationSec = 0, title = it.title)
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = AccentText,
                    strokeWidth = 2.dp,
                    modifier = Modifier.width(16.dp).height(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(
                        when {
                            isTorrent -> R.string.fetching_torrent
                            fetchingPlaylist -> R.string.fetching_playlist
                            fetchWholePage -> R.string.fetching_whole_page
                            else -> R.string.fetching_qualities
                        }
                    ),
                    color = White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontFamily = AppFont
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Found by reading the page itself (Instagram and friends): say so, no login involved.
        if (availableFormats.any { it.isFromPage }) {
            Text(
                text = stringResource(R.string.found_on_page),
                color = StatusGreen,
                fontSize = 10.sp,
                fontFamily = AppFont,
                letterSpacing = tracked(1f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(StatusGreen.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (playlistOffer && !isLoadingFormats) {
            PlaylistOffer(onShowPlaylist = viewModel::loadPlaylist)
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Whole page: everything on it, to pick from. Otherwise a post with several
        // photos/videos (a carousel) offers its slides, and a single video its qualities.
        if (torrentFiles.isNotEmpty()) {
            TorrentPicker(
                name = pageTitle,
                files = torrentFiles,
                onDownload = { chosen -> ensureNotificationPermission { viewModel.downloadTorrent(chosen) } }
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else if (pageItems.isNotEmpty()) {
            AudioVoicePicker(voices = audioVoices, selected = audioLanguage, onSelect = viewModel::selectAudioLanguage)
            if (audioVoices.isNotEmpty()) Spacer(modifier = Modifier.height(12.dp))
            PageMediaPicker(
                items = pageItems,
                pageTitle = pageTitle,
                pageUrl = url,
                resolving = resolving,
                onResolve = viewModel::resolvePageItem,
                onDownload = { chosen -> ensureNotificationPermission { viewModel.downloadPageItems(chosen) } },
                playlist = availableFormats.isPlaylist || availableFormats.isLinkList,
                onDownloadAudio = { chosen -> ensureNotificationPermission { viewModel.downloadPageItems(chosen, audioOnly = true) } },
                linkList = availableFormats.isLinkList
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else if (postItems.isNotEmpty()) {
            PostItemsPicker(
                items = postItems,
                onDownload = { chosen -> ensureNotificationPermission { viewModel.downloadPostItems(chosen) } }
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else if (availableFormats.isNotEmpty()) {
            // Several different video files read off a page (the video, an ad, a preview...) each
            // show their own cover and length; one video gets a single card above its qualities.
            val severalVideos = remember(availableFormats) { availableFormats.distinctPageVideos() > 1 }
            val video = availableFormats.firstOrNull { !it.isImage && !it.isAudioOnly }
            if (!severalVideos && video != null &&
                (video.previewUrl.isNotBlank() || video.durationSec > 0 || pageTitle.isNotBlank())
            ) {
                MediaSummaryCard(cover = video.previewUrl, durationSec = video.durationSec, title = pageTitle)
                Spacer(modifier = Modifier.height(12.dp))
            }
            // A video with several voices (its own, translations): which one to save.
            AudioVoicePicker(voices = audioVoices, selected = audioLanguage, onSelect = viewModel::selectAudioLanguage)
            if (audioVoices.isNotEmpty()) Spacer(modifier = Modifier.height(12.dp))
            SubtitlePicker(captions = captions, selected = subtitleLanguage, onSelect = viewModel::selectSubtitles)
            if (captions.isNotEmpty()) Spacer(modifier = Modifier.height(12.dp))
            // Instagram with "Audio languages" on: what was found, to send when a voice is missing.
            if (mediaInfo.isNotBlank()) {
                PillButton(
                    text = stringResource(R.string.copy_media_info),
                    onClick = {
                        ErrorDetails.copy(context, mediaInfo)
                        Toast.makeText(context, R.string.media_info_copied, Toast.LENGTH_SHORT).show()
                    },
                    style = PillStyle.Ghost,
                    small = true
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            FormatSelector(
                formats = availableFormats,
                selectedFormat = selectedFormat,
                onSelect = viewModel::selectFormat,
                showCovers = severalVideos
            )

            Spacer(modifier = Modifier.height(12.dp))
            if (!multiItem && clipFormat != null) {
                ClipPicker(
                    on = clipOn, from = clipFrom, to = clipTo, valid = clipValid,
                    onToggle = viewModel::setClipOn, onFrom = viewModel::setClipFrom, onTo = viewModel::setClipTo
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Preview the image before downloading (when an image format is selected).
        val previewFormat = selectedFormat
        if (previewFormat != null && previewFormat.isImage) {
            val previewModel: Any? = when {
                previewFormat.imageUrl.isNotBlank() -> previewFormat.imageUrl
                else -> null
            }
            if (previewModel != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = previewModel,
                        contentDescription = stringResource(R.string.preview_image_desc),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.preview_label),
                    color = AccentText.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontFamily = AppFont,
                    letterSpacing = tracked(3f)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        (downloadState as? DownloadState.Error)?.let { error ->
            DeckError(error.message, detail = error.detail, link = url)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (url.isBlank() && availableFormats.isEmpty() && !isLoadingFormats) {
            DeckEmptyState()
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(Modifier.onGloballyPositioned { downloadsTop = it.positionInParent().y.roundToInt() }) {
            DownloadQueuePanel(
                tasks = tasks,
                onStop = viewModel::cancelTask,
                onRetry = viewModel::retryTask,
                onOpen = viewModel::openTask,
                onDismiss = viewModel::dismissTask,
                onClearFinished = viewModel::clearFinishedTasks,
                onPauseAll = viewModel::pauseAllTasks,
                onResumeAll = viewModel::resumeAllTasks,
                onUseMobileData = viewModel::useMobileData
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // The Download button stays in reach under the scrolling content.
    if (!multiItem && url.isNotBlank()) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(DarkGray)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            val size = selectedFormat?.filesize?.let(::formatFileSize).orEmpty()
            RecordButton(
                enabled = canDownload,
                label = when {
                    clipping && !clipValid ->
                        stringResource(R.string.clip_invalid, ClipRange.format(clipFormat?.durationSec ?: 0))
                    clipping -> stringResource(R.string.btn_download_clip)
                    size.isNotEmpty() -> stringResource(R.string.btn_download_size, size)
                    else -> stringResource(R.string.btn_record)
                },
                onRecord = { ensureNotificationPermission { viewModel.startDownload() } }
            )
        }
    }
    }
}

/** The app mark and name; the screen's only header. */
@Composable
private fun DeckHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(40.dp)) {
        AppMark(28.dp)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = White)
    }
}

/** Nothing pasted yet: what to do, in one line. */
@Composable
private fun DeckEmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppMark(72.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.deck_empty_title), style = MaterialTheme.typography.titleLarge, color = White)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.deck_empty_hint), color = TextMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FormatSelector(
    formats: List<FormatInfo>,
    selectedFormat: FormatInfo?,
    onSelect: (FormatInfo) -> Unit,
    showCovers: Boolean = false
) {
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.select_quality))
        Spacer(modifier = Modifier.height(8.dp))
        val grouped = formats
            .filter { it.resolution != "unknown" }
            .distinctBy { "${it.id}|${it.resolution}|${it.directUrl}|${it.imageUrl}" }
        val rowHeight = if (showCovers) 64 else 52
        // Tall enough for ~6 rows without an inner scroll; longer lists scroll inside.
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height((grouped.size.coerceIn(1, 6) * (rowHeight + 8)).dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(grouped) { format ->
                // Page images have no format id, so the label is part of the identity.
                val isSelected = selectedFormat?.id == format.id && selectedFormat.resolution == format.resolution
                val shape = RoundedCornerShape(16.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight.dp)
                        .clip(shape)
                        .background(if (isSelected) AccentSoft else Black)
                        .then(if (isSelected) Modifier.border(1.5.dp, Amber, shape) else Modifier)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(format) })
                        .padding(horizontal = if (showCovers) 8.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showCovers && !format.isImage && !format.isAudioOnly) {
                        MediaThumb(
                            cover = format.previewUrl,
                            durationSec = format.durationSec,
                            isVideo = true,
                            modifier = Modifier.width(78.dp).aspectRatio(16f / 9f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    } else {
                        Icon(
                            if (isSelected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) AccentText else TextFaint,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = format.resolution,
                        color = White,
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = format.extension.uppercase(),
                        color = TextFaint,
                        fontSize = 12.sp,
                        fontFamily = NumberFont
                    )
                    val size = formatFileSize(format.filesize)
                    if (size.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = size, color = TextMuted, fontSize = 13.sp, fontFamily = NumberFont)
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    return when {
        bytes > 1_000_000_000 -> "${"%.1f".format(bytes / 1_000_000_000f)}GB"
        bytes > 1_000_000 -> "${"%.0f".format(bytes / 1_000_000f)}MB"
        else -> "${"%.0f".format(bytes / 1000f)}KB"
    }
}

/** Loose link heuristic for clipboard auto-detect: http(s) URL or a known platform domain. */
private fun String.isLinkLike(): Boolean {
    val c = lowercase()
    return c.startsWith("http://") || c.startsWith("https://") || Magnet.isMagnet(c) ||
        Constants.SUPPORTED_DOMAINS.any { c.contains(it) }
}

/** "Only a part": saves the stretch between two times instead of the whole video. */
@Composable
private fun ClipPicker(
    on: Boolean,
    from: String,
    to: String,
    valid: Boolean,
    onToggle: (Boolean) -> Unit,
    onFrom: (String) -> Unit,
    onTo: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppChip(
            label = stringResource(R.string.clip_part),
            selected = on,
            onClick = { onToggle(!on) },
            icon = Icons.Filled.ContentCut
        )
        if (on) {
            Spacer(Modifier.width(10.dp))
            ClipTime(from, stringResource(R.string.clip_from), !valid, onFrom, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            ClipTime(to, stringResource(R.string.clip_to), !valid, onTo, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ClipTime(value: String, label: String, error: Boolean, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(MediumGray)
            .border(1.5.dp, if (error) StatusRed else Color.Transparent, CircleShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextFaint, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            // The number pad has a "." where a time wants ":".
            onValueChange = { typed ->
                val text = typed.replace('.', ':')
                if (text.length <= 8 && text.all { it.isDigit() || it == ':' }) onChange(text)
            },
            singleLine = true,
            textStyle = TextStyle(color = White, fontSize = 15.sp, fontFamily = NumberFont),
            cursorBrush = SolidColor(Amber),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
    }
}
