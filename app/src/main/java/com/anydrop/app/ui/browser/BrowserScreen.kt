package com.anydrop.app.ui.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.anydrop.app.R
import com.anydrop.app.data.repository.DownloadQueue
import com.anydrop.app.data.repository.DownloadRequest
import com.anydrop.app.data.repository.LibraryRepository
import com.anydrop.app.engine.MediaCandidate
import com.anydrop.app.engine.MediaSniffer
import com.anydrop.app.engine.PageScan
import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import com.anydrop.app.engine.toFormatInfos
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkGray
import com.anydrop.app.ui.theme.Hairline
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.OnAccent
import com.anydrop.app.ui.theme.White
import com.anydrop.app.ui.theme.tracked
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.util.Collections
import javax.inject.Inject

/** Sites on the browser's start page. */
private data class Site(val name: String, val url: String, val argb: Long)

private val SITES = listOf(
    Site("Instagram", "https://www.instagram.com/", 0xFFE1306C),
    Site("TikTok", "https://www.tiktok.com/", 0xFF25F4EE),
    Site("YouTube", "https://m.youtube.com/", 0xFFFF3B30),
    Site("Facebook", "https://m.facebook.com/watch/", 0xFF1877F2),
    Site("X", "https://x.com/", 0xFF9E9E9E),
    Site("Reddit", "https://www.reddit.com/", 0xFFFF4500),
    Site("Vimeo", "https://vimeo.com/", 0xFF1AB7EA),
    Site("Pinterest", "https://www.pinterest.com/", 0xFFE60023),
    Site("Dailymotion", "https://www.dailymotion.com/", 0xFF0A7CFF),
    Site("Twitch", "https://m.twitch.tv/", 0xFF9146FF),
    Site("Threads", "https://www.threads.net/", 0xFFBDBDBD),
    Site("Snapchat", "https://www.snapchat.com/spotlight", 0xFFFFD600),
)

/**
 * The in-app browser (Snaptube-style): browse any site; every page is watched for media
 * with the same sniffer the deck's no-login extractor uses. Held above the nav graph so
 * switching tabs keeps the page and its history.
 */
@Stable
class BrowserState(private val appContext: Context) {

    var webView: WebView? = null
        private set
    var currentUrl by mutableStateOf<String?>(null)
        private set
    var pageTitle by mutableStateOf("")
        private set
    var progress by mutableIntStateOf(0)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set
    var desktopMode by mutableStateOf(false)
        private set
    var scan by mutableStateOf<PageScan?>(null)
        private set

    /** Called when a page starts a file download itself (e.g. a "download video" link). */
    var onPageDownload: ((url: String, mime: String?, contentDisposition: String?) -> Unit)? = null

    private val network = Collections.synchronizedList(mutableListOf<MediaCandidate>())
    private var pendingUrl: String? = null
    private var clearHistoryOnBlank = false

    val showingStart: Boolean get() = currentUrl == null
    val foundCount: Int get() = scan?.let { it.videos.size + it.images.size } ?: 0

    val userAgent: String
        get() = webView?.settings?.userAgentString ?: MediaSniffer.mobileUserAgent(appContext)

