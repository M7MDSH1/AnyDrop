package com.anydrop.app.engine

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

object MediaMuxerUtil {

    private const val TAG = "MediaMuxerUtil"

    fun mux(videoPath: String, audioPath: String, outputPath: String): Boolean {
        return try {
            val videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(videoPath)
            val videoTrackIndex = findTrack(videoExtractor, "video/")
            videoExtractor.selectTrack(videoTrackIndex)
            val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)

            val audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(audioPath)
            val audioTrackIndex = findTrack(audioExtractor, "audio/")
            audioExtractor.selectTrack(audioTrackIndex)
            val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)

            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val videoWriteIndex = muxer.addTrack(videoFormat)
            val audioWriteIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            writeAllSamples(videoExtractor, muxer, videoWriteIndex, buffer, bufferInfo)
            writeAllSamples(audioExtractor, muxer, audioWriteIndex, buffer, bufferInfo)

            muxer.stop()
            muxer.release()
            videoExtractor.release()
            audioExtractor.release()

            File(outputPath).exists()
        } catch (e: Exception) {
            Log.e(TAG, "Mux failed", e)
            false
        }
    }

    private fun writeAllSamples(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        trackIndex: Int,
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            bufferInfo.set(0, sampleSize, extractor.sampleTime, extractor.sampleFlags)
            muxer.writeSampleData(trackIndex, buffer, bufferInfo)
            if (!extractor.advance()) break
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i) ?: continue
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return i
        }
        throw IllegalStateException("No $mimePrefix track found")
    }
}
