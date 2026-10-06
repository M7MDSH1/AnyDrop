package com.anydrop.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Collections
import kotlin.coroutines.resume

/** A media URL found on a web page, with a confidence score (higher = more likely the real one). */
data class MediaCandidate(
    val url: String,
    val score: Int,
    val source: String,
    val width: Int = 0,
    val height: Int = 0,
    val code: String? = null,
    val quality: String? = null,
    /** Position in a multi-photo/video post (carousel), or -1. */
    val slide: Int = -1,
    /** Cover picture of a video slide, for previews. */
    val cover: String? = null,
) {
    val pixels: Int get() = width * height
    val isHls: Boolean get() = url.substringBefore('?').lowercase().endsWith(".m3u8")
    val isDash: Boolean get() = url.substringBefore('?').lowercase().endsWith(".mpd")
}

data class PageScan(
    val pageUrl: String,
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val videos: List<MediaCandidate>,
    val images: List<MediaCandidate>,
    val userAgent: String,
)

/**
 * Finds media the way a browser sees it. This is what lets Instagram (and any other site)
 * work without logging in: a real browser engine loads the page anonymously, then the media
 * URLs are read out of the page's data, its network traffic and its <video> tags.
 *
 * Verified 2026-09: logged-out Instagram serves `video_versions` (progressive MP4 with audio)
 * only to a *desktop* browser; the mobile page is an "open in app" wall with no media data.
 */
object MediaSniffer {

    private const val TAG = "MediaSniffer"

