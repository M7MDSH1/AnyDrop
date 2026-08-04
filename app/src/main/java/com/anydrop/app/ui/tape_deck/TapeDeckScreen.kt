package com.anydrop.app.ui.tape_deck

import android.content.ClipboardManager
import android.content.Context
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.anydrop.app.data.model.DownloadState
import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import com.anydrop.app.ui.components.DuplicatePromptDialog
import com.anydrop.app.ui.tape_deck.components.DeckControls
import com.anydrop.app.ui.tape_deck.components.LEDIndicator
import com.anydrop.app.ui.tape_deck.components.ReelAnimation
import com.anydrop.app.util.Constants
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.AmberBright
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkGray
import com.anydrop.app.ui.theme.LedBlue
import com.anydrop.app.ui.theme.LedGreen
import com.anydrop.app.ui.theme.LedRed
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.VuGreen
import com.anydrop.app.ui.theme.VuRed
import com.anydrop.app.ui.theme.VuYellow
import com.anydrop.app.ui.theme.White
import kotlinx.coroutines.delay

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
    val context = LocalContext.current

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
            onReplace = { viewModel.confirmReplaceDownload() },
            onDownloadAnyway = { viewModel.confirmDownloadAnyway() },
            onCancel = { viewModel.dismissDuplicatePrompt() }
        )
    }

    val isSpinning = downloadState is DownloadState.Progress

    val canDownload = selectedFormat != null &&
        downloadState !is DownloadState.Progress && url.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGray)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ANYDROP",
            style = MaterialTheme.typography.displayLarge,
            color = Amber,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Text(
            text = "TAPE DECK v1.0",
            color = White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LEDIndicator(label = "POWER", isOn = true, onColor = LedGreen)
            LEDIndicator(label = "REC", isOn = isSpinning, onColor = LedRed, blink = isSpinning)
            LEDIndicator(label = "SIGNAL", isOn = isCookieImported, onColor = LedBlue)
            LEDIndicator(label = "LINK", isOn = url.isNotBlank(), onColor = Amber)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ReelAnimation(size = 100.dp, isSpinning = isSpinning, reelColor = Amber, trackColor = MediumGray)
            ReelAnimation(size = 100.dp, isSpinning = isSpinning, reelColor = Amber, trackColor = MediumGray)
        }

        Spacer(modifier = Modifier.height(24.dp))

        VUMeter(
            level = when (val state = downloadState) {
                is DownloadState.Progress -> state.percentage / 100f
                else -> 0f
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Multi-image picker: when the URL is a carousel/multi-image post, show a
        // checkable preview grid so the user can pick one, several, or all images.
        val imageEntries = availableFormats.filter { it.isImage && it.imageUrl.isNotBlank() }
        val multiImage = imageEntries.size > 1
        if (multiImage) {
            MultiImagePicker(
                images = imageEntries,
                onDownloadSelected = { chosen -> viewModel.downloadImages(chosen) }
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = previewModel,
                        contentDescription = "Image preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "PREVIEW",
                    color = Amber.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (isLoadingFormats) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    "Fetching available qualities...",
                    color = White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        detectedLink?.let { link ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Black)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LINK DETECTED",
                        color = LedGreen,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
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
                    Text("LOAD", color = Amber, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                TextButton(onClick = { detectedLink = null }) {
                    Text("✕", color = White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        DeckControls(
            url = url,
            downloadState = downloadState,
            downloadEnabled = canDownload,
            onUrlChange = viewModel::onUrlChange,
            onDownload = viewModel::startDownload,
            onCancel = viewModel::cancelDownload,
            onPaste = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.let {
                    viewModel.onUrlChange(it)
                }
            }
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
                "${images.size} IMAGES · tap to select",
                color = Amber,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
            Text(
                if (selected.size == images.size) "CLEAR ALL" else "SELECT ALL",
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
                        contentDescription = "Image ${index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isSel) {
                        Text(
                            "✓",
                            color = Black,
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
                "DOWNLOAD SELECTED (${selected.size})",
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
            text = "SELECT QUALITY",
            color = White.copy(alpha = 0.4f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val grouped = formats
                .filter { it.resolution != "unknown" }
                .groupBy { it.resolution }
                .map { (res, list) -> list.first() }
            items(grouped) { format ->
                val isSelected = selectedFormat?.id == format.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) Amber.copy(alpha = 0.2f) else MediumGray.copy(alpha = 0.4f))
                        .clickable { onSelect(format) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            format.isImage -> "▣ ${format.resolution}"
                            format.isAudioOnly -> "♪ ${format.resolution}"
                            else -> "● ${format.resolution}"
                        },
                        color = if (isSelected) AmberBright else White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
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
            text = "VU METER",
            color = White.copy(alpha = 0.3f),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
    }
}
