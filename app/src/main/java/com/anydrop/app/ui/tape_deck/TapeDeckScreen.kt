package com.anydrop.app.ui.tape_deck

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.anydrop.app.ui.components.DuplicatePromptDialog
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
import com.anydrop.app.ui.theme.tracked

@Composable
fun TapeDeckScreen(
    viewModel: TapeDeckViewModel = hiltViewModel()
) {
    val url by viewModel.url.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val isCookieImported by viewModel.isCookieImported.collectAsState()
    val availableFormats by viewModel.availableFormats.collectAsState()
    val selectedFormat by viewModel.selectedFormat.collectAsState()
    val isLoadingFormats by viewModel.isLoadingFormats.collectAsState()
    val duplicatePrompt by viewModel.duplicatePrompt.collectAsState()
    val autoDetectLinks by viewModel.autoDetectLinks.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val context = LocalContext.current

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
                cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGray)
            .verticalScroll(rememberScrollState())
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
                    stringResource(R.string.fetching_qualities),
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

        // Multi-image picker: when the URL is a carousel of photos only, show a
        // checkable preview grid so the user can pick one, several, or all images.
        // (Mixed carousels with videos use the regular list, which shows every item.)
        val imageEntries = availableFormats.filter { it.isImage && it.imageUrl.isNotBlank() }
        val multiImage = imageEntries.size > 1 && availableFormats.all { it.isImage }
        if (multiImage) {
            MultiImagePicker(
                images = imageEntries,
                onDownloadSelected = { chosen -> ensureNotificationPermission { viewModel.downloadImages(chosen) } }
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else if (availableFormats.isNotEmpty()) {
            FormatSelector(
                formats = availableFormats,
                selectedFormat = selectedFormat,
                onSelect = viewModel::selectFormat
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
            DeckError(error.message)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (!multiImage) {
            RecordButton(
                enabled = canDownload,
                onRecord = { ensureNotificationPermission { viewModel.startDownload() } }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        DownloadQueuePanel(
            tasks = tasks,
            onStop = viewModel::cancelTask,
            onRetry = viewModel::retryTask,
            onOpen = viewModel::openTask,
            onDismiss = viewModel::dismissTask,
            onClearFinished = viewModel::clearFinishedTasks
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MultiImagePicker(
    images: List<com.anydrop.app.engine.YtDlpEngine.FormatInfo>,
    onDownloadSelected: (List<com.anydrop.app.engine.YtDlpEngine.FormatInfo>) -> Unit
) {
    val selected = remember { mutableStateListOf<Int>() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.deck_images_tap_select, images.size),
                color = Amber,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = tracked(1f)
            )
            Text(
                stringResource(
                    if (selected.size == images.size) R.string.clear_all else R.string.select_all
                ),
                color = Amber,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.clickable {
                    if (selected.size == images.size) selected.clear()
                    else { selected.clear(); selected.addAll(images.indices) }
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        // Fixed height: a LazyVerticalGrid inside a vertically-scrolling Column must
        // NOT use heightIn (infinite-height crash). Height scales with row count.
        val rows = (images.size + 2) / 3
        val gridHeight = (rows.coerceAtMost(4) * 116).dp
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().height(gridHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(images) { index, img ->
                val isSel = selected.contains(index)
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Black)
                        .then(if (isSel) Modifier.border(2.dp, Amber, RoundedCornerShape(6.dp)) else Modifier)
                        .clickable {
                            if (isSel) selected.remove(index) else selected.add(index)
                        }
                ) {
                    AsyncImage(
                        model = img.imageUrl,
                        contentDescription = stringResource(R.string.share_image_number, index + 1),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isSel) {
                        Text(
                            "✓",
                            color = OnAccent,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Amber)
                                .padding(horizontal = 5.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.Button(
            onClick = { onDownloadSelected(selected.sorted().map { idx -> images[idx] }) },
            enabled = selected.isNotEmpty(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Black),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.download_selected_count, selected.size),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun FormatSelector(
    formats: List<FormatInfo>,
    selectedFormat: FormatInfo?,
    onSelect: (FormatInfo) -> Unit
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
            .groupBy { it.resolution }
            .map { (_, list) -> list.first() }
        // Tall enough for ~6 rows without an inner scroll; longer lists scroll inside.
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height((grouped.size.coerceIn(1, 6) * 44).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(grouped) { format ->
                // Page images have no format id, so the label is part of the identity.
                val isSelected = selectedFormat?.id == format.id && selectedFormat.resolution == format.resolution
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Amber.copy(alpha = 0.16f) else Black)
                        .border(1.dp, if (isSelected) Amber else Hairline, RoundedCornerShape(8.dp))
                        .clickable { onSelect(format) }
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            format.isImage -> "▣ ${format.resolution}"
                            format.isAudioOnly -> "♪ ${format.resolution}"
                            else -> "● ${format.resolution}"
                        },
                        color = if (isSelected) Amber else White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else null,
                        fontFamily = FontFamily.Monospace
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
    return c.startsWith("http://") || c.startsWith("https://") ||
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