    fun obtainWebView(context: Context): WebView {
        webView?.let { existing ->
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        val wv = WebView(context)
        MediaSniffer.configure(wv, MediaSniffer.mobileUserAgent(context))
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                MediaSniffer.classifyNetworkUrl(request.url.toString())?.let { network.add(it) }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return when (uri.scheme) {
                    "http", "https" -> false
                    "intent" -> {
                        // Sites try to bounce you into their app; stay here and follow the web fallback.
                        runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()
                            ?.getStringExtra("browser_fallback_url")?.let { view.loadUrl(it) }
                        true
                    }
                    else -> true
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url == null || url == "about:blank") return
                if (url != currentUrl) resetFindings()
                currentUrl = url
                isLoading = true
                if (!MediaSniffer.documentStartSupported) view.evaluateJavascript(MediaSniffer.CORE_JS, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                isLoading = false
                if (url == "about:blank" && clearHistoryOnBlank) {
                    clearHistoryOnBlank = false
                    view.clearHistory()
                }
                updateNav(view)
                rescan()
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                if (url != null && url != "about:blank" && currentUrl != null) currentUrl = url
                updateNav(view)
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress = newProgress
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                pageTitle = title.orEmpty()
            }
        }
        wv.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            onPageDownload?.invoke(url, mimeType, contentDisposition)
        }
        webView = wv
        pendingUrl?.let {
            pendingUrl = null
            wv.loadUrl(it)
        }
        return wv
    }

    private fun resetFindings() {
        network.clear()
        scan = null
    }

    private fun updateNav(view: WebView) {
        canGoBack = view.canGoBack() && currentUrl != null
        canGoForward = view.canGoForward()
    }

    fun load(input: String) {
        val url = toUrl(input)
        resetFindings()
        currentUrl = url
        val wv = webView
        if (wv == null) pendingUrl = url else wv.loadUrl(url)
    }

    fun goBack() = webView?.goBack()
    fun goForward() = webView?.goForward()
    fun reload() = webView?.reload()
    fun stop() = webView?.stopLoading()

    fun goHome() {
        currentUrl = null
        resetFindings()
        isLoading = false
        clearHistoryOnBlank = true
        webView?.let {
            it.stopLoading()
            it.loadUrl("about:blank")
        }
        canGoBack = false
        canGoForward = false
    }

    fun toggleDesktop() {
        desktopMode = !desktopMode
        webView?.let {
            it.settings.userAgentString =
                if (desktopMode) MediaSniffer.desktopUserAgent(appContext) else MediaSniffer.mobileUserAgent(appContext)
            it.reload()
        }
    }

    fun pause() = webView?.onPause()
    fun resume() = webView?.onResume()

    /** Reads the page for media. Cheap enough to run every couple of seconds. */
    fun rescan() {
        val wv = webView ?: return
        val pageUrl = currentUrl ?: return
        wv.evaluateJavascript(MediaSniffer.COLLECT_JS) { raw ->
            if (currentUrl == null) return@evaluateJavascript
            val snapshot = synchronized(network) { network.toList() }
            scan = MediaSniffer.parse(raw, snapshot, pageUrl, userAgent)
        }
    }

    companion object {
        fun toUrl(input: String): String {
            val t = input.trim()
            Regex("""https?://\S+""").find(t)?.value?.let { return it }
            if (!t.contains(' ') && Regex("""^[\w-]+(\.[\w-]+)+(/.*)?$""").matches(t)) return "https://$t"
            return "https://www.google.com/search?q=" + URLEncoder.encode(t, "UTF-8")
        }
    }
}