    /**
     * Injected into every page. Hooks fetch/XHR so JSON the page downloads is scanned too, and
     * exposes window.__ad.collect(). ES5 only; `$` written as ${'$'} for the Kotlin raw string.
     */
    val CORE_JS = """
(function () {
  if (window.__ad) return;
  var ad = window.__ad = { hits: [], seen: {}, imgs: [], seenImg: {}, scanned: (typeof WeakSet !== 'undefined') ? new WeakSet() : null };
  var MEDIA_PATH = /\.(mp4|m3u8|webm|mov|m4v|mkv|mpd)${'$'}/i;
  function clean(u) {
    if (typeof u !== 'string') return null;
    u = u.replace(/\\u0026/gi, '&').replace(/\\u003d/gi, '=').replace(/\\u002f/gi, '/')
         .replace(/\\\//g, '/').replace(/&amp;/g, '&').replace(/\\+${'$'}/, '').trim();
    if (u.indexOf('//') === 0) u = location.protocol + u;
    if (!/^https?:\/\//i.test(u)) return null;
    return u;
  }
  function pathOf(u) { return u.split('#')[0].split('?')[0]; }
  function isMedia(u) { return !!u && MEDIA_PATH.test(pathOf(u)); }
  function junk(u) {
    return /[?&](bytestart|byteend|range)=/i.test(u) || /\.(m4s|ts)${'$'}/i.test(pathOf(u)) ||
      /googlevideo\.com|doubleclick|googlesyndication|googleads|adservice|imasdk|adnxs|moatads|\/ads\//i.test(u);
  }
  function add(u, score, src, meta) {
    u = clean(u);
    if (!u || junk(u)) return;
    var k, prev = ad.seen[u];
    if (prev) {
      if (score > prev.score) prev.score = score;
      if (meta) for (k in meta) if (meta[k] != null && prev[k] == null) prev[k] = meta[k];
      return;
    }
    var c = { url: u, score: score, src: src };
    if (meta) for (k in meta) if (meta[k] != null) c[k] = meta[k];
    ad.seen[u] = c;
    ad.hits.push(c);
  }
  function addImg(u, meta) {
    u = clean(u);
    if (!u || ad.seenImg[u]) return;
    var c = { url: u, score: 100, src: 'ig' };
    for (var k in meta) if (meta[k] != null) c[k] = meta[k];
    ad.seenImg[u] = c;
    ad.imgs.push(c);
  }
  function largest(list) {
    var best = null;
    for (var i = 0; i < list.length; i++) {
      var v = list[i];
      if (v && v.url && (!best || (v.width || 0) * (v.height || 0) > (best.width || 0) * (best.height || 0))) best = v;
    }
    return best;
  }
  var HD = { browser_native_hd_url: 1, playable_url_quality_hd: 1, hd_src: 1, hd_src_no_ratelimit: 1 };
  var SD = { browser_native_sd_url: 1, playable_url: 1, sd_src: 1, sd_src_no_ratelimit: 1 };
  var KEYS = { video_url: 1, videoUrl: 1, contentUrl: 1, content_url: 1, download_url: 1, downloadUrl: 1,
    playback_url: 1, playbackUrl: 1, stream_url: 1, hls_url: 1, hlsUrl: 1, hls: 1, mp4: 1, mp4_url: 1,
    file: 1, src: 1, url: 1, source: 1, video: 1, play_addr: 1, playAddr: 1, downloadAddr: 1 };
  // `inherit`: carousel slides carry their own `code`, but belong to the parent post.
  // `slide`: position in the post's carousel, so slides keep their order and identity.
  function walk(o, ctx, depth, inherit, slide) {
    if (!o || depth > 60) return;
    var i;
    if (Array.isArray(o)) { for (i = 0; i < o.length; i++) walk(o[i], ctx, depth + 1, inherit, slide); return; }
    if (typeof o !== 'object') return;
    var code = ctx;
    if (!inherit) {
      if (typeof o.code === 'string' && /^[A-Za-z0-9_-]{5,40}${'$'}/.test(o.code)) code = o.code;
      else if (typeof o.shortcode === 'string') code = o.shortcode;
    }
    if (Array.isArray(o.carousel_media)) {
      for (i = 0; i < o.carousel_media.length; i++) walk(o.carousel_media[i], code, depth + 1, true, i);
    }
    var sidecar = o.edge_sidecar_to_children && o.edge_sidecar_to_children.edges;
    if (Array.isArray(sidecar)) {
      for (i = 0; i < sidecar.length; i++) walk(sidecar[i] && sidecar[i].node, code, depth + 1, true, i);
    }
    var vv = o.video_versions;
    if (vv && vv.length) {
      var best = largest(vv);
      var cover = o.image_versions2 && o.image_versions2.candidates ? largest(o.image_versions2.candidates) : null;
      if (best) add(best.url, 100, 'ig', { code: code, w: best.width || o.original_width, h: best.height || o.original_height,
        slide: slide, cover: cover && cover.url });
    } else if (o.image_versions2 && o.image_versions2.candidates && !o.carousel_media) {
      // Instagram/Threads photo (or one photo slide of a carousel).
      var img = largest(o.image_versions2.candidates);
      if (img) addImg(img.url, { code: code, w: img.width, h: img.height, slide: slide });
    }
    if (typeof o.video_url === 'string' && (o.is_video === true || /Video/.test(o.__typename || ''))) {
      var d = o.dimensions || {};
      add(o.video_url, 98, 'ig', { code: code, w: d.width, h: d.height, slide: slide, cover: o.display_url });
    } else if (typeof o.display_url === 'string' && o.is_video === false && !o.edge_sidecar_to_children) {
      var dd = o.dimensions || {};
      addImg(o.display_url, { code: code, w: dd.width, h: dd.height, slide: slide });
    }
    if (o['@type'] === 'VideoObject' && typeof o.contentUrl === 'string') add(o.contentUrl, 82, 'ld');
    for (var k in o) {
      if (!Object.prototype.hasOwnProperty.call(o, k)) continue;
      if (k === 'carousel_media' || k === 'edge_sidecar_to_children') continue; // walked above
      var val = o[k];
      if (typeof val === 'string') {
        if (val.length < 10 || val.length > 5000) continue;
        if (HD[k]) add(val, 95, 'fb', { q: 'HD', code: code });
        else if (SD[k]) add(val, 88, 'fb', { q: 'SD', code: code });
        else if (KEYS[k] && isMedia(clean(val))) add(val, 70, 'json', { code: code });
      } else if (val && typeof val === 'object') {
        walk(val, code, depth + 1, inherit, slide);
      }
    }
  }
  function scanText(t, src) {
    if (!t || t.length < 20 || t.length > 8000000) return;
    var s = t.replace(/^\s*for\s*\(;;\);/, '');
    var c = s.charAt(0);
    if (c === '{' || c === '[') {
      try { walk(JSON.parse(s), null, 0); }
      catch (e) {
        var lines = s.split('\n');
        for (var i = 0; i < lines.length && i < 400; i++) {
          var l = lines[i].trim();
          if (l.charAt(0) === '{') { try { walk(JSON.parse(l), null, 0); } catch (e2) {} }
        }
      }
    }
    var re = /https?:(?:\\?\/){2}[^"'\s<>]+?\.(?:mp4|m3u8|webm|mov|m4v)(?:[?#][^"'\s<>]*)?/gi, m, n = 0;
    while ((m = re.exec(s)) && n++ < 150) add(m[0], 40, src);
  }
  function meta(p) {
    var m = document.querySelector('meta[property="' + p + '"],meta[name="' + p + '"]');
    return m ? m.getAttribute('content') : null;
  }
  ad.collect = function () {
    try {
      var scripts = document.querySelectorAll('script[type="application/json"],script[type="application/ld+json"],script#__NEXT_DATA__');
      for (var i = 0; i < scripts.length; i++) {
        var sc = scripts[i];
        if (ad.scanned) { if (ad.scanned.has(sc)) continue; ad.scanned.add(sc); }
        var t = sc.textContent;
        if (t && t.length < 8000000) { try { walk(JSON.parse(t), null, 0); } catch (e) { scanText(t, 'script'); } }
      }
      ['og:video:secure_url', 'og:video:url', 'og:video', 'twitter:player:stream'].forEach(function (p) {
        var v = meta(p);
        if (v && isMedia(clean(v))) add(v, 85, 'og');
      });
      var vids = document.querySelectorAll('video,video source');
      for (var j = 0; j < vids.length; j++) {
        var el = vids[j];
        var s = el.currentSrc || el.src || el.getAttribute('src') || el.getAttribute('data-src');
        if (s && s.indexOf('blob:') !== 0) add(s, 78, 'video', { w: el.videoWidth || null, h: el.videoHeight || null });
      }
      if (ad.hits.length === 0 && !ad.htmlScanned && document.readyState === 'complete') {
        ad.htmlScanned = true;
        scanText(document.documentElement.innerHTML, 'html');
      }
    } catch (e) {}
    var hits = ad.hits.slice().sort(function (a, b) { return b.score - a.score; }).slice(0, 150);
    return JSON.stringify({
      title: meta('og:title') || document.title || '',
      thumb: meta('og:image') || meta('twitter:image') || null,
      url: location.href,
      hits: hits,
      imgs: ad.imgs.slice(0, 60)
    });
  };
  ad.kick = function () {
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      try { vids[i].muted = true; var p = vids[i].play(); if (p && p.catch) p.catch(function () {}); } catch (e) {}
    }
  };
  try {
    var origFetch = window.fetch;
    if (origFetch) window.fetch = function () {
      var p = origFetch.apply(this, arguments);
      try {
        p.then(function (r) {
          try {
            var ct = (r.headers.get('content-type') || '').toLowerCase();
            if (/video\/|mpegurl|dash\+xml/.test(ct)) { add(r.url, 64, 'net'); return; }
            if (ct !== '' && !/json|javascript|text|html/.test(ct)) return;
            if (+(r.headers.get('content-length') || 0) > 8000000) return;
            r.clone().text().then(function (t) { scanText(t, 'fetch'); }).catch(function () {});
          } catch (e) {}
        }).catch(function () {});
      } catch (e) {}
      return p;
    };
  } catch (e) {}
  try {
    var origSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function () {
      var x = this;
      try {
        x.addEventListener('load', function () {
          try {
            var rt = x.responseType;
            if (rt === '' || rt === 'text') scanText(x.responseText, 'xhr');
            else if (rt === 'json' && x.response) walk(x.response, null, 0);
          } catch (e) {}
        });
      } catch (e) {}
      return origSend.apply(this, arguments);
    };
  } catch (e) {}
})();
""".trimIndent()

