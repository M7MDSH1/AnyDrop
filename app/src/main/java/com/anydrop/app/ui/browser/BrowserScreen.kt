package com.anydrop.app.ui.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VerticalAlignTop
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.webkit.WebViewCompat
import com.anydrop.app.R
import com.anydrop.app.data.repository.BrowserSite
import com.anydrop.app.data.repository.BrowserSitesRepository
import com.anydrop.app.data.repository.BrowserHistoryRepository
import com.anydrop.app.data.repository.DownloadRepository
import com.anydrop.app.data.repository.HistoryEntry
import com.anydrop.app.data.repository.HistoryList
import com.anydrop.app.data.repository.DownloadQueue
import com.anydrop.app.data.repository.DownloadRequest
import com.anydrop.app.data.repository.LibraryRepository
import com.anydrop.app.engine.MediaCandidate
import com.anydrop.app.engine.MediaProbe
import com.anydrop.app.engine.MediaSniffer
import com.anydrop.app.engine.PageScan
import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import com.anydrop.app.engine.toFormatInfos
import com.anydrop.app.ui.components.MediaThumb
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkGray
import com.anydrop.app.ui.theme.Hairline
import com.anydrop.app.ui.theme.LedRed
import com.anydrop.app.ui.theme.LightGray
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.OnAccent
import com.anydrop.app.ui.theme.White
import com.anydrop.app.ui.theme.tracked
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.Collections
import javax.inject.Inject

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
    // Desktop mode is remembered per site (like Chrome's "Desktop site"). Bumped on every
    // change so [desktopMode] is re-read.
    private val desktopPrefs = appContext.getSharedPreferences("anydrop_browser_desktop", Context.MODE_PRIVATE)
    private var desktopPrefsVersion by mutableIntStateOf(0)

    /** Whether the page on screen is shown as the desktop site. */
    val desktopMode: Boolean
        get() {
            desktopPrefsVersion
            return wantsDesktop(currentUrl)
        }
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
        // Pinch to zoom (desktop pages are shown zoomed out), without the old +/- buttons.
        wv.settings.builtInZoomControls = true
        wv.settings.displayZoomControls = false
        if (MediaSniffer.documentStartSupported) {
            WebViewCompat.addDocumentStartJavaScript(wv, PageTweaks.JS, setOf("*"))
        }
        applyUserAgent(wv, pendingUrl)
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                MediaSniffer.classifyNetworkUrl(request.url.toString())?.let { network.add(it) }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return when (uri.scheme) {
                    "http", "https" -> when {
                        PageTweaks.isAppInstallRedirect(uri) -> true
                        // Moving to a site shown the other way (desktop/mobile): reload it with
                        // the matching browser identity.
                        request.isForMainFrame && applyUserAgent(view, uri.toString()) -> {
                            view.loadUrl(uri.toString())
                            true
                        }
                        else -> false
                    }
                    "intent" -> {
                        // Sites try to bounce you into their app; stay here and follow only a
                        // web fallback (never an app-store one, which would just dead-end).
                        PageTweaks.webFallbackFor(uri.toString())?.let { view.loadUrl(it) }
                        true
                    }
                    else -> true
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url == null || url == "about:blank") return
                // Back/forward into a site shown the other way: fetch it again as that.
                if (applyUserAgent(view, url)) {
                    view.reload()
                    return
                }
                if (url != currentUrl) {
                    resetFindings()
                    pageTitle = ""
                }
                currentUrl = url
                isLoading = true
                if (!MediaSniffer.documentStartSupported) {
                    view.evaluateJavascript(MediaSniffer.CORE_JS, null)
                    view.evaluateJavascript(PageTweaks.JS, null)
                }
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
        pageTitle = ""
        currentUrl = url
        val wv = webView
        if (wv == null) {
            pendingUrl = url
        } else {
            applyUserAgent(wv, url)
            wv.loadUrl(url)
        }
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

    /** Switches the current site between its desktop and mobile versions, and remembers it. */
    fun toggleDesktop() {
        val url = currentUrl ?: return
        val key = siteKey(url) ?: return
        desktopPrefs.edit().putBoolean(key, !wantsDesktop(url)).apply()
        desktopPrefsVersion++
        webView?.let {
            applyUserAgent(it, url)
            it.reload()
        }
    }

    private fun wantsDesktop(url: String?): Boolean {
        val host = siteKey(url) ?: return false
        // The user's choice for this site (or a parent domain) wins over the defaults.
        var domain = host
        while (true) {
            if (desktopPrefs.contains(domain)) return desktopPrefs.getBoolean(domain, false)
            val dot = domain.indexOf('.')
            if (dot < 0 || domain.indexOf('.', dot + 1) < 0) break
            domain = domain.substring(dot + 1)
        }
        return DESKTOP_BY_DEFAULT.any { host == it || host.endsWith(".$it") }
    }

    /** Sets the browser identity [url] should get; true when it changed. */
    private fun applyUserAgent(view: WebView, url: String?): Boolean {
        val want = if (wantsDesktop(url)) MediaSniffer.desktopUserAgent(appContext) else MediaSniffer.mobileUserAgent(appContext)
        if (view.settings.userAgentString == want) return false
        view.settings.userAgentString = want
        return true
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
        /**
         * Sites opened as their desktop version until the user says otherwise. TikTok's
         * mobile site stops loading videos for visitors without the app and sends swipes
         * to the Play Store; its desktop site scrolls freely.
         */
        private val DESKTOP_BY_DEFAULT = setOf("tiktok.com")

        private fun siteKey(url: String?): String? =
            url?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                ?.lowercase()?.removePrefix("www.")?.removePrefix("m.")?.takeIf { it.isNotEmpty() }

        fun toUrl(input: String): String {
            val t = input.trim()
            Regex("""https?://\S+""").find(t)?.value?.let { return it }
            if (!t.contains(' ') && Regex("""^(localhost|127\.0\.0\.1)(:\d+)?(/.*)?$""").matches(t)) return "http://$t"
            if (!t.contains(' ') && Regex("""^[\w-]+(\.[\w-]+)+(:\d+)?(/.*)?$""").matches(t)) return "https://$t"
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
    private val libraryRepository: LibraryRepository,
    private val sitesRepository: BrowserSitesRepository,
    private val historyRepository: BrowserHistoryRepository,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    val sites: StateFlow<List<BrowserSite>> = sitesRepository.sites

    /** Pages visited, newest first. */
    val history: StateFlow<List<HistoryEntry>> = historyRepository.entries

    fun recordVisit(url: String, title: String) = historyRepository.record(url, title)
    fun removeHistory(entry: HistoryEntry) = historyRepository.remove(entry)
    fun clearHistory() = historyRepository.clear()

    /** Length, real resolution and a cover for the video files found on the page. */
    suspend fun probe(formats: List<FormatInfo>, pageUrl: String): List<FormatInfo> =
        runCatching { downloadRepository.probePageMedia(formats, pageUrl) }.getOrDefault(formats)

    fun addSite(name: String, url: String) = sitesRepository.add(name, url)
    fun updateSite(old: BrowserSite, name: String, url: String) = sitesRepository.update(old, name, url)
    fun removeSite(site: BrowserSite) = sitesRepository.remove(site)
    fun moveSiteToTop(site: BrowserSite) = sitesRepository.moveToTop(site)
    fun restoreDefaultSites() = sitesRepository.restoreDefaults()

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
    val sites by viewModel.sites.collectAsState()
    // Start page editing: the site being added/edited (null = closed) and the one to remove.
    var siteEditor by remember { mutableStateOf<SiteEditor?>(null) }
    var siteToRemove by remember { mutableStateOf<BrowserSite?>(null) }
    val history by viewModel.history.collectAsState()
    var historyOpen by remember { mutableStateOf(false) }
    val address = remember { AddressBarState() }
    val focusManager = LocalFocusManager.current
    val openAddress: (String) -> Unit = { target ->
        focusManager.clearFocus()
        state.load(target)
    }

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
    // ...but first closes the address bar's suggestions.
    BackHandler(enabled = address.editing) { focusManager.clearFocus() }

    // Every page that stays open a moment goes into the history (redirect hops don't), and
    // gets its title once the page names itself.
    LaunchedEffect(state.currentUrl, state.pageTitle) {
        val visited = state.currentUrl ?: return@LaunchedEffect
        delay(800)
        viewModel.recordVisit(visited, state.pageTitle)
    }

    // Keep looking while the page plays: many sites only fetch the video when it starts.
    LaunchedEffect(state.currentUrl) {
        while (state.currentUrl != null) {
            delay(1500)
            state.rescan()
        }
    }

    Column(Modifier.fillMaxSize().background(DarkGray)) {
        BrowserBar(
            state = state,
            address = address,
            onHistory = { historyOpen = true },
            onAddToStart = {
                val url = state.currentUrl ?: return@BrowserBar
                siteEditor = SiteEditor(
                    original = null,
                    name = BrowserSitesRepository.suggestName(state.pageTitle, url),
                    url = url
                )
            }
        )
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
                StartPage(
                    sites = sites,
                    recent = history.take(5),
                    onOpenHistory = { historyOpen = true },
                    showRestore = sites != BrowserSitesRepository.DEFAULT_SITES,
                    onOpen = state::load,
                    onAdd = { siteEditor = SiteEditor(original = null, name = "", url = "") },
                    onEdit = { site -> siteEditor = SiteEditor(original = site, name = site.name, url = site.url) },
                    onMoveToTop = viewModel::moveSiteToTop,
                    onRemove = { site -> siteToRemove = site },
                    onRestore = viewModel::restoreDefaultSites
                )
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
            if (address.editing) {
                AddressSuggestions(
                    query = if (address.typed) address.field.text else "",
                    clip = address.clip,
                    history = history,
                    onOpen = openAddress,
                    onSeeAll = {
                        focusManager.clearFocus()
                        historyOpen = true
                    }
                )
            }
        }
    }

    if (historyOpen) {
        HistorySheet(
            entries = history,
            onOpen = { target ->
                historyOpen = false
                openAddress(target)
            },
            onRemove = viewModel::removeHistory,
            onClear = viewModel::clearHistory,
            onDismiss = { historyOpen = false }
        )
    }

    siteEditor?.let { editor ->
        SiteDialog(
            editor = editor,
            onDismiss = { siteEditor = null },
            onSave = { name, url ->
                val original = editor.original
                if (original == null) {
                    viewModel.addSite(name, url)
                    if (!state.showingStart) Toast.makeText(context, R.string.browser_site_added, Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.updateSite(original, name, url)
                }
                siteEditor = null
            }
        )
    }

    siteToRemove?.let { site ->
        AlertDialog(
            onDismissRequest = { siteToRemove = null },
            containerColor = MediumGray,
            title = { Text(stringResource(R.string.browser_remove_site_title), color = LedRed, fontFamily = FontFamily.Monospace) },
            text = { Text(stringResource(R.string.browser_remove_site_message, site.name), color = White, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { viewModel.removeSite(site); siteToRemove = null }) {
                    Text(stringResource(R.string.browser_remove), color = LedRed, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { siteToRemove = null }) {
                    Text(stringResource(R.string.btn_cancel), color = White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
                }
            }
        )
    }

    if (sheetOpen) {
        val scan = state.scan
        val found = remember(scan) { scan?.toFormatInfos().orEmpty() }
        // The page is re-read while it plays; only look inside the files again when they change.
        val files = found.map { it.id + it.directUrl + it.imageUrl }
        var formats by remember(files) { mutableStateOf(found) }
        LaunchedEffect(files) {
            if (found.any(MediaProbe::needsProbe)) {
                formats = viewModel.probe(found, scan?.pageUrl ?: state.currentUrl.orEmpty())
            }
        }
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
private fun BrowserBar(state: BrowserState, address: AddressBarState, onHistory: () -> Unit, onAddToStart: () -> Unit) {
    val focus = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    val editing = address.editing
    val field = address.field
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
                    onValueChange = {
                        if (editing) {
                            if (it.text != field.text) address.typed = true
                            address.field = it
                        }
                    },
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
                            address.field = TextFieldValue(full, TextRange(0, full.length))
                            address.typed = false
                            // Offer what was just copied (a link copied in another app, say).
                            address.clip = runCatching { clipboard.getText()?.text }.getOrNull()
                                ?.trim()?.takeIf { it.isNotEmpty() && it.length <= 2000 && it != full }
                        }
                        address.editing = f.isFocused
                    }
                )
                if (!editing && display.isEmpty()) {
                    Text(stringResource(R.string.browser_search_hint), color = White.copy(alpha = 0.4f), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
            }
            if (editing && field.text.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close, null,
                    Modifier.size(18.dp).clip(CircleShape).clickable {
                        address.field = TextFieldValue("")
                        address.typed = true
                    },
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
                    text = { Text(stringResource(R.string.browser_history)) },
                    leadingIcon = { Icon(Icons.Filled.History, null) },
                    onClick = { menu = false; onHistory() }
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
                    enabled = !state.showingStart,
                    onClick = { menu = false; state.toggleDesktop() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.browser_add_to_start)) },
                    leadingIcon = { Icon(Icons.Filled.BookmarkAdd, null) },
                    enabled = !state.showingStart,
                    onClick = { menu = false; onAddToStart() }
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

/** The site being added ([original] == null) or edited in [SiteDialog]. */
private data class SiteEditor(val original: BrowserSite?, val name: String, val url: String)

/**
 * The user's own start page: tap a site to open it, long-press it to edit, move or
 * remove it, and use the last tile to add one. The shipped sites can be removed too,
 * and restored in one tap.
 */
@Composable
private fun StartPage(
    sites: List<BrowserSite>,
    recent: List<HistoryEntry>,
    onOpenHistory: () -> Unit,
    showRestore: Boolean,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (BrowserSite) -> Unit,
    onMoveToTop: (BrowserSite) -> Unit,
    onRemove: (BrowserSite) -> Unit,
    onRestore: () -> Unit
) {
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
            // null stands for the trailing "add site" tile.
            (sites + listOf<BrowserSite?>(null)).chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { site ->
                        if (site == null) {
                            AddSiteTile(onClick = onAdd, modifier = Modifier.weight(1f))
                        } else {
                            SiteTile(
                                site = site,
                                canMoveToTop = site != sites.first(),
                                onOpen = { onOpen(site.url) },
                                onEdit = { onEdit(site) },
                                onMoveToTop = { onMoveToTop(site) },
                                onRemove = { onRemove(site) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.browser_sites_hint),
            color = White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        if (showRestore) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onRestore) {
                Icon(Icons.Filled.RestartAlt, null, tint = Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.browser_restore_sites), color = Amber, fontSize = 12.sp)
            }
        }
        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.browser_recent),
                    color = White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = tracked(2f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.browser_see_all),
                    color = Amber,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpenHistory).padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Black)
                    .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                    .padding(vertical = 4.dp)
            ) {
                recent.forEach { entry -> HistoryRow(entry, onOpen = { onOpen(entry.url) }) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteTile(
    site: BrowserSite,
    canMoveToTop: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onMoveToTop: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    }
                )
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
                Text(site.initial(), color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                site.name,
                color = White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_edit)) },
                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                onClick = { menu = false; onEdit() }
            )
            if (canMoveToTop) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.browser_move_top)) },
                    leadingIcon = { Icon(Icons.Filled.VerticalAlignTop, null) },
                    onClick = { menu = false; onMoveToTop() }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_remove), color = LedRed) },
                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = LedRed) },
                onClick = { menu = false; onRemove() }
            )
        }
    }
}

