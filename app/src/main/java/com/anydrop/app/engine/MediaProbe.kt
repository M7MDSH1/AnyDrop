package com.anydrop.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Looks inside the video files a page plays, so they can be told apart before downloading:
 * how long each one is, its real resolution, and a frame to show as its cover (an ad is usually
 * a short clip with a different picture). Only the file's header and one frame are read, not the
 * whole video; streams (HLS/DASH) give their length and resolution from their playlist.
 */
object MediaProbe {
    private const val TAG = "MediaProbe"
    private const val PER_ITEM_TIMEOUT_MS = 9_000L
    private const val MAX_ITEMS = 12
    private const val COVER_MAX_SIDE = 480
    private val parallel = Semaphore(3)

    private class Info(val seconds: Int, val shortSide: Int, val cover: String?)

    /** Page videos still missing a length, a resolution or a cover. */
    fun needsProbe(f: FormatInfo): Boolean =
        f.isFromPage && !f.isAudioOnly && !f.isImage &&
            (f.durationSec <= 0 || f.previewUrl.isBlank() || ProbeParsing.isUnsized(f.resolution))

    /** [formats] with what probing found filled in; entries that couldn't be read stay as they were. */
    suspend fun fill(context: Context, formats: List<FormatInfo>, pageUrl: String): List<FormatInfo> = coroutineScope {
        val targets = formats.filter(::needsProbe).map { it.directUrl }.distinct().take(MAX_ITEMS)
        if (targets.isEmpty()) return@coroutineScope formats
        pruneCovers(context)
        val results = targets.map { url ->
            async {
                val sample = formats.first { it.directUrl == url }
                val wantFrame = formats.any { it.directUrl == url && it.previewUrl.isBlank() }
                parallel.withPermit {
                    withTimeoutOrNull(PER_ITEM_TIMEOUT_MS) {
                        runInterruptible(Dispatchers.IO) { probe(context, url, headersFor(url, sample.userAgent, pageUrl), wantFrame) }
                    }
                }
            }
        }.awaitAll()
        val found = targets.zip(results).mapNotNull { (url, info) -> info?.let { url to it } }.toMap()
        if (found.isEmpty()) return@coroutineScope formats
        ProbeParsing.uniqueLabels(
            formats.map { f ->
                val info = found[f.directUrl] ?: return@map f
                f.copy(
                    durationSec = f.durationSec.takeIf { it > 0 } ?: info.seconds,
                    previewUrl = f.previewUrl.ifBlank { info.cover.orEmpty() },
                    resolution = if (f.isAudioOnly) f.resolution else ProbeParsing.relabel(f.resolution, info.shortSide)
                )
            }
        )
    }

    private fun headersFor(url: String, userAgent: String, pageUrl: String): Map<String, String> = buildMap {
        if (userAgent.isNotBlank()) put("User-Agent", userAgent)
        if (pageUrl.startsWith("http")) put("Referer", pageUrl)
        MediaSniffer.cookieHeaderFor(url)?.let { put("Cookie", it) }
    }

    private fun probe(context: Context, url: String, headers: Map<String, String>, wantFrame: Boolean): Info? {
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        return try {
            when {
                path.endsWith(".m3u8") -> probeHls(url, headers)
                path.endsWith(".mpd") -> fetchText(url, headers)?.let { ProbeParsing.dashInfo(it) }?.let { (s, short) -> Info(s, short, null) }
                else -> probeFile(context, url, headers, wantFrame)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read ${url.take(80)}: ${e.message}")
            null
        }
    }

    private fun probeHls(url: String, headers: Map<String, String>): Info? {
        val text = fetchText(url, headers) ?: return null
        val variants = ProbeParsing.hlsVariants(text)
        if (variants.isEmpty()) return Info(ProbeParsing.hlsSeconds(text), 0, null)
        val best = variants.maxBy { if (it.bandwidth > 0) it.bandwidth else it.width.toLong() * it.height }
        val media = fetchText(URL(URL(url), best.uri).toString(), headers)
        val short = minOf(best.width, best.height).takeIf { it > 0 } ?: maxOf(best.width, best.height)
        return Info(media?.let(ProbeParsing::hlsSeconds) ?: 0, short, null)
    }

    private fun probeFile(context: Context, url: String, headers: Map<String, String>, wantFrame: Boolean): Info? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(url, headers)
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val cover = if (wantFrame && (w > 0 || h > 0)) frame(context, r, url, ms) else null
            val short = minOf(w, h).takeIf { it > 0 } ?: maxOf(w, h)
            return Info((ms / 1000).toInt(), short, cover)
        } finally {
            runCatching { r.release() }
        }
    }

    /** A frame a little way in (past black intros), saved small as the cover. */
    private fun frame(context: Context, r: MediaMetadataRetriever, url: String, durationMs: Long): String? {
        val atUs = (durationMs * 1000L / 4).coerceIn(0L, 3_000_000L)
        val option = MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            r.getScaledFrameAtTime(atUs, option, COVER_MAX_SIDE, COVER_MAX_SIDE)
        } else {
            r.getFrameAtTime(atUs, option)
        } ?: return null
        val scale = COVER_MAX_SIDE.toFloat() / maxOf(raw.width, raw.height)
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt().coerceAtLeast(1), (raw.height * scale).toInt().coerceAtLeast(1), true) else raw
        val file = File(coverDir(context), "${url.hashCode().toUInt()}.jpg")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        if (bmp !== raw) bmp.recycle()
        raw.recycle()
        return Uri.fromFile(file).toString()
    }

    private fun coverDir(context: Context) = File(context.cacheDir, "page_covers").apply { mkdirs() }

    /** Covers are only needed while choosing; drop the ones from days ago. */
    private fun pruneCovers(context: Context) {
        val cutoff = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000
        coverDir(context).listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private fun fetchText(url: String, headers: Map<String, String>): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 6_000
            conn.readTimeout = 6_000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { reader ->
                val buf = CharArray(8192)
                val sb = StringBuilder()
                while (sb.length < 2_000_000) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                }
                sb.toString()
            }
        } finally {
            conn.disconnect()
        }
    }
}