    val COLLECT_JS = "$CORE_JS\nwindow.__ad.collect();"
    val KICK_JS = "$CORE_JS\nwindow.__ad.kick(); true;"

    private val JUNK = Regex("""googlevideo\.com|doubleclick|googlesyndication|googleads|adservice|imasdk|adnxs|moatads|/ads/|[?&](bytestart|byteend|range)=""")
    private val IG_CODE = Regex("""(?:instagram\.com|threads\.(?:net|com))/(?:[^/?#]+/)?(?:p|reel|reels|tv|post)/([A-Za-z0-9_-]+)""")

    fun shortcodeOf(url: String): String? = IG_CODE.find(url)?.groupValues?.get(1)

    /** Media requests seen on the wire (from WebViewClient.shouldInterceptRequest). */
    fun classifyNetworkUrl(url: String): MediaCandidate? {
        if (!url.startsWith("http")) return null
        val lower = url.lowercase()
        if (JUNK.containsMatchIn(lower)) return null
        val path = lower.substringBefore('#').substringBefore('?')
        return when {
            path.endsWith(".m3u8") -> MediaCandidate(url, if ("master" in path || "playlist" in path) 74 else 70, "hls")
            path.endsWith(".mpd") -> MediaCandidate(url, 55, "dash")
            path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mov") || path.endsWith(".m4v") ->
                MediaCandidate(url, 62, "net")
            else -> null
        }
    }

