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
import com.anydrop.app.engine.distinctPageVideos
import com.anydrop.app.engine.isWholePage
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
    val fetchWholePage by viewModel.fetchWholePage.collectAsState()
    val pageTitle by viewModel.pageTitle.collectAsState()
    val resolving by viewModel.resolving.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

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
        val pageItems = remember(formats) { if (formats.isWholePage) formats.pageItems() else emptyList() }
        val postItems = remember(formats) { if (formats.isWholePage) emptyList() else formats.postItems() }

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
            Spacer(Modifier.height(12.dp))
            FetchModeToggle(wholePage = fetchWholePage, onChange = viewModel::setFetchWholePage)
            if (formats.any { it.isFromPage } && !formats.isWholePage) {
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
                                stringResource(if (fetchWholePage) R.string.fetching_whole_page else R.string.share_fetching),
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

                // Whole page: everything on it, to pick from.
                pageItems.isNotEmpty() -> {
                    PageMediaPicker(
                        items = pageItems,
                        pageTitle = pageTitle,
                        pageUrl = sharedUrl,
                        resolving = resolving,
                        onResolve = viewModel::resolvePageItem,
                        onDownload = { chosen -> ensureNotificationPermission { viewModel.downloadPageItems(chosen) } },
                        maxListHeight = 380.dp
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
                        .padding(horizontal = if (showCovers) 8.dp else 16.dp, vertical = if (showCovers) 8.dp else 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
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
                    }
                    Text(
                        "$icon  ${fmt.resolution}",
                        color = if (isSel) Amber else White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
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
    if (id.isNotBlank()) "$id-$directUrl" else "$resolution-$extension-$imageUrl"

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()
