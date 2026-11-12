package com.anydrop.app.data.repository

import com.anydrop.app.engine.YtDlpFormatParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRetryTest {

    @Test
    fun connectionTroubleIsTriedAgain() {
        assertTrue(DownloadRetry.isTransient("ERROR: unable to download video data: <urlopen error timed out>"))
        assertTrue(DownloadRetry.isTransient("java.net.SocketException: Software caused connection abort"))
        assertTrue(DownloadRetry.isTransient("ERROR: Unable to download webpage: HTTP Error 503: Service Unavailable"))
        assertTrue(DownloadRetry.isTransient("[Errno 101] Network is unreachable"))
    }

    @Test
    fun whatWaitingCannotFixIsNot() {
        assertFalse(DownloadRetry.isTransient("ERROR: Unable to download webpage: HTTP Error 404: Not Found"))
        assertFalse(DownloadRetry.isTransient("ERROR: unable to download video data: HTTP Error 403: Forbidden"))
        assertFalse(DownloadRetry.isTransient("ERROR: [Errno 28] No space left on device"))
        assertFalse(DownloadRetry.isTransient("ERROR: Private video. Sign in if you've been granted access"))
        assertFalse(DownloadRetry.isTransient("Something else entirely"))
    }

    @Test
    fun waitsGrowBetweenTries() {
        assertEquals(listOf(5_000L, 15_000L, 45_000L), (1..3).map(DownloadRetry::delayMs))
    }

    @Test
    fun creatorFallsBackToTheChannel() {
        assertEquals("Blender", YtDlpFormatParser.creatorOf("""{"uploader": "Blender", "channel": "Other"}"""))
        assertEquals("Blender", YtDlpFormatParser.creatorOf("""{"uploader": null, "channel": "Blender"}"""))
        assertEquals("@handle", YtDlpFormatParser.creatorOf("""{"title": "x", "uploader_id": "@handle"}"""))
        assertEquals("", YtDlpFormatParser.creatorOf("""{"title": "x"}"""))
        assertEquals("", YtDlpFormatParser.creatorOf("not json"))
    }

    @Test
    fun sizeIsEstimatedOnlyWhenTheSiteGivesNone() {
        assertEquals(5_000_000L, YtDlpFormatParser.sizeOrEstimate(5_000_000L, 800, 60))
        // 800 kbps for a minute = 6 MB.
        assertEquals(6_000_000L, YtDlpFormatParser.sizeOrEstimate(0L, 800, 60))
        assertEquals(0L, YtDlpFormatParser.sizeOrEstimate(0L, 0, 60))
        assertEquals(0L, YtDlpFormatParser.sizeOrEstimate(-1L, 800, 0))
    }
}
