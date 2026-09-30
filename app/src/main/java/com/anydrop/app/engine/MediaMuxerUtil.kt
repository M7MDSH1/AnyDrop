package com.anydrop.app.engine

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Android's built-in muxer: joins/rewraps tracks without re-encoding (fast, lossless) and
 * without ffmpeg — which matters because the bundled ffmpeg can't load on phones with
 * 16 KB memory pages.
 */
object MediaMuxerUtil {

    private const val TAG = "MediaMuxerUtil"

    /** Video track of [videoPath] + audio track of [audioPath] → [outputPath] (MP4). */
    fun mux(videoPath: String, audioPath: String, outputPath: String): Boolean = try {
        copyTracks(listOf(videoPath to "video/", audioPath to "audio/"), outputPath)
        File(outputPath).exists()
    } catch (e: Exception) {
        Log.e(TAG, "Mux failed", e)
        File(outputPath).delete()
        false
    }

    /** Pulls the audio track out of a video file into an M4A. */
    fun extractAudio(videoPath: String, outputPath: String): Boolean = try {
        copyTracks(listOf(videoPath to "audio/"), outputPath)
        File(outputPath).exists()
    } catch (e: Exception) {
        Log.e(TAG, "Audio extraction failed", e)
        File(outputPath).delete()
        false
    }

    /** yt-dlp's HLS downloader leaves raw MPEG-TS in the file when ffmpeg can't fix it up. */
    fun isMpegTs(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val head = ByteArray(189)
            input.read(head) == 189 && head[0] == 0x47.toByte() && head[188] == 0x47.toByte()
        }
    }.getOrDefault(false)

    /** Rewraps an MPEG-TS file as a real MP4 in place. */
    fun remuxToMp4(file: File): Boolean {
        val tmp = File(file.parentFile, ".temp_remux_${System.currentTimeMillis()}.mp4")
        return try {
            copyTracks(listOf(file.absolutePath to "video/", file.absolutePath to "audio/"), tmp.absolutePath)
            file.delete() && tmp.renameTo(file)
        } catch (e: Exception) {
            Log.e(TAG, "Remux failed", e)
            tmp.delete()
            false
        }
    }

    @SuppressLint("WrongConstant")
    private fun copyTracks(sources: List<Pair<String, String>>, outputPath: String) {
        File(outputPath).delete()
        val extractors = mutableListOf<MediaExtractor>()
        val tracks = sources.mapNotNull { (path, prefix) ->
            val ex = MediaExtractor().also { extractors += it }
            ex.setDataSource(path)
            val idx = findTrack(ex, prefix) ?: return@mapNotNull null
            ex.selectTrack(idx)
            ex to ex.getTrackFormat(idx)
        }
        try {
            check(tracks.isNotEmpty()) { "No audio or video track found" }
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val writeIndex = tracks.map { (_, format) ->
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_ROTATION)) {
                        muxer.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION))
                    }
                    muxer.addTrack(format)
                }
                muxer.start()
                // Big enough for 4K key frames (a fixed 1 MB buffer overflows on them).
                val maxInput = tracks.maxOf { (_, f) ->
                    if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
                }
                val buffer = ByteBuffer.allocateDirect(maxOf(maxInput, 8 * 1024 * 1024))
                val info = MediaCodec.BufferInfo()
                tracks.forEachIndexed { i, (ex, _) ->
                    while (true) {
                        buffer.clear()
                        val size = ex.readSampleData(buffer, 0)
                        if (size < 0) break
                        // Only the key-frame bit carries over; extractor flag 4 ("partial frame")
                        // would otherwise be read by the muxer as end-of-stream.
                        val flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        info.set(0, size, ex.sampleTime, flags)
                        muxer.writeSampleData(writeIndex[i], buffer, info)
                        if (!ex.advance()) break
                    }
                }
                muxer.stop()
            } finally {
                runCatching { muxer.release() }
            }
        } finally {
            extractors.forEach { runCatching { it.release() } }
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int? =
        (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true
        }
}