@Composable
private fun AddSiteTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .border(1.dp, LightGray, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, null, tint = Amber)
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.browser_add_site), color = Amber, fontSize = 11.sp, maxLines = 1)
    }
}

/** First letter (or emoji) of the site's name for its tile. */
private fun BrowserSite.initial(): String {
    val n = name.trim()
    if (n.isEmpty()) return "?"
    return String(Character.toChars(n.codePointAt(0))).uppercase()
}

/** Add or edit a start page site: its web address (checked as you type) and a name. */
@Composable
private fun SiteDialog(
    editor: SiteEditor,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String) -> Unit
) {
    var url by remember(editor) { mutableStateOf(editor.url) }
    var name by remember(editor) { mutableStateOf(editor.name) }
    var urlEdited by remember(editor) { mutableStateOf(false) }
    // A blank "add site" dialog opens ready to type the address.
    val urlFocus = remember { FocusRequester() }
    LaunchedEffect(editor) { if (editor.url.isEmpty()) urlFocus.requestFocus() }
    val normalized = BrowserSitesRepository.normalizeUrl(url)
    val showError = urlEdited && url.isNotBlank() && normalized == null
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Amber,
        unfocusedBorderColor = LightGray,
        errorBorderColor = LedRed,
        cursorColor = Amber,
        focusedTextColor = White,
        unfocusedTextColor = White,
        errorTextColor = White,
        focusedLabelColor = Amber,
        unfocusedLabelColor = White.copy(alpha = 0.6f),
        errorLabelColor = LedRed,
        errorSupportingTextColor = LedRed,
        focusedPlaceholderColor = White.copy(alpha = 0.35f),
        unfocusedPlaceholderColor = White.copy(alpha = 0.35f)
    )
    // Reads the fields when tapped. (A local fun captured `normalized` and the Compose
    // compiler kept an early copy of it, which saved a half-typed address.)
    val save: () -> Unit = {
        BrowserSitesRepository.normalizeUrl(url)?.let { address ->
            onSave(name.trim().ifEmpty { BrowserSitesRepository.defaultName(address) }, address)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MediumGray,
        title = {
            Text(
                stringResource(if (editor.original == null) R.string.browser_add_site else R.string.browser_edit_site),
                color = Amber,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.replace("\n", ""); urlEdited = true },
                    label = { Text(stringResource(R.string.browser_site_url)) },
                    placeholder = { Text("example.com", style = TextStyle(textDirection = TextDirection.Ltr)) },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.browser_site_url_invalid)) }
                    } else null,
                    // Web addresses always read left to right, even in the Arabic UI.
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, textDirection = TextDirection.Ltr),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().focusRequester(urlFocus)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.replace("\n", "").take(40) },
                    label = { Text(stringResource(R.string.browser_site_name)) },
                    placeholder = normalized?.let { address ->
                        { Text(BrowserSitesRepository.defaultName(address)) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = save, enabled = normalized != null) {
                Text(
                    stringResource(if (editor.original == null) R.string.btn_add else R.string.btn_save),
                    color = if (normalized != null) Amber else White.copy(alpha = 0.3f),
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
            }
        }
    )
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
                        // Videos show their cover and length, so the one playing stands out from ads.
                        if (f.isAudioOnly) {
                            KindIcon(Icons.Filled.MusicNote)
                        } else {
                            MediaThumb(
                                cover = f.previewUrl.ifBlank { f.imageUrl },
                                durationSec = f.durationSec,
                                isVideo = !f.isImage,
                                modifier = Modifier.width(72.dp).aspectRatio(16f / 9f)
                            )
                        }
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