@Composable
fun rememberBrowserState(): BrowserState {
    val context = LocalContext.current
    return remember { BrowserState(context.applicationContext) }
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val downloadQueue: DownloadQueue,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    /** Queues media found on the page. The browser's session cookies go along with it. */
    fun download(pageUrl: String, title: String, format: FormatInfo) {
        val request = DownloadRequest(
            url = pageUrl,
            format = format,
            title = title,
            webpageUrl = pageUrl,
            cookieHeader = format.directUrl.takeIf { it.isNotBlank() }?.let(MediaSniffer::cookieHeaderFor)
        )
        val path = when {
            format.isImage -> libraryRepository.getImageOutputPath(pageUrl, title, "image")
            format.isAudioOnly -> libraryRepository.getAudioOutputPath(pageUrl, title, "audio")
            else -> {
                val quality = format.resolution.ifBlank { "video" }
                val copies = libraryRepository.findVideoCopies(pageUrl, title, quality)
                if (copies.isEmpty()) libraryRepository.getOutputPath(pageUrl, title, quality)
                else libraryRepository.getSuffixedVideoOutputPath(pageUrl, title, quality, copies)
            }
        }
        downloadQueue.launch(request, path)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    state: BrowserState,
    onAnalyze: (String) -> Unit,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var sheetOpen by remember { mutableStateOf(false) }

    // Pages that hand over a file directly (a "Download" link to an .mp4, say).
    state.onPageDownload = { url, mime, disposition ->
        val isMedia = mime?.startsWith("video/") == true || mime?.startsWith("audio/") == true ||
            MediaSniffer.classifyNetworkUrl(url) != null
        if (isMedia) {
            val name = URLUtil.guessFileName(url, disposition, mime).substringBeforeLast('.')
            val audio = mime?.startsWith("audio/") == true
            val format = FormatInfo(
                id = "web:file",
                extension = if (audio) "m4a" else "mp4",
                resolution = "Original",
                filesize = 0L,
                vcodec = if (audio) "none" else "page",
                acodec = "page",
                isAudioOnly = audio,
                directUrl = url,
                userAgent = state.userAgent
            )
            viewModel.download(state.currentUrl ?: url, name, format)
            Toast.makeText(context, R.string.browser_added, Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        state.resume()
        onDispose { state.pause() }
    }

    // System back walks the page history before leaving the tab.
    BackHandler(enabled = state.canGoBack && !state.showingStart) { state.goBack() }

    // Keep looking while the page plays: many sites only fetch the video when it starts.
    LaunchedEffect(state.currentUrl) {
        while (state.currentUrl != null) {
            delay(1500)
            state.rescan()
        }
    }

    Column(Modifier.fillMaxSize().background(DarkGray)) {
        BrowserBar(state)
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (state.isLoading) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxSize(),
                    color = Amber,
                    trackColor = Color.Transparent
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        addView(
                            state.obtainWebView(ctx),
                            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (state.showingStart) {
                StartPage(onOpen = state::load)
            } else {
                DownloadFab(
                    count = state.foundCount,
                    onClick = {
                        state.rescan()
                        sheetOpen = true
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                )
            }
        }
    }

    if (sheetOpen) {
        val scan = state.scan
        val formats = remember(scan) { scan?.toFormatInfos().orEmpty() }
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = DarkGray
        ) {
            MediaSheet(
                title = scan?.title?.takeIf { it.isNotBlank() } ?: state.pageTitle,
                formats = formats,
                onSave = { format ->
                    val pageUrl = scan?.pageUrl ?: state.currentUrl.orEmpty()
                    viewModel.download(pageUrl, scan?.title?.ifBlank { null } ?: state.pageTitle, format)
                    Toast.makeText(context, R.string.browser_added, Toast.LENGTH_SHORT).show()
                    sheetOpen = false
                },
                onAnalyze = {
                    sheetOpen = false
                    state.currentUrl?.let(onAnalyze)
                }
            )
        }
    }
}

@Composable
private fun BrowserBar(state: BrowserState) {
    val focus = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var menu by remember { mutableStateOf(false) }
    val display = state.currentUrl?.removePrefix("https://")?.removePrefix("http://")?.removePrefix("www.")?.trimEnd('/').orEmpty()

    Row(
        Modifier.fillMaxWidth().background(Black).padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.canGoBack && !state.showingStart) {
            IconButton(onClick = { state.goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_desc), tint = White) }
        } else {
            IconButton(onClick = { state.goHome() }, enabled = !state.showingStart) {
                Icon(Icons.Filled.Home, stringResource(R.string.browser_start_page), tint = if (state.showingStart) White.copy(alpha = 0.3f) else White)
            }
        }
        Row(
            Modifier
                .weight(1f)
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkGray)
                .border(1.dp, if (editing) Amber else Hairline, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (!editing && state.currentUrl?.startsWith("https://") == true) Icons.Filled.Lock else Icons.Filled.Search,
                null,
                Modifier.size(15.dp),
                tint = White.copy(alpha = 0.5f)
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = if (editing) field else TextFieldValue(display),
                    onValueChange = { if (editing) field = it },
                    singleLine = true,
                    textStyle = TextStyle(color = White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
                    cursorBrush = SolidColor(Amber),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        if (field.text.isNotBlank()) state.load(field.text)
                        focus.clearFocus()
                    }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { f ->
                        if (f.isFocused && !editing) {
                            val full = state.currentUrl.orEmpty()
                            field = TextFieldValue(full, TextRange(0, full.length))
                        }
                        editing = f.isFocused
                    }
                )
                if (!editing && display.isEmpty()) {
                    Text(stringResource(R.string.browser_search_hint), color = White.copy(alpha = 0.4f), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
            }
            if (editing && field.text.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close, null,
                    Modifier.size(18.dp).clip(CircleShape).clickable { field = TextFieldValue("") },
                    tint = White.copy(alpha = 0.6f)
                )
            }
        }
        if (state.isLoading) {
            IconButton(onClick = { state.stop() }) { Icon(Icons.Filled.Close, stringResource(R.string.browser_stop), tint = White) }
        } else {
            IconButton(onClick = { state.reload() }, enabled = !state.showingStart) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.browser_reload), tint = if (state.showingStart) White.copy(alpha = 0.3f) else White)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.menu_desc), tint = White) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.browser_forward)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                    enabled = state.canGoForward,
                    onClick = { menu = false; state.goForward() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.browser_desktop_site)) },
                    leadingIcon = { Icon(Icons.Filled.DesktopWindows, null) },
                    trailingIcon = {
                        Checkbox(
                            checked = state.desktopMode, onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = Amber, checkmarkColor = OnAccent)
                        )
                    },
                    onClick = { menu = false; state.toggleDesktop() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.browser_copy_link)) },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                    enabled = !state.showingStart,
                    onClick = { menu = false; state.currentUrl?.let { clipboard.setText(AnnotatedString(it)) } }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.browser_start_page)) },
                    leadingIcon = { Icon(Icons.Filled.Home, null) },
                    onClick = { menu = false; state.goHome() }
                )
            }
        }
    }
}

