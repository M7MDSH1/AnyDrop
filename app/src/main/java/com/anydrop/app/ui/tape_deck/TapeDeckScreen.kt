package com.anydrop.app.ui.tape_deck

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
import com.anydrop.app.ui.components.DuplicatePromptDialog
import com.anydrop.app.ui.components.ErrorDetails
import com.anydrop.app.ui.tape_deck.components.LinkField
import com.anydrop.app.ui.tape_deck.components.RecordButton
import com.anydrop.app.ui.tape_deck.components.DeckError
import com.anydrop.app.ui.tape_deck.components.DeckPanel
import com.anydrop.app.ui.tape_deck.components.DownloadQueuePanel
import com.anydrop.app.ui.theme.StatusGreen
import com.anydrop.app.ui.tape_deck.components.LEDIndicator
import com.anydrop.app.ui.tape_deck.components.ReelAnimation
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
    val selectedFormat by viewModel.selectedFormat.collectAsState()
    val isLoadingFormats by viewModel.isLoadingFormats.collectAsState()
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

    // The reels turn while anything is downloading; several downloads can run at once.
    val isSpinning = tasks.any { it.isActive }
    val canDownload = selectedFormat != null && url.isNotBlank()
    val level = tasks.filter { it.isActive }.maxOfOrNull { it.percent }?.div(100f) ?: 0f

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGray)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.brand_wordmark),
            style = MaterialTheme.typography.displayLarge,
            color = Amber,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.tape_deck_version),
            color = White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = tracked(4f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        DeckPanel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LEDIndicator(label = stringResource(R.string.led_power), isOn = true, onColor = LedGreen)
                LEDIndicator(label = stringResource(R.string.led_rec), isOn = isSpinning, onColor = LedRed, blink = isSpinning)
                LEDIndicator(label = stringResource(R.string.led_signal), isOn = isCookieImported, onColor = LedBlue)
                LEDIndicator(label = stringResource(R.string.led_link), isOn = url.isNotBlank(), onColor = Amber)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ReelAnimation(size = 96.dp, isSpinning = isSpinning, reelColor = Amber, trackColor = MediumGray)
                ReelAnimation(size = 96.dp, isSpinning = isSpinning, reelColor = Amber, trackColor = MediumGray)
            }

            Spacer(modifier = Modifier.height(18.dp))

            VUMeter(level = level)
        }

        Spacer(modifier = Modifier.height(16.dp))

        detectedLink?.let { link ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Black)
                    .border(1.dp, LedGreen.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.link_detected),
                        color = StatusGreen,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = tracked(2f)
                    )
                    Text(
                        text = link,
                        color = White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = {
                    viewModel.onUrlChange(link)
                    detectedLink = null
                }) {
                    Text(stringResource(R.string.load_link), color = Amber, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                TextButton(onClick = { detectedLink = null }) {
                    Text("✕", color = White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
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

        // A torrent has no page to read: the video/page switch only shows for web links.
        val isTorrent = remember(url) { Magnet.isTorrentLink(url) }
        if (!isTorrent) {
            Spacer(modifier = Modifier.height(8.dp))
            FetchModeToggle(wholePage = fetchWholePage, onChange = viewModel::setFetchWholePage)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoadingFormats) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = Amber,
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
                    fontFamily = FontFamily.Monospace
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
                fontFamily = FontFamily.Monospace,
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
        val pageItems = remember(availableFormats) { if (availableFormats.isWholePage) availableFormats.pageItems() else emptyList() }
        val postItems = remember(availableFormats) { if (availableFormats.isWholePage) emptyList() else availableFormats.postItems() }
        val torrentFiles = remember(availableFormats) { availableFormats.torrentFileList() }
        val multiItem = postItems.isNotEmpty() || pageItems.isNotEmpty() || torrentFiles.isNotEmpty()
        if (torrentFiles.isNotEmpty()) {
            TorrentPicker(
                name = pageTitle,
                files = torrentFiles,
                onDownload = { chosen -> ensureNotificationPermission { viewModel.downloadTorrent(chosen) } }
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else if (pageItems.isNotEmpty()) {
            PageMediaPicker(
                items = pageItems,
                pageTitle = pageTitle,
                pageUrl = url,
                resolving = resolving,
                onResolve = viewModel::resolvePageItem,
                onDownload = { chosen -> ensureNotificationPermission { viewModel.downloadPageItems(chosen) } },
                playlist = availableFormats.isPlaylist,
                onDownloadAudio = { chosen -> ensureNotificationPermission { viewModel.downloadPageItems(chosen, audioOnly = true) } }
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
            FormatSelector(
                formats = availableFormats,
                selectedFormat = selectedFormat,
                onSelect = viewModel::selectFormat,
                showCovers = severalVideos
            )

            Spacer(modifier = Modifier.height(12.dp))
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
                    color = Amber.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = tracked(3f)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        (downloadState as? DownloadState.Error)?.let { error ->
            DeckError(error.message, detail = error.detail, link = url)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (!multiItem) {
            RecordButton(
                enabled = canDownload,
                onRecord = { ensureNotificationPermission { viewModel.startDownload() } }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(Modifier.onGloballyPositioned { downloadsTop = it.positionInParent().y.roundToInt() }) {
            DownloadQueuePanel(
                tasks = tasks,
                onStop = viewModel::cancelTask,
                onRetry = viewModel::retryTask,
                onOpen = viewModel::openTask,
                onDismiss = viewModel::dismissTask,
                onClearFinished = viewModel::clearFinishedTasks
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun FormatSelector(
    formats: List<FormatInfo>,
    selectedFormat: FormatInfo?,
    onSelect: (FormatInfo) -> Unit,
    showCovers: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.select_quality),
            color = White.copy(alpha = 0.4f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = tracked(2f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        val grouped = formats
            .filter { it.resolution != "unknown" }
            .distinctBy { "${it.id}|${it.resolution}|${it.directUrl}|${it.imageUrl}" }
        val rowHeight = if (showCovers) 56 else 40
        // Tall enough for ~6 rows without an inner scroll; longer lists scroll inside.
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height((grouped.size.coerceIn(1, 6) * (rowHeight + 4)).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(grouped) { format ->
                // Page images have no format id, so the label is part of the identity.
                val isSelected = selectedFormat?.id == format.id && selectedFormat.resolution == format.resolution
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Amber.copy(alpha = 0.16f) else Black)
                        .border(1.dp, if (isSelected) Amber else Hairline, RoundedCornerShape(8.dp))
                        .clickable { onSelect(format) }
                        .padding(horizontal = if (showCovers) 6.dp else 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
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
                    }
                    Text(
                        text = when {
                            format.isImage -> "▣ ${format.resolution}"
                            format.isAudioOnly -> "♪ ${format.resolution}"
                            else -> "● ${format.resolution}"
                        },
                        color = if (isSelected) Amber else White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else null,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = format.extension.uppercase(),
                            color = White.copy(alpha = 0.4f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = formatFileSize(format.filesize),
                            color = White.copy(alpha = 0.4f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
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

@Composable
private fun VUMeter(modifier: Modifier = Modifier, level: Float = 0f) {
    val clampedLevel = level.coerceIn(0f, 1f)
    val segments = 12

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (i in 0 until segments) {
                val threshold = (i + 1).toFloat() / segments
                val color = when {
                    i < segments * 0.6f -> VuGreen
                    i < segments * 0.85f -> VuYellow
                    else -> VuRed
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(if (clampedLevel >= threshold) color else MediumGray)
                )
            }
        }
        Text(
            text = stringResource(R.string.vu_meter),
            color = White.copy(alpha = 0.3f),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = tracked(2f)
        )
    }
}
