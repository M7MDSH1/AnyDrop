package com.anydrop.app.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.anydrop.app.R
import com.anydrop.app.data.model.DownloadState
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.model.MediaSource
import com.anydrop.app.data.repository.SettingsRepository
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDL.CanceledException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class YtDlpEngine(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    private var ffmpegDir: File? = null

    /** Running yt-dlp process per download (keyed by output path), so STOP hits the right one. */
    private val running = ConcurrentHashMap<String, String>()
    private val cancelled: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    private val webExtractor = WebVideoExtractor(context)

    companion object {
        private const val TAG = "YtDlpEngine"
        private const val UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
        private val VIDEO_EXTS = listOf("mp4", "mkv", "webm", "mov", "m4v")
        private val AUDIO_EXTS = listOf("m4a", "mp3", "opus", "aac", "ogg", "webm")
        /** The most videos listed from one playlist. */
        private const val MAX_PLAYLIST = 200
        /** [FormatInfo.group] prefix of a playlist's videos ("playlist:" + the video's link). */
        const val PLAYLIST_GROUP = "playlist:"
        /** [FormatInfo.group] of the page's own video in whole-page results. */
        const val MAIN_GROUP = "main"

        /** Same web address, ignoring the scheme, "www.", a trailing slash and #fragment. */
        fun sameAddress(a: String, b: String): Boolean {
            fun norm(u: String) = u.trim().substringBefore('#').removePrefix("https://").removePrefix("http://")
                .removePrefix("www.").removePrefix("m.").trimEnd('/')
            return a.isNotBlank() && norm(a) == norm(b)
        }
    }

    data class FormatInfo(
        val id: String,
        val extension: String,
        val resolution: String,
        val filesize: Long,
        val vcodec: String,
        val acodec: String,
        val isAudioOnly: Boolean = false,
        val abr: Int = 0, // audio bitrate (kbps), for audio-only entries
        val isImage: Boolean = false,
        val imageUrl: String = "", // direct image URL when derived from a thumbnail (no yt-dlp format id)
        /** Media file found on the web page itself (no login needed); downloaded with [downloadDirect]. */
        val directUrl: String = "",
        /** Browser identity used to find [directUrl]; the download reuses it so the CDN sees the same client. */
        val userAgent: String = "",
        /** Position in a multi-photo/video post (carousel), or -1 for a single-media post. */
        val slide: Int = -1,
        /** Picture to show for this entry in pickers (photo itself, or a video's cover). */
        val previewUrl: String = "",
        /** Length in seconds, 0 when unknown. */
        val durationSec: Int = 0,
        /** This entry's own title when a page holds several different videos; blank = the page's title. */
        val title: String = "",
        /** Whole-page results: the entries of one video share this key. Blank elsewhere. */
        val group: String = "",
        /** A video linked from the page (e.g. a suggestion under the player); it downloads from this page. */
        val pageUrl: String = ""
    ) {
        val isFromPage: Boolean get() = directUrl.isNotBlank()

        /** A linked video whose qualities aren't known yet; the best one is picked when it downloads. */
        val isLink: Boolean get() = vcodec == LINK_CODEC

        /** Files of a torrent (magnet link or .torrent file); [id] is "torrent:" + their indexes. */
        val isTorrent: Boolean get() = vcodec == TORRENT_CODEC

        /** Which of the torrent's files this entry downloads. */
        val torrentFiles: Set<Int>
            get() = if (isTorrent) id.removePrefix(TORRENT_ID).split(',').mapNotNull { it.trim().toIntOrNull() }.toSet() else emptySet()

        companion object {
            const val LINK_CODEC = "link"
            const val TORRENT_CODEC = "torrent"
            const val TORRENT_ID = "torrent:"
        }
    }

    /** What a fetch found, plus the page's own details (empty when the site gave none). */
    data class FetchResult(
        val formats: List<FormatInfo>,
        val title: String = "",
        val uploader: String = "",
        val webpageUrl: String = "",
        /**
         * yt-dlp's catch-all extractor found no video, only the page's picture (as on a page
         * with several players): worth reading the page itself before settling for that.
         */
        val coverOnly: Boolean = false
    )

    fun init() {
        YoutubeDL.getInstance().init(context)
        // Initialize the official FFmpeg native lib (installs a real executable in the app's
        // native lib dir and registers it with yt-dlp).
        try {
            com.yausername.ffmpeg.FFmpeg.getInstance().init(context)
            Log.i(TAG, "FFmpeg initialized")
        } catch (e: Exception) {
            Log.w(TAG, "FFmpeg init failed", e)
        }
        Thread {
            Log.i(TAG, "ffmpeg usable on this device: $ffmpegUsable")
            try {
                // Only check for a nightly update once a day — keeps cold starts
                // cheap instead of hitting the network on every launch.
                val last = settingsRepository.lastYtDlpUpdateCheck()
                if (System.currentTimeMillis() - last >= UPDATE_CHECK_INTERVAL_MS) {
                    Log.i(TAG, "Checking for yt-dlp update...")
                    YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
                    settingsRepository.markYtDlpUpdateCheck()
                } else {
                    Log.i(TAG, "yt-dlp update check skipped (checked recently)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp update failed (non-fatal)", e)
            }
        }.apply { isDaemon = true }.start()
    }

    /**
     * Whether the bundled ffmpeg actually runs here. It's built for 4 KB memory pages, so it
     * can't load on phones with 16 KB pages (newer devices); yt-dlp then silently skips
     * merging/converting. When false we avoid needing it: merges use [MediaMuxerUtil] (already
     * the case) and audio is kept as the original AAC stream instead of being converted.
     */
    val ffmpegUsable: Boolean by lazy {
        try {
            val bin = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
            val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
            val process = ProcessBuilder(bin.absolutePath, "-hide_banner", "-version")
                .redirectErrorStream(true)
                .apply {
                    environment()["LD_LIBRARY_PATH"] = listOf("python", "ffmpeg", "aria2c")
                        .joinToString(":") { File(packages, "$it/usr/lib").absolutePath }
                }
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val ok = process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0 && output.contains("ffmpeg version")
            if (!ok) Log.w(TAG, "ffmpeg can't run here: ${output.take(200)}")
            ok
        } catch (e: Exception) {
            Log.w(TAG, "ffmpeg probe failed", e)
            false
        }
    }

    /**
     * YouTube is currently in an anti-bot arms race: it rotates which player
     * client works from week to week, and split (video-only/audio-only) formats
     * get 403'd far more than single progressive files. We try clients in order
     * and fall back automatically instead of hardcoding one that will go stale.
     */
    private val youtubeClientFallbackOrder = listOf("android", "tv", "web_creator", "mweb", "web")

    private fun hostOf(url: String): String =
        runCatching { Uri.parse(url.trim()).host?.lowercase() }.getOrNull().orEmpty()

    private fun String.isOn(vararg domains: String) = domains.any { this == it || endsWith(".$it") }

    private fun isYoutubeUrl(url: String): Boolean = hostOf(url).isOn("youtube.com", "youtu.be", "youtube-nocookie.com")

    private fun isTiktokUrl(url: String): Boolean = hostOf(url).isOn("tiktok.com")

    /**
     * Applies the shared extractor/client args used for BOTH info fetch and download.
     * IMPORTANT: do NOT add manual Origin/Referer headers here — googlevideo URLs
     * signed for the android/tv player clients will 403 if those headers are present.
     */
    private fun YoutubeDLRequest.applyCommonOptions(
        cookieFile: File?,
        youtubeClient: String? = null,
        isTiktok: Boolean = false,
        url: String? = null,
        mobileAgent: Boolean = true
    ) {
        addOption("--no-update")
        // A link to a video inside a playlist (…watch?v=…&list=…) means that video: without
        // this, yt-dlp would fetch or download the whole list.
        if (url != null && Playlists.hasList(url)) addOption("--no-playlist")
        if (isTiktok) {
            // Current yt-dlp TikTok extractor ONLY tries its mobile API (which needs
            // no TLS impersonation) when a runtime config arg is present; otherwise it
            // goes straight to the webpage path, which demands curl_cffi
            // (--impersonate) that youtubedl-android doesn't ship — that's the
            // "blocks non-browser downloaders" failure. app_info uses the extractor's
            // own current known-good app build (kept up to date by the nightly update);
            // on API failure the extractor still falls back to the webpage internally.
            // No manual UA/Referer/Origin headers here: the API sets its own app UA,
            // and extraneous headers aimed at the tiktokv.com API can trip its WAF.
            addOption("--extractor-args", "tiktok:app_info=/musical_ly/35.1.3/2023501030/0")
        } else if (mobileAgent) {
            addOption("--user-agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.127 Mobile Safari/537.36")
        }
        addOption("--extractor-retries", "5")
        addOption("--retries", "5")
        // One progress line per update (the library parses them for the tracker).
        addOption("--newline")
        if (youtubeClient != null) {
            addOption("--extractor-args", "youtube:player_client=$youtubeClient")
            addOption("--extractor-args", "youtube:skip=webpage")
        }
        cookieFile?.let { addOption("--cookies", it.absolutePath) }
    }

    private fun Exception.is403(): Boolean = (message ?: "").contains("403")

    /**
     * Some sites (e.g. Cloudflare / TLS-fingerprint protected ones) can only be
     * reached with yt-dlp's --impersonate, which needs the curl_cffi native
     * backend. youtubedl-android does not ship that backend, so these sites are
     * unsupported. Detect the tell-tale yt-dlp message so we can show the user a
     * clear reason instead of a bare "403 Forbidden".
     */
    private fun Exception.isImpersonationUnavailable(): Boolean {
        val m = message ?: ""
        return m.contains("no impersonate target is available") ||
            m.contains("attempting impersonation")
    }

    class UnsupportedSiteException(message: String) : Exception(message)

    // ── Format discovery ─────────────────────────────────────────────────────

    /**
     * Two independent engines:
     *  1. yt-dlp (all qualities for 1800+ sites);
     *  2. the web extractor — a hidden browser that reads the media straight from the page.
     *     It needs no account, which is what makes Instagram work without logging in.
     * Instagram/Threads use the page first, logged in or not: only the page lists every
     * photo and video of a carousel (yt-dlp returns the first), and with the in-app login
     * the page is the logged-in one. When the page shows nothing (a private post seen
     * anonymously) yt-dlp with the saved cookies takes over. Everything else tries yt-dlp
     * first and falls back to the page when yt-dlp can't get it.
     */
    suspend fun getFormats(url: String, cookieFile: File? = null): FetchResult {
        val host = hostOf(url)
        val instagramLike = host.isOn("instagram.com", "instagr.am", "threads.net", "threads.com")
        val pageFirst = instagramLike

        if (pageFirst) {
            webFormats(url, desktop = true)?.let { return it }
        }

        var failure: Exception? = null
        val result = try {
            ytdlpFormats(url, cookieFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e
            FetchResult(emptyList())
        }
        if (result.formats.isNotEmpty() && !result.coverOnly) return result

        // yt-dlp came up empty (or with just the page's picture): look at the page like a
        // browser would. (Not for YouTube: its player streams are split and signed, the page
        // has nothing directly usable.)
        if (!pageFirst && !isYoutubeUrl(url)) {
            val desktop = instagramLike || host.isOn("facebook.com", "fb.watch", "fb.com")
            webFormats(url, desktop)?.let { return it }
            if (!instagramLike && !isTiktokUrl(url)) {
                // Some sites only put a <video> in one flavor of their page.
                webFormats(url, !desktop, timeoutMs = 15_000)?.let { return it }
            }
        }

        if (result.formats.isNotEmpty()) return result
        failure?.let { throw friendlyFailure(it) }
        return FetchResult(emptyList())
    }

    private fun usesDesktopPage(host: String) =
        host.isOn("instagram.com", "instagr.am", "threads.net", "threads.com", "facebook.com", "fb.watch", "fb.com", "tiktok.com")

    /**
     * Everything on the page, for "whole page" mode: the page's own video (all qualities, via
     * yt-dlp when it knows the site), every other video file the page plays, its photos (post
     * pages) and the videos it links to, like the suggestions under a player. Each video is one
     * [FormatInfo.group]; linked ones are only resolved when chosen ([FormatInfo.isLink]).
     */
    suspend fun getPageMedia(url: String, cookieFile: File? = null): FetchResult = coroutineScope {
        val host = hostOf(url)
        val pageOnly = host.isOn("instagram.com", "instagr.am", "threads.net", "threads.com")
        val main = async {
            if (pageOnly) null
            else try {
                ytdlpFormats(url, cookieFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Whole page: yt-dlp has nothing for the page itself", e)
                null
            }
        }
        val page = async {
            try {
                webExtractor.extract(url, usesDesktopPage(host), timeoutMs = 30_000, wholePage = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Whole page: web extraction failed", e)
                null
            }
        }
        // Just the page's picture from yt-dlp is no video; the page's own reading has the real ones.
        val mainResult = main.await()?.let { if (it.coverOnly) it.copy(formats = emptyList()) else it }
        val scan = page.await()

        // yt-dlp's title when it found the page's video; otherwise the page's own (a catch-all
        // extractor names its guesses "Title (1)").
        val title = mainResult?.takeIf { it.formats.isNotEmpty() }?.title?.ifBlank { null }
            ?: scan?.title?.ifBlank { null } ?: mainResult?.title.orEmpty()
        val mainVideo = mainResult?.formats.orEmpty()
            .filter { !it.isAudioOnly && !it.isImage }
            .map { it.copy(group = MAIN_GROUP, title = title) }
        val mainImages = mainResult?.formats.orEmpty()
            .filter { it.isImage }
            .mapIndexed { i, f -> f.copy(group = "img:$i", title = title, previewUrl = f.previewUrl.ifBlank { f.imageUrl }) }
        val onPage = scan?.toFormatInfos().orEmpty()
            .filter { !it.isAudioOnly }
            // yt-dlp already listed the page's photos; the page's copies would only repeat them.
            .filter { !(it.isImage && mainImages.isNotEmpty()) }
            .mapIndexed { i, f -> f.copy(group = if (f.isImage) "pimg:$i" else "page:$i") }
        val linked = scan?.links.orEmpty()
            .filter { link -> !sameAddress(link.url, url) && !sameAddress(link.url, scan?.pageUrl.orEmpty()) }
            .map { link ->
                FormatInfo(
                    id = "",
                    extension = "mp4",
                    resolution = "Best",
                    filesize = 0L,
                    vcodec = FormatInfo.LINK_CODEC,
                    acodec = FormatInfo.LINK_CODEC,
                    previewUrl = link.thumb.orEmpty(),
                    durationSec = link.duration,
                    title = link.title,
                    group = link.url,
                    pageUrl = link.url
                )
            }
        val all = mainVideo + mainImages + onPage + linked
        if (all.isEmpty() && mainResult == null && scan == null) {
            throw UnsupportedSiteException(context.getString(R.string.error_no_media_found))
        }
        FetchResult(
            formats = all,
            title = title,
            uploader = mainResult?.uploader?.ifBlank { null } ?: scan?.uploader.orEmpty(),
            webpageUrl = mainResult?.webpageUrl?.ifBlank { null } ?: scan?.pageUrl ?: url
        )
    }

    /**
     * The videos of a playlist (a YouTube playlist or mix, a channel's list…), listed without
     * opening each one: title, length and thumbnail. Each is a linked entry ([FormatInfo.isLink]),
     * so its qualities are looked up when it is picked or downloaded. Null when [url] isn't a
     * playlist (or yt-dlp can't list it).
     */
    suspend fun getPlaylist(url: String, cookieFile: File? = null): FetchResult? = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url)
        // yt-dlp's own desktop agent: YouTube's mobile pages leave a mix's videos out.
        request.applyCommonOptions(cookieFile, isTiktok = isTiktokUrl(url), mobileAgent = false)
        request.addOption("--flat-playlist")
        request.addOption("-J")
        request.addOption("--no-warnings")
        // Mixes never end; a few hundred videos is plenty to pick from.
        request.addOption("--playlist-end", MAX_PLAYLIST.toString())
        val out = try {
            YoutubeDL.getInstance().execute(request).out
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't list the playlist", e)
            return@withContext null
        }
        val playlist = Playlists.parse(out)?.takeIf { it.entries.isNotEmpty() } ?: return@withContext null
        Log.i(TAG, "Playlist \"${playlist.title}\": ${playlist.entries.size} videos")
        FetchResult(
            formats = playlist.entries.map { e ->
                FormatInfo(
                    id = "",
                    extension = "mp4",
                    resolution = "Best",
                    filesize = 0L,
                    vcodec = FormatInfo.LINK_CODEC,
                    acodec = FormatInfo.LINK_CODEC,
                    previewUrl = e.thumbnail.orEmpty(),
                    durationSec = e.durationSec,
                    title = e.title,
                    group = PLAYLIST_GROUP + e.url,
                    pageUrl = e.url
                )
            },
            title = playlist.title,
            uploader = playlist.uploader,
            webpageUrl = url
        )
    }

    /** Fills in length, real resolution and a cover for media found on [pageUrl] (see [MediaProbe]). */
    suspend fun probePageMedia(formats: List<FormatInfo>, pageUrl: String): List<FormatInfo> =
        MediaProbe.fill(context, formats, pageUrl)

    /** Turns raw yt-dlp errors into something a person can act on. */
    private fun friendlyFailure(e: Exception): Exception {
        val m = e.message.orEmpty()
        return when {
            listOf("ip address is blocked", "not available in your country", "geo restrict", "blocked in your country", "not available in your region")
                .any { m.contains(it, ignoreCase = true) } ->
                UnsupportedSiteException(context.getString(R.string.error_region_blocked))
            e is UnsupportedSiteException && e.isLoginMessage ->
                UnsupportedSiteException(context.getString(R.string.error_private_post))
            e is UnsupportedSiteException -> e
            listOf("unable to download webpage", "timed out", "network is unreachable", "failed to resolve", "connection reset")
                .any { m.contains(it, ignoreCase = true) } ->
                UnsupportedSiteException(context.getString(R.string.error_network))
            else -> UnsupportedSiteException(context.getString(R.string.error_no_media_found))
        }
    }

    private val Exception.isLoginMessage: Boolean get() = message == loginRequiredMessage
    private val loginRequiredMessage = "login-required"

    private suspend fun webFormats(url: String, desktop: Boolean, timeoutMs: Long = 25_000): FetchResult? {
        val scan = try {
            webExtractor.extract(url, desktop, timeoutMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Web extraction failed", e)
            return null
        }
        if (scan.videos.isEmpty() && scan.images.isEmpty()) return null
        Log.i(TAG, "Found on page: ${scan.videos.size} video(s), ${scan.images.size} image(s)")
        return FetchResult(scan.toFormatInfos(), scan.title, scan.uploader, scan.pageUrl)
    }

    private suspend fun ytdlpFormats(url: String, cookieFile: File? = null): FetchResult {
        return withContext(Dispatchers.IO) {
            val isTiktok = isTiktokUrl(url)
            val clients = if (isYoutubeUrl(url)) youtubeClientFallbackOrder else listOf<String?>(null)

            for ((index, client) in clients.withIndex()) {
                val request = YoutubeDLRequest(url)
                request.applyCommonOptions(cookieFile, client, isTiktok = isTiktok, url = url)
                try {
                    val info = YoutubeDL.getInstance().getInfo(request)
                    val title = info.title?.trim().orEmpty()
                    val uploader = runCatching { info.uploader?.trim().orEmpty() }.getOrDefault("")
                    val webpageUrl = runCatching { info.webpageUrl?.trim().orEmpty() }.getOrDefault("")
                    val cover = runCatching { info.thumbnail?.trim().orEmpty() }.getOrDefault("")
                    val duration = runCatching { info.duration }.getOrDefault(0).coerceAtLeast(0)
                    val raw = info.formats?.filter { it.formatId != null } ?: emptyList()

                    fun hasVideo(vc: String?) = !vc.isNullOrEmpty() && vc != "none"
                    fun hasAudio(ac: String?) = !ac.isNullOrEmpty() && ac != "none"

                    // Video formats (anything with a video codec).
                    val videoFormats = raw
                        .filter { hasVideo(it.vcodec) }
                        .map { format ->
                            val height = format.height
                            val fps = if (format.fps > 30) format.fps.toString() else ""
                            val resolution = format.formatNote
                                ?: if (height != null && height > 0) "${height}p$fps" else "N/A"
                            FormatInfo(
                                id = format.formatId ?: "",
                                extension = format.ext ?: "",
                                resolution = resolution,
                                filesize = format.fileSize.coerceAtLeast(format.fileSizeApproximate),
                                vcodec = format.vcodec ?: "",
                                acodec = format.acodec ?: ""
                            )
                        }
                        .distinctBy { it.resolution }
                        .sortedByDescending { YtDlpFormatParser.extractHeight(it.resolution) }

                    // Audio-only formats (video codec none, has an audio codec). Present
                    // them as selectable "Audio • Nkbps" entries alongside the video ones.
                    val audioFormats = raw
                        .filter { !hasVideo(it.vcodec) && hasAudio(it.acodec) }
                        .map { format ->
                            val abr = format.abr.toInt()
                            FormatInfo(
                                id = format.formatId ?: "",
                                extension = format.ext ?: "m4a",
                                resolution = if (abr > 0) "Audio ${abr}kbps" else "Audio",
                                filesize = format.fileSize.coerceAtLeast(format.fileSizeApproximate),
                                vcodec = "none",
                                acodec = format.acodec ?: "",
                                isAudioOnly = true,
                                abr = abr
                            )
                        }
                        .filter { it.abr > 0 }
                        .distinctBy { it.abr }
                        .sortedByDescending { it.abr }

                    // Image formats (no video AND no audio codec, image extension).
                    // Instagram photos, X/Twitter image tweets, and TikTok photo posts
                    // surface here. yt-dlp downloads these directly (no ffmpeg needed).
                    val imageExts = listOf("jpg", "jpeg", "png", "webp", "heic", "gif")
                    val imageFormats = raw
                        .filter { f ->
                            !hasVideo(f.vcodec) && !hasAudio(f.acodec) &&
                                (f.ext?.lowercase() in imageExts)
                        }
                        .mapIndexed { i, format ->
                            val w = format.width ?: 0
                            val h = format.height ?: 0
                            val label = if (w > 0 && h > 0) "Image ${w}x${h}" else "Image ${i + 1}"
                            FormatInfo(
                                id = format.formatId ?: "",
                                extension = format.ext ?: "jpg",
                                resolution = label,
                                filesize = format.fileSize.coerceAtLeast(format.fileSizeApproximate),
                                vcodec = "none",
                                acodec = "none",
                                isImage = true
                            )
                        }
                        .sortedByDescending { YtDlpFormatParser.extractHeight(it.resolution) }

                    // If there were no real formats but the post has a thumbnail/image,
                    // derive an image entry from it (Instagram/TikTok photo posts often
                    // expose the picture only via `thumbnails`, not `formats`).
                    val thumbImages = if (videoFormats.isEmpty() && audioFormats.isEmpty() && imageFormats.isEmpty()) {
                        carouselImagesFromJson(url, cookieFile, client, isTiktok)
                            .ifEmpty { imageEntriesFromThumbnail(info) }
                    } else emptyList()

                    // The video's cover and length go on its entries, so pickers can show them.
                    val formats = (videoFormats + audioFormats).map { it.copy(previewUrl = cover, durationSec = duration) } +
                        imageFormats + thumbImages
                    // yt-dlp's catch-all extractors (any page with a player) vs. a site's own one.
                    val generic = runCatching { info.extractorKey }.getOrNull()
                        .let { it.isNullOrBlank() || it.contains("Generic", ignoreCase = true) || it == "HTML5MediaEmbed" }
                    Log.i(TAG, "yt-dlp (${runCatching { info.extractorKey }.getOrNull()}): ${videoFormats.size} video, ${audioFormats.size} audio, ${imageFormats.size} image, ${thumbImages.size} from thumbnails")
                    if (formats.isNotEmpty()) {
                        return@withContext FetchResult(formats, title, uploader, webpageUrl, coverOnly = thumbImages.isNotEmpty() && generic)
                    }
                    Log.w(TAG, "Client '$client' returned no usable formats, trying next")
                } catch (e: Exception) {
                    Log.w(TAG, "getFormats failed with client '$client' (${index + 1}/${clients.size})", e)
                    if (e.isImpersonationUnavailable()) {
                        throw UnsupportedSiteException(context.getString(R.string.error_site_unsupported))
                    }
                    if (e.needsLogin()) {
                        // Caller tries the page-reading engine before this reaches the user.
                        throw UnsupportedSiteException(loginRequiredMessage)
                    }
                    if (e.isNoVideoFormats()) {
                        val imgs = imageFallbackViaThumbnails(url, cookieFile, client, isTiktok)
                        if (imgs.isNotEmpty()) return@withContext FetchResult(imgs)
                    }
                    if (clients.size == 1) throw e
                }
            }
            FetchResult(emptyList())
        }
    }

    private fun Exception.isNoVideoFormats(): Boolean {
        val m = message ?: ""
        return m.contains("No video formats found", ignoreCase = true) ||
            m.contains("Unsupported URL", ignoreCase = true) ||
            m.contains("There's no video", ignoreCase = true)
    }

    /**
     * True when the site says the content requires being logged in (cookies).
     * Most common on Instagram now, which returns an "empty media response" for
     * logged-out requests.
     */
    private fun Exception.needsLogin(): Boolean {
        val m = message ?: ""
        return m.contains("empty media response", ignoreCase = true) ||
            m.contains("login required", ignoreCase = true) ||
            m.contains("Requested content is not available", ignoreCase = true) ||
            m.contains("use --cookies", ignoreCase = true) ||
            m.contains("rate-limit reached", ignoreCase = true) ||
            m.contains("Restricted Video", ignoreCase = true)
    }

    /**
     * Build image FormatInfo entries from a VideoInfo's thumbnail(s). Used when a post
     * is a photo (no playable formats). Returns the largest available thumbnail(s).
     */
    private fun imageEntriesFromThumbnail(info: com.yausername.youtubedl_android.mapper.VideoInfo): List<FormatInfo> {
        val urls = mutableListOf<String>()
        info.thumbnails?.forEach { t -> t.url?.let { if (it.isNotBlank()) urls.add(it) } }
        info.thumbnail?.let { if (it.isNotBlank() && it !in urls) urls.add(it) }
        val distinct = urls.distinct()
        if (distinct.isEmpty()) return emptyList()
        // If the post exposes multiple distinct images (a carousel/slideshow),
        // surface EACH as its own selectable entry so the user can pick which to
        // download. Otherwise a single "Image (photo)" entry.
        if (distinct.size > 1) {
            return distinct.mapIndexed { i, u ->
                FormatInfo(
                    id = "",
                    extension = YtDlpFormatParser.imageExtFromUrl(u),
                    resolution = "Image ${i + 1}/${distinct.size}",
                    filesize = 0L,
                    vcodec = "none",
                    acodec = "none",
                    isImage = true,
                    imageUrl = u
                )
            }
        }
        val best = distinct.last()
        return listOf(
            FormatInfo(
                id = "",
                extension = YtDlpFormatParser.imageExtFromUrl(best),
                resolution = "Image (photo)",
                filesize = 0L,
                vcodec = "none",
                acodec = "none",
                isImage = true,
                imageUrl = best
            )
        )
    }

    /**
     * Second-chance info pass for image posts: ask yt-dlp to skip the download and
     * dump metadata so we can read thumbnails even when the default extract path threw.
     */
    private fun imageFallbackViaThumbnails(
        url: String,
        cookieFile: File?,
        client: String?,
        isTiktok: Boolean
    ): List<FormatInfo> {
        // First try the full-JSON carousel path (gets ALL slides), then fall back to
        // the single-thumbnail path if that yields nothing.
        val carousel = carouselImagesFromJson(url, cookieFile, client, isTiktok)
        if (carousel.isNotEmpty()) return carousel
        return try {
            val request = YoutubeDLRequest(url)
            request.applyCommonOptions(cookieFile, client, isTiktok = isTiktok, url = url)
            request.addOption("--ignore-no-formats-error")
            request.addOption("--skip-download")
            val info = YoutubeDL.getInstance().getInfo(request)
            imageEntriesFromThumbnail(info)
        } catch (e: Exception) {
            Log.w(TAG, "Image thumbnail fallback failed", e)
            emptyList()
        }
    }

    /**
     * Dump the full JSON (-J) and parse the playlist `entries` array to extract EVERY
     * image of a carousel/slideshow (Instagram/TikTok multi-image posts). The typed
     * VideoInfo mapper doesn't expose entries, so we parse the raw stdout ourselves.
     */
    private fun carouselImagesFromJson(
        url: String,
        cookieFile: File?,
        client: String?,
        isTiktok: Boolean
    ): List<FormatInfo> {
        return try {
            val request = YoutubeDLRequest(url)
            request.applyCommonOptions(cookieFile, client, isTiktok = isTiktok, url = url)
            request.addOption("-J")                    // dump single JSON (whole playlist)
            request.addOption("--ignore-no-formats-error")
            request.addOption("--no-warnings")
            request.addOption("--skip-download")
            val response = YoutubeDL.getInstance().execute(request)
            YtDlpFormatParser.parseCarouselImages(response.out.trim())
        } catch (e: Exception) {
            Log.w(TAG, "Carousel JSON parse failed", e)
            emptyList()
        }
    }

    // ── Downloads ────────────────────────────────────────────────────────────

    fun download(
        url: String,
        outputPath: String,
        formatId: String = "bestvideo+bestaudio/best",
        cookieFile: File? = null
    ): Flow<DownloadState> = callbackFlow {
        val tag = outputPath
        cancelled.remove(tag)
        trySend(DownloadState.Progress(0, phase = context.getString(R.string.progress_starting)))

        val isYt = isYoutubeUrl(url)
        val isTiktok = isTiktokUrl(url)
        // YouTube and TikTok both benefit from retrying on 403/blocked-client failures.
        val needsResilientRetry = isYt || isTiktok

        val clients = if (isYt) youtubeClientFallbackOrder else listOf<String?>(null)
        // If the requested format is a split video+audio combo, add a plain
        // "best" progressive fallback at the end — YouTube 403s split streams
        // far more often than single progressive files right now. TikTok URLs
        // are usually progressive-only, so give them a "best" fallback too.
        val formatsToTry = when {
            isYt && formatId.contains("+") -> listOf(formatId, "best")
            isTiktok -> listOf(formatId, "best").distinct()
            else -> listOf(formatId)
        }

        var lastError: String = context.getString(R.string.error_download_failed)
        var succeeded = false

        outer@ for (fmt in formatsToTry) {
            for ((index, client) in clients.withIndex()) {
                if (succeeded) break@outer
                if (tag in cancelled) break@outer

                val request = YoutubeDLRequest(url)
                request.applyCommonOptions(cookieFile, client, isTiktok = isTiktok, url = url)
                request.addOption("-f", fmt)
                request.addOption("-o", outputPath)

                try {
                    val progress = ProgressMapper()
                    runTracked(tag, request) { p, eta, line -> progress.map(p, eta, line)?.let { trySend(it) } }

                    val outputDir = File(outputPath).parentFile
                    val expectedFile = File(outputPath)
                    val actualFile = if (expectedFile.exists()) {
                        expectedFile
                    } else {
                        outputDir?.listFiles { f ->
                            f.extension in listOf("mp4", "mkv", "webm")
                        }?.maxByOrNull { it.lastModified() }
                    }

                    if (actualFile != null && actualFile.exists()) {
                        trySend(
                            DownloadState.Completed(
                                MediaItem(
                                    id = actualFile.nameWithoutExtension,
                                    title = actualFile.nameWithoutExtension,
                                    url = url,
                                    localFilePath = actualFile.absolutePath,
                                    source = url.detectSource()
                                )
                            )
                        )
                        succeeded = true
                    } else {
                        lastError = context.getString(R.string.error_output_not_found)
                    }
                } catch (e: CanceledException) {
                    break@outer
                } catch (e: Exception) {
                    lastError = friendlyDownloadError(e)
                    Log.w(TAG, "Download failed with client '$client', format '$fmt' (${index + 1}/${clients.size})", e)
                    // Only worth retrying if this looks like a 403/blocked-client
                    // issue, not a genuine bad URL or no-space error. For TikTok there
                    // is only one client entry, so this break just moves on to the
                    // next format (e.g. the "best" progressive fallback).
                    if (!needsResilientRetry || !e.is403()) break
                }
            }
        }

        finish(tag, succeeded, lastError)
    }

    /**
     * Audio-only download. With a working ffmpeg the best audio is extracted to .m4a; without
     * one (16 KB-page phones) the original AAC stream is saved as-is, which needs no conversion.
     * The outputPath should end in ".m4a"; yt-dlp writes the file next to it.
     */
    fun downloadAudio(
        url: String,
        outputPath: String,
        cookieFile: File? = null
    ): Flow<DownloadState> = callbackFlow {
        val tag = outputPath
        cancelled.remove(tag)
        trySend(DownloadState.Progress(0, phase = context.getString(R.string.progress_starting)))

        val isYt = isYoutubeUrl(url)
        val isTiktok = isTiktokUrl(url)
        val needsResilientRetry = isYt || isTiktok

        val clients = if (isYt) youtubeClientFallbackOrder else listOf<String?>(null)
        // yt-dlp replaces the extension with the extracted audio format's, so pass
        // the path without extension as the -o template base. Strip only the real
        // trailing extension (titles may contain dots, e.g. "Video 2.0").
        val outBase = File(outputPath).let { f ->
            File(f.parentFile, f.nameWithoutExtension).absolutePath
        }

        var lastError = context.getString(R.string.error_download_failed)
        var succeeded = false

        for ((index, client) in clients.withIndex()) {
            if (succeeded || tag in cancelled) break

            val request = YoutubeDLRequest(url)
            request.applyCommonOptions(cookieFile, client, isTiktok = isTiktok, url = url)
            if (ffmpegUsable) {
                request.addOption("-f", "bestaudio/best")
                request.addOption("-x")                       // extract audio
                request.addOption("--audio-format", "m4a")    // container ffmpeg can produce
                request.addOption("--audio-quality", "0")     // best
            } else {
                request.addOption("-f", "bestaudio[ext=m4a]/bestaudio")
            }
            request.addOption("-o", "$outBase.%(ext)s")

            try {
                val progress = ProgressMapper()
                runTracked(tag, request) { p, eta, line -> progress.map(p, eta, line)?.let { trySend(it) } }

                val outputDir = File(outputPath).parentFile
                val actualFile = outputDir?.listFiles { f ->
                    f.nameWithoutExtension == File(outBase).name && f.extension in AUDIO_EXTS
                }?.maxByOrNull { it.lastModified() }

                if (actualFile != null && actualFile.exists()) {
                    trySend(
                        DownloadState.Completed(
                            MediaItem(
                                id = actualFile.nameWithoutExtension,
                                title = actualFile.nameWithoutExtension,
                                url = url,
                                localFilePath = actualFile.absolutePath,
                                source = url.detectSource(),
                                isAudio = true
                            )
                        )
                    )
                    succeeded = true
                } else {
                    lastError = context.getString(R.string.error_output_not_found)
                }
            } catch (e: CanceledException) {
                break
            } catch (e: Exception) {
                lastError = friendlyDownloadError(e)
                Log.w(TAG, "Audio download failed with client '$client' (${index + 1}/${clients.size})", e)
                if (e.isImpersonationUnavailable()) {
                    lastError = context.getString(R.string.error_site_unsupported)
                    break
                }
                if (!needsResilientRetry || !e.is403()) break
            }
        }

        finish(tag, succeeded, lastError)
    }

    /**
     * Image download: for photo posts (Instagram photos, X/Twitter image tweets,
     * TikTok photo posts). yt-dlp writes the image file directly — no ffmpeg. The
     * formatId is the specific image format the user picked (or "best" as fallback).
     */
    fun downloadImage(
        url: String,
        outputPath: String,
        formatId: String,
        cookieFile: File? = null,
        directImageUrl: String = ""
    ): Flow<DownloadState> = callbackFlow {
        trySend(DownloadState.Progress(0, phase = context.getString(R.string.progress_starting)))

        // If we have a direct image URL (from the page or a thumbnail), fetch it over HTTP
        // instead of going through yt-dlp format ids.
        if (directImageUrl.isNotBlank()) {
            try {
                val ext = YtDlpFormatParser.imageExtFromUrl(directImageUrl)
                val outFile = File(outputPath.substringBeforeLast(".") + "." + ext)
                java.net.URL(directImageUrl).openStream().use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (outFile.exists() && outFile.length() > 0) {
                    trySend(
                        DownloadState.Completed(
                            MediaItem(
                                id = outFile.nameWithoutExtension,
                                title = outFile.nameWithoutExtension,
                                url = url,
                                localFilePath = outFile.absolutePath,
                                source = url.detectSource(),
                                isImage = true
                            )
                        )
                    )
                } else {
                    trySend(DownloadState.Error(context.getString(R.string.error_save_image)))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct image download failed", e)
                trySend(DownloadState.Error(e.message ?: context.getString(R.string.error_image_download)))
            }
            close()
            awaitClose { }
            return@callbackFlow
        }

        val tag = outputPath
        cancelled.remove(tag)
        val isYt = isYoutubeUrl(url)
        val isTiktok = isTiktokUrl(url)
        val needsResilientRetry = isYt || isTiktok

        val clients = if (isYt) youtubeClientFallbackOrder else listOf<String?>(null)
        // Strip only the real trailing extension (titles may contain dots).
        val outBase = File(outputPath).let { f ->
            File(f.parentFile, f.nameWithoutExtension).absolutePath
        }

        var lastError = context.getString(R.string.error_image_download)
        var succeeded = false

        for ((index, client) in clients.withIndex()) {
            if (succeeded || tag in cancelled) break

            val request = YoutubeDLRequest(url)
            request.applyCommonOptions(cookieFile, client, isTiktok = isTiktok, url = url)
            request.addOption("-f", formatId.ifBlank { "best" })
            request.addOption("-o", "$outBase.%(ext)s")

            try {
                val progress = ProgressMapper()
                runTracked(tag, request) { p, eta, line -> progress.map(p, eta, line)?.let { trySend(it) } }

                val outputDir = File(outputPath).parentFile
                val actualFile = outputDir?.listFiles { f ->
                    f.nameWithoutExtension == File(outBase).name &&
                        f.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "heic", "gif")
                }?.maxByOrNull { it.lastModified() }

                if (actualFile != null && actualFile.exists()) {
                    trySend(
                        DownloadState.Completed(
                            MediaItem(
                                id = actualFile.nameWithoutExtension,
                                title = actualFile.nameWithoutExtension,
                                url = url,
                                localFilePath = actualFile.absolutePath,
                                source = url.detectSource(),
                                isImage = true
                            )
                        )
                    )
                    succeeded = true
                } else {
                    lastError = context.getString(R.string.error_output_not_found)
                }
            } catch (e: CanceledException) {
                break
            } catch (e: Exception) {
                lastError = friendlyDownloadError(e)
                Log.w(TAG, "Image download failed with client '$client' (${index + 1}/${clients.size})", e)
                if (e.isImpersonationUnavailable()) {
                    lastError = context.getString(R.string.error_site_unsupported)
                    break
                }
                if (!needsResilientRetry || !e.is403()) break
            }
        }

        finish(tag, succeeded, lastError)
    }

    fun downloadWithMerge(
        url: String,
        outputPath: String,
        formatId: String,
        height: Int,
        cookieFile: File? = null
    ): Flow<DownloadState> = callbackFlow {
        val tag = outputPath
        cancelled.remove(tag)
        trySend(DownloadState.Progress(0, phase = context.getString(R.string.progress_starting)))

        val downloadDir = File(outputPath).parentFile ?: File(outputPath)
        val timestamp = System.currentTimeMillis()
        val tempVideoPath = File(downloadDir, ".temp_video_$timestamp.mp4").absolutePath
        val tempAudioPath = File(downloadDir, ".temp_audio_$timestamp.m4a").absolutePath

        try {
            // Video is ~85% of the bytes, audio the rest; each phase gets its share of the bar.
            val videoPhase = context.getString(R.string.progress_downloading_video)
            val videoProgress = ProgressMapper(phase = videoPhase, from = 0, to = 85)
            trySend(DownloadState.Progress(0, phase = videoPhase))
            // The format the user picked first; H.264 at that height as a fallback because
            // Android's own muxer can always put it in an MP4.
            val videoSelector = listOfNotNull(
                formatId.takeIf { it.isNotBlank() && !it.contains("+") && !it.contains("/") },
                "bestvideo[height<=$height][vcodec~='^(avc|h264)']",
                "bestvideo[height<=$height]"
            ).joinToString("/")
            var videoFile = downloadRaw(tag, url, videoSelector, tempVideoPath, cookieFile) { p, eta, line ->
                videoProgress.map(p, eta, line)?.let { trySend(it) }
            }
            if ((videoFile == null || !videoFile.exists()) && tag !in cancelled) {
                trySend(DownloadState.Progress(0, phase = context.getString(R.string.progress_retrying_video)))
                videoFile = downloadRaw(tag, url, "bestvideo", tempVideoPath, cookieFile) { p, eta, line ->
                    videoProgress.map(p, eta, line)?.let { trySend(it) }
                }
            }
            if (tag in cancelled) {
                videoFile?.delete()
                finish(tag, false, "")
                return@callbackFlow
            }
            if (videoFile == null || !videoFile.exists()) {
                finish(tag, false, context.getString(R.string.error_video_download))
                return@callbackFlow
            }

            val audioPhase = context.getString(R.string.progress_downloading_audio)
            val audioProgress = ProgressMapper(phase = audioPhase, from = 85, to = 97)
            trySend(DownloadState.Progress(85, phase = audioPhase))
            val audioFile = downloadRaw(tag, url, "bestaudio[ext=m4a]/bestaudio", tempAudioPath, cookieFile) { p, eta, line ->
                audioProgress.map(p, eta, line)?.let { trySend(it) }
            }
            if (tag in cancelled) {
                videoFile.delete()
                audioFile?.delete()
                finish(tag, false, "")
                return@callbackFlow
            }

            if (audioFile != null && audioFile.exists()) {
                trySend(DownloadState.Progress(98, phase = context.getString(R.string.progress_merging_audio)))
                val muxed = MediaMuxerUtil.mux(videoFile.absolutePath, audioFile.absolutePath, outputPath)
                videoFile.delete()
                audioFile.delete()
                if (!muxed) {
                    finish(tag, false, context.getString(R.string.error_merge_audio))
                    return@callbackFlow
                }
            } else {
                videoFile.renameTo(File(outputPath))
            }

            val finalFile = File(outputPath)
            if (finalFile.exists()) {
                trySend(DownloadState.Completed(MediaItem(
                    id = finalFile.nameWithoutExtension,
                    title = finalFile.nameWithoutExtension,
                    url = url,
                    localFilePath = finalFile.absolutePath,
                    source = url.detectSource()
                )))
                finish(tag, true, "")
            } else {
                finish(tag, false, context.getString(R.string.error_output_not_found))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download with merge failed", e)
            finish(tag, false, friendlyDownloadError(e))
        }
    }

    /**
     * Downloads a media file that the web extractor found on the page — no site extractor,
     * no login. yt-dlp still does the transfer (resume, retries, HLS), posing as the same
     * browser that found the link.
     */
    fun downloadDirect(
        pageUrl: String,
        format: FormatInfo,
        outputPath: String,
        cookieHeader: String? = null
    ): Flow<DownloadState> = callbackFlow {
        val tag = outputPath
        cancelled.remove(tag)
        trySend(DownloadState.Progress(0, phase = context.getString(R.string.progress_starting)))

        val out = File(outputPath)
        val dir = out.parentFile ?: out
        val audioOnly = format.isAudioOnly
        // Without ffmpeg the audio is pulled out of the video by Android's muxer afterwards.
        val nativeAudio = audioOnly && !ffmpegUsable
        val base = if (nativeAudio) ".temp_webaudio_${System.currentTimeMillis()}" else out.nameWithoutExtension

        val request = YoutubeDLRequest(format.directUrl).apply {
            addOption("--no-update")
            addOption("--no-playlist")
            addOption("--newline")
            addOption("--retries", "5")
            addOption("--fragment-retries", "5")
            addOption("--concurrent-fragments", "4")
            addOption("-o", File(dir, "$base.%(ext)s").absolutePath)
            if (audioOnly && !nativeAudio) {
                addOption("-x")
                addOption("--audio-format", "m4a")
                addOption("--audio-quality", "0")
            } else {
                addOption("--merge-output-format", "mp4")
            }
            if (format.userAgent.isNotBlank()) addOption("--user-agent", format.userAgent)
            addOption("--referer", pageUrl)
            cookieHeader?.takeIf { it.isNotBlank() }?.let { addOption("--add-header", "Cookie:$it") }
        }

        try {
            val progress = ProgressMapper(to = if (nativeAudio) 95 else 100)
            runTracked(tag, request) { p, eta, line -> progress.map(p, eta, line)?.let { trySend(it) } }

            var file = dir.listFiles { f ->
                f.isFile && f.nameWithoutExtension == base && f.extension.lowercase() in (VIDEO_EXTS + AUDIO_EXTS)
            }?.maxByOrNull { it.lastModified() }

            if (file != null && !ffmpegUsable && MediaMuxerUtil.isMpegTs(file)) {
                trySend(DownloadState.Progress(98, phase = context.getString(R.string.progress_finishing)))
                MediaMuxerUtil.remuxToMp4(file)
            }
            if (file != null && nativeAudio) {
                trySend(DownloadState.Progress(97, phase = context.getString(R.string.progress_extracting_audio)))
                val ok = MediaMuxerUtil.extractAudio(file.absolutePath, outputPath)
                file.delete()
                file = if (ok) out else null
            }

            if (file != null && file.exists()) {
                trySend(
                    DownloadState.Completed(
                        MediaItem(
                            id = file.nameWithoutExtension,
                            title = file.nameWithoutExtension,
                            url = pageUrl,
                            localFilePath = file.absolutePath,
                            source = pageUrl.detectSource(),
                            isAudio = audioOnly
                        )
                    )
                )
                finish(tag, true, "")
            } else {
                finish(tag, false, context.getString(R.string.error_output_not_found))
            }
        } catch (e: CanceledException) {
            finish(tag, false, "")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Direct download failed", e)
            finish(tag, false, friendlyDownloadError(e))
        }
    }

    private fun downloadRaw(
        tag: String,
        url: String,
        formatId: String,
        outputPath: String,
        cookieFile: File?,
        onProgress: (Float, Long, String) -> Unit
    ): File? {
        val isYt = isYoutubeUrl(url)
        val isTiktok = isTiktokUrl(url)
        val needsResilientRetry = isYt || isTiktok
        val clients = if (isYt) youtubeClientFallbackOrder else listOf<String?>(null)

        for ((index, client) in clients.withIndex()) {
            if (tag in cancelled) return null
            try {
                val request = YoutubeDLRequest(url)
                request.applyCommonOptions(cookieFile, client, isTiktok = isTiktok, url = url)
                request.addOption("-f", formatId)
                request.addOption("-o", outputPath)

                runTracked(tag, request, onProgress)

                val expectedFile = File(outputPath)
                if (expectedFile.exists()) return expectedFile

                val parentDir = expectedFile.parentFile ?: return null
                val stem = expectedFile.nameWithoutExtension
                val found = parentDir.listFiles { f ->
                    f.name.startsWith(stem) && f.lastModified() > System.currentTimeMillis() - 60000
                }?.maxByOrNull { it.lastModified() }
                if (found != null) return found
            } catch (e: CanceledException) {
                return null
            } catch (e: Exception) {
                Log.w(TAG, "Raw download failed for format '$formatId' with client '$client' (${index + 1}/${clients.size})", e)
                if (!needsResilientRetry || !e.is403()) break
            }
        }
        return null
    }

    /** Runs yt-dlp for one download, remembering its process so [cancel] can stop it. */
    private fun runTracked(tag: String, request: YoutubeDLRequest, onProgress: (Float, Long, String) -> Unit) {
        val pid = UUID.randomUUID().toString()
        running[tag] = pid
        try {
            if (tag in cancelled) throw CanceledException()
            YoutubeDL.getInstance().execute(request, pid) { progress, eta, line -> onProgress(progress, eta, line.orEmpty()) }
        } finally {
            running.remove(tag, pid)
        }
    }

    /** Terminal event for a download flow: completed (already sent), cancelled or failed. */
    private suspend fun ProducerScope<DownloadState>.finish(tag: String, succeeded: Boolean, error: String) {
        if (!succeeded) {
            if (tag in cancelled) trySend(DownloadState.Cancelled)
            else trySend(DownloadState.Error(error.ifBlank { context.getString(R.string.error_download_failed) }))
        }
        cancelled.remove(tag)
        close()
        awaitClose { }
    }

    private fun friendlyDownloadError(e: Exception): String {
        val m = e.message.orEmpty()
        return when {
            m.contains("HTTP Error 403", true) -> context.getString(R.string.error_link_expired)
            m.contains("No space left", true) -> context.getString(R.string.error_no_space)
            listOf("timed out", "Unable to download", "Network is unreachable", "Connection reset", "Failed to resolve")
                .any { m.contains(it, true) } -> context.getString(R.string.error_network)
            else -> m.lineSequence().firstOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim()?.take(180)
                ?: m.lineSequence().firstOrNull { it.isNotBlank() }?.take(180)
                ?: context.getString(R.string.error_download_failed)
        }
    }

    /**
     * Stops a download. With a [tag] (the download's output path) only that one; without,
     * every running download (the old single-download behavior).
     */
    fun cancel(tag: String? = null) {
        val targets = if (tag == null) running.keys.toList() else listOf(tag)
        targets.forEach { t ->
            cancelled.add(t)
            running.remove(t)?.let { pid ->
                try {
                    YoutubeDL.getInstance().destroyProcessById(pid)
                } catch (_: Exception) {}
            }
        }
    }

    private fun ensureFfmpeg(): File? {
        val cached = ffmpegDir
        if (cached?.exists() == true) return cached

        try {
            val archDir = "ffmpeg/arm64-v8a"
            val destDir = File(context.filesDir, "ffmpeg")
            destDir.mkdirs()

            val libs = listOf(
                "libavcodec.so", "libavdevice.so", "libavfilter.so",
                "libavformat.so", "libavutil.so", "libpostproc.so",
                "libswresample.so", "libswscale.so"
            )

            for (lib in libs) {
                val dest = File(destDir, lib)
                if (!dest.exists()) {
                    context.assets.open("$archDir/$lib").use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }

            val binary = File(destDir, "ffmpeg.bin")
            if (!binary.exists()) {
                context.assets.open("$archDir/ffmpeg.bin").use { input ->
                    binary.outputStream().use { output -> input.copyTo(output) }
                }
                binary.setExecutable(true)
            }

            val wrapper = File(destDir, "ffmpeg")
            if (!wrapper.exists()) {
                wrapper.writeText("""#!/system/bin/sh
DIR="${'$'}(dirname "${'$'}0")"
export LD_LIBRARY_PATH="${'$'}DIR:${'$'}LD_LIBRARY_PATH"
exec "${'$'}DIR/ffmpeg.bin" "${'$'}@"
""")
                wrapper.setExecutable(true)
            }

            ffmpegDir = destDir
            return destDir
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract ffmpeg", e)
            return null
        }
    }

    private fun String.detectSource(): MediaSource {
        val host = hostOf(this)
        return when {
            host.isOn("instagram.com", "instagr.am") -> MediaSource.INSTAGRAM
            host.isOn("tiktok.com") -> MediaSource.TIKTOK
            host.isOn("youtube.com", "youtu.be") -> MediaSource.YOUTUBE
            host.isOn("facebook.com", "fb.watch", "fb.com") -> MediaSource.FACEBOOK
            host.isOn("reddit.com", "redd.it") -> MediaSource.REDDIT
            host.isOn("twitter.com", "x.com", "t.co") -> MediaSource.TWITTER
            else -> MediaSource.UNKNOWN
        }
    }
}

/**
 * Turns yt-dlp's progress callback (0–100 %, ETA seconds, raw line) into a tracker state
 * with speed and size. [from]..[to] maps this transfer onto part of the overall bar, e.g.
 * video 0–85 and audio 85–97 for a merged download.
 */
private class ProgressMapper(
    private val phase: String = "",
    private val from: Int = 0,
    private val to: Int = 100
) {
    private val pctRe = Regex("""\[download]\s+([\d.]+)%""")
    private val sizeRe = Regex("""of\s+~?\s*([\d.]+)\s*([KMGT]?i?B)""")
    private val speedRe = Regex("""at\s+([\d.]+)\s*([KMGT]?i?B)/s""")
    private val etaRe = Regex("""ETA\s+(\d+(?::\d+){1,2})""")
    private var lastPct = -1

    fun map(rawProgress: Float, rawEta: Long, line: String): DownloadState.Progress? {
        val pct = when {
            rawProgress >= 0 -> rawProgress
            else -> pctRe.find(line)?.groupValues?.get(1)?.toFloatOrNull() ?: return null
        }.coerceIn(0f, 100f)
        if (!line.contains("[download]") && rawProgress < 0) return null
        val overall = (from + (to - from) * pct / 100f).toInt().coerceIn(0, 100)

        val total = sizeRe.find(line)?.let { toBytes(it.groupValues[1], it.groupValues[2]) } ?: 0L
        val speed = speedRe.find(line)?.let { toBytes(it.groupValues[1], it.groupValues[2]) } ?: 0L
        val eta = when {
            rawEta > 0 -> rawEta
            else -> etaRe.find(line)?.groupValues?.get(1)?.split(":")?.mapNotNull { it.toLongOrNull() }
                ?.fold(0L) { acc, n -> acc * 60 + n } ?: 0L
        }
        // Drop duplicate lines that change nothing visible.
        if (overall == lastPct && speed == 0L) return null
        lastPct = overall
        return DownloadState.Progress(
            percentage = overall,
            speed = if (speed > 0) "${formatBytes(speed)}/s" else "",
            eta = if (eta > 0 && pct < 100f) formatClock(eta) else "",
            phase = phase,
            sizeText = if (total > 0) "${formatBytes((total * pct / 100f).toLong())} / ${formatBytes(total)}" else ""
        )
    }

    private fun toBytes(num: String, unit: String): Long {
        val v = num.toDoubleOrNull() ?: return 0
        val mult = when (unit.uppercase(Locale.ROOT)) {
            "KIB" -> 1024.0; "MIB" -> 1024.0 * 1024; "GIB" -> 1024.0 * 1024 * 1024
            "KB" -> 1e3; "MB" -> 1e6; "GB" -> 1e9
            else -> 1.0
        }
        return (v * mult).toLong()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1e6)
        bytes >= 1_000 -> String.format(Locale.US, "%.0f KB", bytes / 1e3)
        else -> "$bytes B"
    }

    private fun formatClock(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }
}