    /** Parses what COLLECT_JS returned and merges it with network hits. */
    fun parse(raw: String?, network: List<MediaCandidate>, fallbackUrl: String, userAgent: String): PageScan {
        var title = ""
        var thumb: String? = null
        var url = fallbackUrl
        val videos = mutableListOf<MediaCandidate>()
        val images = mutableListOf<MediaCandidate>()
        try {
            val obj = when (val decoded = raw?.let { JSONTokener(it).nextValue() }) {
                is String -> JSONObject(decoded)
                is JSONObject -> decoded
                else -> null
            }
            if (obj != null) {
                title = obj.optString("title")
                thumb = obj.optString("thumb").takeIf { it.startsWith("http") }
                url = obj.optString("url").takeIf { it.startsWith("http") } ?: fallbackUrl
                obj.optJSONArray("hits")?.let { videos += it.toCandidates() }
                obj.optJSONArray("imgs")?.let { images += it.toCandidates() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse page scan: ${e.message}")
        }
        videos += network
        val (cleanTitle, uploader) = cleanTitle(title, url)
        return PageScan(url, cleanTitle, uploader, thumb, rank(url, videos), rankImages(url, images), userAgent)
    }

    private fun JSONArray.toCandidates(): List<MediaCandidate> = (0 until length()).mapNotNull { i ->
        val h = optJSONObject(i) ?: return@mapNotNull null
        MediaCandidate(
            url = h.optString("url"),
            score = h.optInt("score"),
            source = h.optString("src"),
            width = h.optInt("w", 0),
            height = h.optInt("h", 0),
            code = h.optString("code").takeIf { it.isNotEmpty() && it != "null" },
            quality = h.optString("q").takeIf { it.isNotEmpty() && it != "null" },
            slide = h.optInt("slide", -1),
            cover = h.optString("cover").takeIf { it.startsWith("http") },
        )
    }

    /** Best candidates first; drops weak guesses when a strong match exists. */
    fun rank(pageUrl: String, all: List<MediaCandidate>): List<MediaCandidate> {
        var list = all.filter { it.url.startsWith("http") }
            .groupBy { it.url }
            .map { (_, same) -> same.maxBy { it.score } }
        val code = shortcodeOf(pageUrl)
        if (code != null) {
            // Instagram pages also carry "more reels" data; keep only this post's media.
            val matched = list.filter { it.code == code }
            if (matched.isNotEmpty()) list = matched
        }
        list = list.sortedWith(compareByDescending<MediaCandidate> { it.score }.thenByDescending { it.pixels })
        val top = list.firstOrNull()?.score ?: return emptyList()
        if (top >= 85) list = list.filter { it.score >= 70 }
        // Every slide of a carousel stays; other guesses are capped.
        return list.filterIndexed { i, c -> c.slide >= 0 || i < 8 }
    }

    private fun rankImages(pageUrl: String, all: List<MediaCandidate>): List<MediaCandidate> {
        val code = shortcodeOf(pageUrl) ?: return emptyList() // only trust images on post pages
        return all.filter { it.code == code }.distinctBy { it.url }.take(20)
    }

    /** Returns (title, uploader). */
    fun cleanTitle(raw: String, url: String): Pair<String, String> {
        var t = raw.replace(Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]"), "").replace(Regex("\\s+"), " ").trim()
        var uploader = ""
        // Instagram: `Name on Instagram: "caption..."` (the "on" is localized, e.g. "على" in Arabic).
        Regex("""^(.*?)\s+\S+\s+(Instagram|Threads)\s*:\s*["“](.*)""").find(t)?.let { m ->
            uploader = m.groupValues[1].trim()
            val caption = m.groupValues[3].trimEnd('"', '”').trim()
            t = caption.ifEmpty { uploader }
        }
        t = t.replace(Regex("""\s*[|•·\-–]\s*(Instagram|Facebook|TikTok|X|Twitter|YouTube|Threads|Reddit|Vimeo)\s*$""", RegexOption.IGNORE_CASE), "")
        if (t.length > 90) t = t.take(90).trimEnd() + "…"
        if (t.isBlank() || t.equals("Instagram", true)) {
            val host = runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: "video"
            t = shortcodeOf(url)?.let { "$host $it" } ?: host
        }
        return t to uploader
    }

    // ── User agents ──────────────────────────────────────────────────────────

    @Volatile private var chromeMajor: String? = null

    private fun chromeVersion(context: Context): String {
        chromeMajor?.let { return it }
        val v = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
            ?.let { Regex("""Chrome/(\d+)""").find(it)?.groupValues?.get(1) } ?: "131"
        chromeMajor = v
        return v
    }

    fun desktopUserAgent(context: Context) =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${chromeVersion(context)}.0.0.0 Safari/537.36"

    /** Regular Chrome for Android, without the "; wv" marker some sites block. */
    fun mobileUserAgent(context: Context): String {
        val default = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
            ?: return "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${chromeVersion(context)}.0.0.0 Mobile Safari/537.36"
        return default.replace("; wv", "").replace(Regex("""Version/\d+(\.\d+)* """), "")
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView, userAgent: String) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = userAgent
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, CORE_JS, setOf("*"))
        }
    }

    val documentStartSupported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    /** Cookies the browser engine holds for [url] (anonymous session cookies unless the user signed in). */
    fun cookieHeaderFor(url: String): String? =
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()?.takeIf { it.isNotBlank() }
}

