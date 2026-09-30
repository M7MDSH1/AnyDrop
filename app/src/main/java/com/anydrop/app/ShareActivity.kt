package com.anydrop.app

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
import kotlinx.coroutines.launch
import com.anydrop.app.ui.theme.tracked

@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

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

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent == null || intent.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return Regex("""https?://[^\s]+""").find(text)?.value
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
    val downloadState by viewModel.downloadState.collectAsState()
    val duplicatePrompt by viewModel.duplicatePrompt.collectAsState()

    // Kick off the fetch in the background as soon as the sheet appears.
    androidx.compose.runtime.LaunchedEffect(sharedUrl) {
        viewModel.handleSharedUrl(sharedUrl)
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
        containerColor = DarkGray,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        val images = formats.filter { it.isImage && it.imageUrl.isNotBlank() }
        val videoAudio = formats.filter { !it.isImage }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header
            Text(
                stringResource(R.string.brand_wordmark),
                color = Amber,
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = tracked(3f)
            )
            Text(
                stringResource(R.string.share_select_what),
                color = White.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            if (formats.any { it.isFromPage }) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.found_on_page),
                    color = StatusGreen,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(StatusGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
            Spacer(Modifier.height(16.dp))

            when {
                isLoading || formats.isEmpty() && downloadState !is DownloadState.Error -> {
                    // Fetching
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Amber, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(14.dp))
                            Text(
                                stringResource(R.string.share_fetching),
                                color = White.copy(alpha = 0.6f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                downloadState is DownloadState.Error -> {
                    Text(
                        (downloadState as DownloadState.Error).message,
                        color = StatusRed,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }

                // A carousel of photos only; mixed photo/video posts use the list below.
                images.size > 1 && videoAudio.isEmpty() -> {
                    ShareImagePicker(images = images) { chosen -> ensureNotificationPermission { viewModel.downloadImages(chosen) } }
                }

                else -> {
                    ShareQualityList(
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
    onRecord: (FormatInfo) -> Unit
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
                val icon = when {
                    fmt.isImage -> "▣"
                    fmt.isAudioOnly -> "♪"
                    else -> "●"
                }
                val isSel = fmt.uniqueKey() == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Black)
                        .then(if (isSel) Modifier.border(2.dp, Amber, RoundedCornerShape(8.dp)) else Modifier)
                        .clickable { selectedId = fmt.uniqueKey() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$icon  ${fmt.resolution}",
                        color = if (isSel) Amber else White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                    Text(
                        fmt.extension.uppercase(),
                        color = Amber.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected == null) MediumGray else Amber)
                .clickable(enabled = selected != null) { selected?.let(onRecord) }
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.share_record),
                color = if (selected == null) White.copy(alpha = 0.4f) else Black,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = tracked(3f)
            )
        }
    }
}

/** Stable key for selection (id if present, else resolution+ext). */
private fun FormatInfo.uniqueKey(): String =
    if (id.isNotBlank()) id else "$resolution-$extension-$imageUrl"

/** Compact multi-image picker with select + a single download button. */
@Composable
private fun ShareImagePicker(
    images: List<FormatInfo>,
    onDownloadSelected: (List<FormatInfo>) -> Unit
) {
    val selected = remember { mutableStateListOf<Int>() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.share_images_count, images.size),
                color = Amber,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
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
        Spacer(Modifier.height(10.dp))

        val rows = (images.size + 2) / 3
        val gridHeight = (rows.coerceAtMost(4) * 116).dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().height(gridHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(images) { index, img ->
                val isSel = selected.contains(index)
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Black)
                        .then(if (isSel) Modifier.border(2.dp, Amber, RoundedCornerShape(8.dp)) else Modifier)
                        .clickable { if (isSel) selected.remove(index) else selected.add(index) }
                ) {
                    AsyncImage(
                        model = img.imageUrl,
                        contentDescription = stringResource(R.string.share_image_number, index + 1),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isSel) {
                        Text(
                            "✓",
                            color = Black,
                            fontSize = 13.sp,
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
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected.isEmpty()) MediumGray else Amber)
                .clickable(enabled = selected.isNotEmpty()) {
                    onDownloadSelected(selected.sorted().map { idx -> images[idx] })
                }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.share_record_count, selected.size),
                color = if (selected.isEmpty()) White.copy(alpha = 0.4f) else Black,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = tracked(2f)
            )
        }
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()
