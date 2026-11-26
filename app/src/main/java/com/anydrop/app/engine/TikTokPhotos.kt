package com.anydrop.app.engine

import android.util.Log
import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

/**
 * TikTok photo posts: a few pictures with a song. yt-dlp can't read them: it doesn't know
 * `/photo/` links, and for the post itself it returns only the song. The post's mobile page
 * has everything: every picture at full size and the song's file, which download without
 * cookies. Checked 2026-11: the mobile page carries the post in its `api-data` script; the
 * desktop page (`__UNIVERSAL_DATA_FOR_REHYDRATION__`) soon answers with a "Please wait" bot
 * check instead.
 */
object TikTokPhotos {

    private const val TAG = "TikTokPhotos"

    data class Photo(val url: String, val width: Int, val height: Int)
    data class Song(val url: String, val cover: String, val durationSec: Int)
    data class Post(val title: String, val uploader: String, val photos: List<Photo>, val song: Song?)

    /** A share link (vt.tiktok.com/…, vm.tiktok.com/…, tiktok.com/t/…) that forwards to the post. */
    fun isShortLink(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase().orEmpty()
        return host == "vt.tiktok.com" || host == "vm.tiktok.com" ||
            (host.removePrefix("www.") == "tiktok.com" && uri.path.orEmpty().startsWith("/t/"))
    }

    fun isPhotoLink(url: String): Boolean =
        runCatching { URI(url.trim()).path }.getOrNull().orEmpty().contains("/photo/")

    /**
     * The photo post behind [url], or null when it's a video (yt-dlp's job) or can't be read.
     * [anyLink]: read the page whatever the address says (yt-dlp found no video at it).
     */
    fun fetch(url: String, userAgent: String, anyLink: Boolean = false): Pair<String, Post>? = try {
        val page = if (isShortLink(url)) resolve(url) else url
        if (page == null || (!anyLink && !isPhotoLink(page))) null
        else download(page, userAgent)?.let(::parse)?.let { page to it }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read the TikTok post: ${e.message}")
        null
    }

    /** Where a share link leads; it answers with redirects (to the address TikTok's own crawler is given). */
    private fun resolve(url: String): String? {
        var current = url
        repeat(5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "HEAD"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "facebookexternalhit/1.1")
            }
            try {
                if (conn.responseCode !in 300..399) return current
                current = URL(URL(current), conn.getHeaderField("Location") ?: return null).toString()
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    private fun download(url: String, userAgent: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", userAgent)
        }
        return try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private val API_DATA = Regex("""<script[^>]*\bid="api-data"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
    private val REHYDRATION = Regex("""<script[^>]*\bid="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    /** The photo post on a TikTok page (mobile `api-data`, or the desktop page's data); null when it's not one. */
    fun parse(html: String): Post? {
        val item = API_DATA.find(html)?.let { json(it)?.path("videoDetail", "itemInfo", "itemStruct") }
            ?: REHYDRATION.find(html)?.let { json(it)?.path("__DEFAULT_SCOPE__", "webapp.video-detail", "itemInfo", "itemStruct") }
            ?: return null
        val images = item.optJSONObject("imagePost")?.optJSONArray("images") ?: return null
        val photos = (0 until images.length()).mapNotNull { i ->
            val image = images.optJSONObject(i) ?: return@mapNotNull null
            val url = image.optJSONObject("imageURL")?.optJSONArray("urlList")?.optString(0).orEmpty()
            if (!url.startsWith("http")) return@mapNotNull null
            Photo(url, image.optInt("imageWidth"), image.optInt("imageHeight"))
        }
        if (photos.isEmpty()) return null
        val music = item.optJSONObject("music")
        val song = music?.optString("playUrl")?.takeIf { it.startsWith("http") }?.let { url ->
            Song(
                url = url,
                cover = listOf("coverLarge", "coverMedium", "coverThumb").map { music.optString(it) }.firstOrNull { it.startsWith("http") }.orEmpty(),
                durationSec = music.optInt("duration").coerceAtLeast(0)
            )
        }
        val author = item.optJSONObject("author")
        return Post(
            title = item.optJSONObject("imagePost")?.optString("title").orEmpty().trim().ifBlank { item.optString("desc").trim() },
            uploader = author?.optString("nickname").orEmpty().ifBlank { author?.optString("uniqueId").orEmpty() },
            photos = photos,
            song = song
        )
    }

    private fun json(match: MatchResult): JSONObject? = runCatching { JSONObject(match.groupValues[1]) }.getOrNull()

    private fun JSONObject.path(vararg keys: String): JSONObject? =
        keys.fold(this as JSONObject?) { o, key -> o?.optJSONObject(key) }

    /**
     * The post as entries to pick from: each photo a slide, then the song as the last one
     * (downloaded straight from TikTok's server, with the page as its referrer).
     */
    fun Post.toFormats(pageUrl: String, userAgent: String): List<FormatInfo> {
        val images = photos.mapIndexed { i, p ->
            FormatInfo(
                id = "",
                extension = YtDlpFormatParser.imageExtFromUrl(p.url),
                resolution = if (photos.size > 1) "Image ${i + 1}/${photos.size}" else "Image (photo)",
                filesize = 0L,
                vcodec = "none",
                acodec = "none",
                isImage = true,
                imageUrl = p.url,
                slide = i,
                previewUrl = p.url
            )
        }
        val audio = song?.let { s ->
            FormatInfo(
                id = "tiktok:song",
                extension = "m4a",
                resolution = "Audio",
                filesize = 0L,
                vcodec = "none",
                acodec = "page",
                isAudioOnly = true,
                directUrl = s.url,
                userAgent = userAgent,
                referer = pageUrl,
                slide = photos.size,
                previewUrl = s.cover,
                durationSec = s.durationSec
            )
        }
        return images + listOfNotNull(audio)
    }
}