/** What was found on a page, in the same shape as yt-dlp's formats (used by the deck and the browser). */
fun PageScan.toFormatInfos(): List<YtDlpEngine.FormatInfo> {
    val several = videos.count { it.score >= 95 } > 1
    val used = mutableMapOf<String, Int>()
    val videoFormats = videos.mapIndexed { i, c ->
        val short = minOf(c.width, c.height).takeIf { it > 0 } ?: maxOf(c.width, c.height)
        var label = when {
            short > 0 -> "${short}p"
            c.quality != null -> c.quality
            c.isHls -> "Auto"
            else -> "Original"
        }
        if (several && c.score >= 95) label = "Video ${(if (c.slide >= 0) c.slide else i) + 1} · $label"
        val n = (used[label] ?: 0) + 1
        used[label] = n
        if (n > 1) label = "$label ($n)"
        YtDlpEngine.FormatInfo(
            id = "web:$i",
            extension = if (c.isHls || c.isDash) "mp4" else c.url.substringBefore('?').substringAfterLast('.', "mp4").take(4).lowercase(),
            resolution = label,
            filesize = 0L,
            vcodec = "page",
            acodec = "page",
            directUrl = c.url,
            userAgent = userAgent,
            slide = if (c.score >= 95) c.slide else -1,
            previewUrl = c.cover.orEmpty()
        )
    }
    val audio = videos.firstOrNull()?.let { first ->
        YtDlpEngine.FormatInfo(
            id = "web:audio",
            extension = "m4a",
            resolution = "Audio",
            filesize = 0L,
            vcodec = "none",
            acodec = "page",
            isAudioOnly = true,
            directUrl = first.url,
            userAgent = userAgent
        )
    }
    val imageFormats = images.mapIndexed { i, c ->
        YtDlpEngine.FormatInfo(
            id = "",
            extension = YtDlpFormatParser.imageExtFromUrl(c.url),
            resolution = if (images.size > 1) "Image ${i + 1}/${images.size}" else "Image (photo)",
            filesize = 0L,
            vcodec = "none",
            acodec = "none",
            isImage = true,
            imageUrl = c.url,
            slide = c.slide,
            previewUrl = c.url
        )
    }
    return videoFormats + listOfNotNull(audio) + imageFormats
}