/** The address bar's typing state, shared with the suggestions shown under it while typing. */
@Stable
private class AddressBarState {
    var editing by mutableStateOf(false)
    var field by mutableStateOf(TextFieldValue(""))
    /** False until the user changes the text (the bar opens holding the current address). */
    var typed by mutableStateOf(false)
    /** What was on the clipboard when typing started, offered as the first suggestion. */
    var clip by mutableStateOf<String?>(null)
}

/**
 * Shown over the page while typing an address: the link (or text) just copied, then history
 * matching what's typed (the most recent pages before anything is typed).
 */
@Composable
private fun AddressSuggestions(
    query: String,
    clip: String?,
    history: List<HistoryEntry>,
    onOpen: (String) -> Unit,
    onSeeAll: () -> Unit
) {
    val matches = remember(query, history) { HistoryList.search(history, query, 8) }
    Column(
        Modifier
            .fillMaxSize()
            .background(DarkGray)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        if (clip != null && query.isBlank()) {
            SuggestionLabel(stringResource(R.string.browser_copied))
            val isLink = Regex("""^(https?://)?[\w-]+(\.[\w-]+)+(/\S*)?$""", RegexOption.IGNORE_CASE).matches(clip) ||
                Regex("""https?://\S+""").containsMatchIn(clip)
            SuggestionRow(
                icon = Icons.Filled.ContentPaste,
                title = stringResource(if (isLink) R.string.browser_go_copied_link else R.string.browser_search_copied),
                subtitle = clip,
                onClick = { onOpen(clip) }
            )
        }
        if (matches.isNotEmpty()) {
            SuggestionLabel(stringResource(if (query.isBlank()) R.string.browser_recent else R.string.browser_from_history))
            matches.forEach { entry ->
                SuggestionRow(
                    icon = Icons.Filled.History,
                    title = entry.title.ifBlank { displayAddress(entry.url) },
                    subtitle = displayAddress(entry.url),
                    onClick = { onOpen(entry.url) }
                )
            }
        }
        if (history.isNotEmpty()) {
            TextButton(onClick = onSeeAll, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Filled.History, null, tint = Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.browser_see_all_history), color = Amber, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        } else if (clip == null) {
            Text(
                stringResource(R.string.browser_history_empty),
                color = White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun SuggestionLabel(text: String) {
    Text(
        text,
        color = White.copy(alpha = 0.5f),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        letterSpacing = tracked(2f),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun SuggestionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Amber, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                color = White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(textDirection = TextDirection.Ltr)
            )
        }
    }
}

/** Everything the browser visited, by day: tap to open, remove one, or clear it all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    entries: List<HistoryEntry>,
    onOpen: (String) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val today = stringResource(R.string.browser_today)
    val yesterday = stringResource(R.string.browser_yesterday)
    val days = remember(entries, locale) {
        val now = System.currentTimeMillis()
        val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
        entries.groupBy { e ->
            when (HistoryList.daysAgo(e.visitedAt, now, TimeZone.getDefault().getOffset(now).toLong())) {
                0L -> today
                1L -> yesterday
                else -> dateFormat.format(Date(e.visitedAt))
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DarkGray
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.browser_history_title),
                    color = Amber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = tracked(2f),
                    modifier = Modifier.weight(1f)
                )
                if (entries.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) {
                        Text(stringResource(R.string.browser_history_clear), color = LedRed, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.browser_history_empty),
                    color = White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                    days.forEach { (day, list) ->
                        item(key = "day:$day") { SuggestionLabel(day) }
                        items(list, key = { it.url }) { entry ->
                            HistoryRow(entry, onOpen = { onOpen(entry.url) }, onRemove = { onRemove(entry) }, locale = locale)
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = MediumGray,
            title = { Text(stringResource(R.string.browser_history_clear_title), color = LedRed, fontFamily = FontFamily.Monospace) },
            text = { Text(stringResource(R.string.browser_history_clear_message), color = White, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { onClear(); confirmClear = false }) {
                    Text(stringResource(R.string.browser_history_clear), color = LedRed, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.btn_cancel), color = White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}

/** A visited page: the site's letter, its title, address and (with [locale]) the time of the visit. */
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onOpen: () -> Unit,
    onRemove: (() -> Unit)? = null,
    locale: Locale? = null
) {
    val host = BrowserSitesRepository.hostOf(entry.url)
    val color = Color(BrowserSitesRepository.tintFor(entry.url))
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = if (onRemove != null) 4.dp else 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.16f))
                .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val letter = host.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Text(letter, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title.ifBlank { displayAddress(entry.url) }, color = White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val time = locale?.let { DateFormat.getTimeInstance(DateFormat.SHORT, it).format(Date(entry.visitedAt)) }
            Text(
                // Each part keeps its own direction ("localhost", "1:26 ص") inside the Arabic layout.
                listOfNotNull(host.ifBlank { null }, time).joinToString(" · ") { "\u2068$it\u2069" },
                color = White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, stringResource(R.string.browser_history_remove), tint = White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** "https://www.example.com/a/" → "example.com/a". */
private fun displayAddress(url: String): String =
    url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
