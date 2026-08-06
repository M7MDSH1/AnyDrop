package com.anydrop.app.engine

import android.content.Context
import android.util.Log
import com.anydrop.app.data.model.DownloadState
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.model.MediaSource
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDL.CanceledException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class YtDlpEngine(
    private val context: Context
) {

    private var processId: String? = null
    private var ffmpegDir: File? = null

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
        val imageUrl: String = "" // direct image URL when derived from a thumbnail (no yt-dlp format id)
    )

    fun init() {
        YoutubeDL.getInstance().init(context)
        // Initialize the official FFmpeg native library (installs a real executable
        // in the app's native lib dir and registers it with yt-dlp). This replaces
        // the old shell-wrapper hack, which couldn't execute on Android 10+ (SELinux
        // blocks running binaries from filesDir) — that's why audio extraction failed
        // with "ffprobe and ffmpeg not found".
        try {
            com.yausername.ffmpeg.FFmpeg.getInstance().init(context)
            Log.i("YtDlpEngine", "FFmpeg initialized")
        } catch (e: Exception) {
            Log.w("YtDlpEngine", "FFmpeg init failed", e)
        }
        Thread {
            try {
                Log.i("YtDlpEngine", "Checking for yt-dlp update...")
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
            } catch (e: Exception) {
                Log.w("YtDlpEngine", "yt-dlp update failed (non-fatal)", e)
            }
        }.apply { isDaemon = true }.start()
    }

    /**
     * YouTube is currently in an anti-bot arms race: it rotates which player
     * client works from week to week, and split (video-only/audio-only) formats
     * get 403'd far more than single progressive files. We try clients in order
     * and fall back automatically instead of hardcoding one that will go stale.
     */
    private val youtubeClientFallbackOrder = listOf("android", "tv", "web_creator", "mweb", "web")

    private fun isYoutubeUrl(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("youtube.com") || u.contains("youtu.be")
    }

    private fun isTiktokUrl(url: String): Boolean {
        val u = url.lowercase()
        return u.contains("tiktok.com") || u.contains("vm.tiktok.com") || u.contains("vt.tiktok.com")
    }

    /**
     * Applies the shared extractor/client args used for BOTH info fetch and download.
     * IMPORTANT: do NOT add manual Origin/Referer headers here — googlevideo URLs
     * signed for the android/tv player clients will 403 if those headers are present.
     */
    private fun YoutubeDLRequest.applyCommonOptions(
        cookieFile: File?,
        youtubeClient: String? = null,
        isTiktok: Boolean = false
    ) {
        addOption("--no-update")
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
        } else {
            addOption("--user-agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.127 Mobile Safari/537.36")
        }
        addOption("--extractor-retries", "5")
        addOption("--retries", "5")
        // NOTE: no manual --ffmpeg-location. The official com.yausername.ffmpeg
        // library (initialized in init()) installs a real ffmpeg executable and
        // registers it with yt-dlp automatically, so it's found without a path.
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

    // Metadata of the most recently fetched media (empty if not provided by site).
    @Volatile
    var lastFetchedTitle: String = ""
        private set

    @Volatile
    var lastFetchedUploader: String = ""
        private set

    @Volatile
    var lastFetchedUploaderUrl: String = ""
        private set

    @Volatile
    var lastFetchedWebpageUrl: String = ""
        private set

    suspend fun getFormats(url: String, cookieFile: File? = null): List<FormatInfo> {
        return withContext(Dispatchers.IO) {
            val isTiktok = isTiktokUrl(url)
            val clients = if (isYoutubeUrl(url)) youtubeClientFallbackOrder else listOf<String?>(null)

            for ((index, client) in clients.withIndex()) {
                val request = YoutubeDLRequest(url)
                request.applyCommonOptions(cookieFile, client, isTiktok = isTiktok)
                try {
                    val info = YoutubeDL.getInstance().getInfo(request)
                    lastFetchedTitle = info.title?.trim().orEmpty()
                    lastFetchedUploader = runCatching { info.uploader?.trim().orEmpty() }.getOrDefault("")
                    lastFetchedWebpageUrl = runCatching { info.webpageUrl?.trim().orEmpty() }.getOrDefault("")
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
                        // Try the carousel JSON path first (all slides); fall back to
                        // the single cover thumbnail if it finds nothing.
                        carouselImagesFromJson(url, cookieFile, client, isTiktok)
                            .ifEmpty { imageEntriesFromThumbnail(info) }
                    } else emptyList()

                    val formats = videoFormats + audioFormats + imageFormats + thumbImages
                    if (formats.isNotEmpty()) return@withContext formats
                    Log.w("YtDlpEngine", "Client '$client' returned no usable formats, trying next")
                } catch (e: Exception) {
                    Log.w("YtDlpEngine", "getFormats failed with client '$client' (${index + 1}/${clients.size})", e)
                    if (e.isImpersonationUnavailable()) {
                        throw UnsupportedSiteException(
                            "This site blocks non-browser downloaders and requires browser impersonation, which this app doesn't support."
                        )
                    }
                    if (e.needsLogin()) {
                        throw UnsupportedSiteException(
                            "This post requires you to be logged in. Import your cookies in Settings → Cookie Import (log in on your browser, export cookies.txt, then load it here), and try again."
                        )
                    }
                    // Instagram/TikTok image posts make the video extractor throw
                    // "No video formats found!". Fall back to grabbing the image via a
                    // thumbnail-oriented info pass and offer it as an image download.
                    if (e.isNoVideoFormats()) {
                        val imgs = imageFallbackViaThumbnails(url, cookieFile, client, isTiktok)
                        if (imgs.isNotEmpty()) return@withContext imgs
                    }
                }
            }
            emptyList()
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
            request.applyCommonOptions(cookieFile, client, isTiktok = isTiktok)
            request.addOption("--ignore-no-formats-error")
            request.addOption("--skip-download")
            val info = YoutubeDL.getInstance().getInfo(request)
            imageEntriesFromThumbnail(info)
        } catch (e: Exception) {
            Log.w("YtDlpEngine", "Image thumbnail fallback failed", e)
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
            request.applyCommonOptions(cookieFile, client, isTiktok = isTiktok)
            request.addOption("-J")                    // dump single JSON (whole playlist)
            request.addOption("--ignore-no-formats-error")
            request.addOption("--no-warnings")
            request.addOption("--skip-download")
            val response = YoutubeDL.getInstance().execute(request)
            YtDlpFormatParser.parseCarouselImages(response.out.trim())
        } catch (e: Exception) {
            Log.w("YtDlpEngine", "Carousel JSON parse failed", e)
            emptyList()
        }
    }

    fun download(
        url: String,
        outputPath: String,
        formatId: String = "bestvideo+bestaudio/best",
        cookieFile: File? = null
    ): Flow<DownloadState> = callbackFlow {
        trySend(DownloadState.Progress(0, "Initializing...", ""))

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

        var lastError: String = "Download failed"
        var succeeded = false

        outer@ for (fmt in formatsToTry) {
            for ((index, client) in clients.withIndex()) {
                if (succeeded) break@outer
                processId = UUID.randomUUID().toString()

                val request = YoutubeDLRequest(url)
                request.applyCommonOptions(cookieFile, client, isTiktok = isTiktok)
                request.addOption("-f", fmt)
                request.addOption("-o", outputPath)

                try {
                    val pid = processId
                    YoutubeDL.getInstance().execute(request, pid) { progress, etaSeconds, _ ->
                        val eta = if (etaSeconds > 0) "${etaSeconds}s" else ""
                        trySend(DownloadState.Progress((progress * 100).toInt().coerceIn(0, 100), "", eta))
                    }

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
                        lastError = "Output file not found at: $outputPath"
                    }
                } catch (e: CanceledException) {
                    trySend(DownloadState.Cancelled)
                    close()
                    return@callbackFlow
                } catch (e: Exception) {
                    lastError = e.message ?: "Download failed"
                    Log.w("YtDlpEngine", "Download failed with client '$client', format '$fmt' (${index + 1}/${clients.size})", e)
                    // Only worth retrying if this looks like a 403/blocked-client
                    // issue, not a genuine bad URL or no-space error. For TikTok there
                    // is only one client entry, so this break just moves on to the
                    // next format (e.g. the "best" progressive fallback).
                    if (!needsResilientRetry || !e.is403()) break
                }
            }
        }

        if (!succeeded) {
            trySend(DownloadState.Error(lastError))
        }
        close()

        awaitClose {
            processId?.let { id ->
                try {
                    YoutubeDL.getInstance().destroyProcessById(id)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Audio-only download: grabs the best audio stream and extracts it to an .m4a
     * using ffmpeg (already wired via --ffmpeg-location). The outputPath should end
     * in ".m4a"; yt-dlp writes the extracted file next to it.
     */
    fun downloadAudio(
        url: String,
        outputPath: String,
        cookieFile: File? = null
    ): Flow<DownloadState> = callbackFlow {
        trySend(DownloadState.Progress(0, "Initializing...", ""))

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

        var lastError = "Audio download failed"
        var succeeded = false

        for ((index, client) in clients.withIndex()) {
            if (succeeded) break
            processId = UUID.randomUUID().toString()

            val request = YoutubeDLRequest(url)
            request.applyCommonOptions(cookieFile, client, isTiktok = isTiktok)
            request.addOption("-f", "bestaudio/best")
            request.addOption("-x")                       // extract audio
            request.addOption("--audio-format", "m4a")    // container ffmpeg can produce
            request.addOption("--audio-quality", "0")     // best
            request.addOption("-o", "$outBase.%(ext)s")

            try {
                val pid = processId
                YoutubeDL.getInstance().execute(request, pid) { progress, etaSeconds, _ ->
                    val eta = if (etaSeconds > 0) "${etaSeconds}s" else ""
                    trySend(DownloadState.Progress((progress * 100).toInt().coerceIn(0, 100), "", eta))
                }

                val outputDir = File(outputPath).parentFile
                val actualFile = outputDir?.listFiles { f ->
                    f.nameWithoutExtension == File(outBase).name &&
                        f.extension in listOf("m4a", "mp3", "opus", "aac", "ogg", "webm")
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
                    lastError = "Audio output not found for: $outBase"
                }
            } catch (e: CanceledException) {
                trySend(DownloadState.Cancelled)
                close()
                return@callbackFlow
            } catch (e: Exception) {
                lastError = e.message ?: "Audio download failed"
                Log.w("YtDlpEngine", "Audio download failed with client '$client' (${index + 1}/${clients.size})", e)
                if (e.isImpersonationUnavailable()) {
                    lastError = "This site blocks non-browser downloaders and isn't supported."
                    break
                }
                if (!needsResilientRetry || !e.is403()) break
            }
        }

        if (!succeeded) {
            trySend(DownloadState.Error(lastError))
        }
        close()

        awaitClose {
            processId?.let { id ->
                try {
                    YoutubeDL.getInstance().destroyProcessById(id)
                } catch (_: Exception) {}
            }
        }
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
        trySend(DownloadState.Progress(0, "Initializing...", ""))

        // If we have a direct image URL (derived from a thumbnail), fetch it over HTTP
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
                    trySend(DownloadState.Error("Failed to save image"))
                }
            } catch (e: Exception) {
                Log.w("YtDlpEngine", "Direct image download failed", e)
                trySend(DownloadState.Error(e.message ?: "Image download failed"))
            }
            close()
            return@callbackFlow
        }

        val isYt = isYoutubeUrl(url)
        val isTiktok = isTiktokUrl(url)
        val needsResilientRetry = isYt || isTiktok

        val clients = if (isYt) youtubeClientFallbackOrder else listOf<String?>(null)
        // Strip only the real trailing extension (titles may contain dots).
        val outBase = File(outputPath).let { f ->
            File(f.parentFile, f.nameWithoutExtension).absolutePath
        }

        var lastError = "Image download failed"
        var succeeded = false

        for ((index, client) in clients.withIndex()) {
            if (succeeded) break
            processId = UUID.randomUUID().toString()

            val request = YoutubeDLRequest(url)
            request.applyCommonOptions(cookieFile, client, isTiktok = isTiktok)
            request.addOption("-f", formatId.ifBlank { "best" })
            request.addOption("-o", "$outBase.%(ext)s")

            try {
                val pid = processId
                YoutubeDL.getInstance().execute(request, pid) { progress, etaSeconds, _ ->
                    val eta = if (etaSeconds > 0) "${etaSeconds}s" else ""
                    trySend(DownloadState.Progress((progress * 100).toInt().coerceIn(0, 100), "", eta))
                }

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
                    lastError = "Image output not found for: $outBase"
                }
            } catch (e: CanceledException) {
                trySend(DownloadState.Cancelled)
                close()
                return@callbackFlow
            } catch (e: Exception) {
                lastError = e.message ?: "Image download failed"
                Log.w("YtDlpEngine", "Image download failed with client '$client' (${index + 1}/${clients.size})", e)
                if (e.isImpersonationUnavailable()) {
                    lastError = "This site blocks non-browser downloaders and isn't supported."
                    break
                }
                if (!needsResilientRetry || !e.is403()) break
            }
        }

        if (!succeeded) {
            trySend(DownloadState.Error(lastError))
        }
        close()

        awaitClose {
            processId?.let { id ->
                try {
                    YoutubeDL.getInstance().destroyProcessById(id)
                } catch (_: Exception) {}
            }
        }
    }

    fun downloadWithMerge(
        url: String,
        outputPath: String,
        formatId: String,
        height: Int,
        cookieFile: File? = null
    ): Flow<DownloadState> = callbackFlow {
        trySend(DownloadState.Progress(0, "Starting...", ""))

        processId = UUID.randomUUID().toString()

        val downloadDir = File(outputPath).parentFile ?: File(outputPath)
        val timestamp = System.currentTimeMillis()
        val tempVideoPath = File(downloadDir, ".temp_video_$timestamp.mp4").absolutePath
        val tempAudioPath = File(downloadDir, ".temp_audio_$timestamp.m4a").absolutePath

        try {
            trySend(DownloadState.Progress(5, "Downloading video...", ""))
            var videoFormat = "bestvideo[height<=$height]"
            var videoFile = downloadRaw(url, videoFormat, tempVideoPath, cookieFile)
            if (videoFile == null || !videoFile.exists()) {
                trySend(DownloadState.Progress(5, "Retrying video without height restriction...", ""))
                videoFormat = "bestvideo"
                videoFile = downloadRaw(url, videoFormat, tempVideoPath, cookieFile)
                if (videoFile == null || !videoFile.exists()) {
                    trySend(DownloadState.Error("Failed to download video"))
                    close()
                    return@callbackFlow
                }
            }
            trySend(DownloadState.Progress(45, "Downloading audio...", ""))

            val audioFile = downloadRaw(url, "bestaudio[ext=m4a]", tempAudioPath, cookieFile)

            if (audioFile != null && audioFile.exists()) {
                trySend(DownloadState.Progress(75, "Merging audio...", ""))
                val muxed = MediaMuxerUtil.mux(videoFile.absolutePath, audioFile.absolutePath, outputPath)
                videoFile.delete()
                audioFile.delete()
                if (!muxed) {
                    trySend(DownloadState.Error("Failed to merge audio"))
                    close()
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
            } else {
                trySend(DownloadState.Error("Output file not found"))
            }
        } catch (e: CanceledException) {
            trySend(DownloadState.Cancelled)
        } catch (e: Exception) {
            Log.e("YtDlpEngine", "Download with merge failed", e)
            trySend(DownloadState.Error(e.message ?: "Download failed"))
        } finally {
            close()
        }

        awaitClose {
            processId?.let { id ->
                try { YoutubeDL.getInstance().destroyProcessById(id) } catch (_: Exception) {}
            }
        }
    }

    private fun downloadRaw(
        url: String,
        formatId: String,
        outputPath: String,
        cookieFile: File?
    ): File? {
        val isYt = isYoutubeUrl(url)
        val isTiktok = isTiktokUrl(url)
        val needsResilientRetry = isYt || isTiktok
        val clients = if (isYt) youtubeClientFallbackOrder else listOf<String?>(null)

        for ((index, client) in clients.withIndex()) {
            try {
                val request = YoutubeDLRequest(url)
                request.applyCommonOptions(cookieFile, client, isTiktok = isTiktok)
                request.addOption("-f", formatId)
                request.addOption("-o", outputPath)

                val pid = UUID.randomUUID().toString()
                YoutubeDL.getInstance().execute(request, pid, null)

                val expectedFile = File(outputPath)
                if (expectedFile.exists()) return expectedFile

                val parentDir = expectedFile.parentFile ?: return null
                val stem = expectedFile.nameWithoutExtension
                val found = parentDir.listFiles { f ->
                    f.name.startsWith(stem) && f.lastModified() > System.currentTimeMillis() - 60000
                }?.maxByOrNull { it.lastModified() }
                if (found != null) return found
            } catch (e: Exception) {
                Log.w("YtDlpEngine", "Raw download failed for format '$formatId' with client '$client' (${index + 1}/${clients.size})", e)
                if (!needsResilientRetry || !e.is403()) break
            }
        }
        return null
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
            Log.w("YtDlpEngine", "Failed to extract ffmpeg", e)
            return null
        }
    }

    fun cancel() {
        processId?.let { id ->
            try {
                YoutubeDL.getInstance().destroyProcessById(id)
            } catch (_: Exception) {}
        }
    }

    private fun String.detectSource(): MediaSource {
        val url = lowercase()
        return when {
            url.contains("instagram.com") -> MediaSource.INSTAGRAM
            url.contains("tiktok.com") -> MediaSource.TIKTOK
            url.contains("youtube.com") || url.contains("youtu.be") -> MediaSource.YOUTUBE
            url.contains("facebook.com") || url.contains("fb.watch") -> MediaSource.FACEBOOK
            url.contains("reddit.com") || url.contains("redd.it") -> MediaSource.REDDIT
            url.contains("twitter.com") || url.contains("x.com") -> MediaSource.TWITTER
            else -> MediaSource.UNKNOWN
        }
    }
}