/** Loads a page in an invisible WebView and returns the media it finds. */
class WebVideoExtractor(context: Context) {

    private val appContext = context.applicationContext

    suspend fun extract(pageUrl: String, desktop: Boolean, timeoutMs: Long = 25_000): PageScan =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val ua = if (desktop) MediaSniffer.desktopUserAgent(appContext) else MediaSniffer.mobileUserAgent(appContext)
                val session = HeadlessSession(appContext, pageUrl, ua, timeoutMs) { scan ->
                    if (cont.isActive) cont.resume(scan)
                }
                cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { session.finish(null) } }
                session.start()
            }
        }
}

private class HeadlessSession(
    private val context: Context,
    private val pageUrl: String,
    private val userAgent: String,
    private val timeoutMs: Long,
    private val onDone: (PageScan) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val network = Collections.synchronizedList(mutableListOf<MediaCandidate>())
    private var webView: WebView? = null
    private val startedAt = SystemClock.elapsedRealtime()
    private var pageFinishedAt = 0L
    private var kicked = false
    private var finished = false
    private var lastScan: PageScan? = null

    fun start() {
        val wv = try {
            WebView(context)
        } catch (e: Exception) {
            Log.e("WebVideoExtractor", "WebView unavailable", e)
            finish(null)
            return
        }
        MediaSniffer.configure(wv, userAgent)
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                MediaSniffer.classifyNetworkUrl(request.url.toString())?.let { network.add(it) }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (!MediaSniffer.documentStartSupported) view.evaluateJavascript(MediaSniffer.CORE_JS, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (pageFinishedAt == 0L) pageFinishedAt = SystemClock.elapsedRealtime()
            }
        }
        // Give the page a real viewport; responsive sites don't render their player at 0x0.
        wv.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        wv.layout(0, 0, 1080, 1920)
        webView = wv
        wv.loadUrl(pageUrl)
        handler.postDelayed(poll, 1200)
    }

    private val poll: Runnable = object : Runnable {
        override fun run() {
            val wv = webView ?: return
            wv.evaluateJavascript(MediaSniffer.COLLECT_JS) { raw ->
                if (finished) return@evaluateJavascript
                val snapshot = synchronized(network) { network.toList() }
                val scan = MediaSniffer.parse(raw, snapshot, pageUrl, userAgent)
                lastScan = scan
                val now = SystemClock.elapsedRealtime()
                val elapsed = now - startedAt
                val sinceFinish = if (pageFinishedAt > 0) now - pageFinishedAt else 0L
                val best = scan.videos.firstOrNull()?.score ?: 0
                when {
                    best >= 95 && elapsed > 1500 -> finish(scan)
                    scan.images.isNotEmpty() && pageFinishedAt > 0 && sinceFinish > 2500 -> finish(scan)
                    best >= 60 && pageFinishedAt > 0 && sinceFinish > 3500 -> finish(scan)
                    elapsed > timeoutMs -> finish(scan)
                    else -> {
                        if (!kicked && pageFinishedAt > 0 && sinceFinish > 1500) {
                            kicked = true
                            wv.evaluateJavascript(MediaSniffer.KICK_JS, null)
                        }
                        handler.postDelayed(this, 900)
                    }
                }
            }
        }
    }

    fun finish(scan: PageScan?) {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        webView?.let {
            it.stopLoading()
            it.destroy()
        }
        webView = null
        onDone(
            scan ?: lastScan ?: PageScan(
                pageUrl, MediaSniffer.cleanTitle("", pageUrl).first, "", null, emptyList(), emptyList(), userAgent
            )
        )
    }
}