@Composable
private fun StartPage(onOpen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(DarkGray)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.brand_wordmark),
            style = MaterialTheme.typography.displayLarge,
            color = Amber
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.browser_title),
            color = White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = tracked(2f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.browser_subtitle),
            color = White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Black)
                .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                .padding(vertical = 14.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SITES.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { site ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpen(site.url) }
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val color = Color(site.argb)
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = 0.16f))
                                    .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(site.name.take(1), color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(site.name, color = White.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DownloadFab(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(count > 0) {
        if (count > 0) {
            scale.animateTo(1.12f, tween(140))
            scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val found = count > 0
    Row(
        modifier
            .scale(scale.value)
            .clip(RoundedCornerShape(16.dp))
            .background(if (found) Amber else Black)
            .border(1.dp, if (found) Amber else MediumGray, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Download, null, tint = if (found) OnAccent else Amber, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (found) stringResource(R.string.browser_download_count, count) else stringResource(R.string.browser_find_video),
            color = if (found) OnAccent else White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = tracked(1f)
        )
    }
}

@Composable
private fun MediaSheet(
    title: String,
    formats: List<FormatInfo>,
    onSave: (FormatInfo) -> Unit,
    onAnalyze: () -> Unit
) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
        Text(
            stringResource(R.string.browser_sheet_title),
            color = Amber,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = tracked(2f)
        )
        if (title.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(title, color = White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(14.dp))

        if (formats.isEmpty()) {
            Text(
                stringResource(R.string.browser_nothing_found),
                color = White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(16.dp))
            SheetButton(stringResource(R.string.browser_analyze), filled = true, onClick = onAnalyze)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                formats.forEach { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Black)
                            .border(1.dp, Hairline, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KindIcon(
                            when {
                                f.isImage -> Icons.Filled.Image
                                f.isAudioOnly -> Icons.Filled.MusicNote
                                else -> Icons.Filled.Videocam
                            }
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.resolution, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text(f.extension.uppercase(), color = White.copy(alpha = 0.5f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Text(
                            stringResource(R.string.browser_save),
                            color = OnAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Amber)
                                .clickable { onSave(f) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            SheetButton(stringResource(R.string.browser_more_qualities), filled = false, onClick = onAnalyze)
        }
    }
}

@Composable
private fun KindIcon(icon: ImageVector) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Amber.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Amber, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SheetButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) Amber else Color.Transparent)
            .border(1.dp, Amber, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (filled) OnAccent else Amber,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = tracked(1f)
        )
    }
}
