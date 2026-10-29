package com.anydrop.app.data.repository

import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadListStoreTest {

    private val pageVideo = FormatInfo(
        id = "web:0", extension = "mp4", resolution = "720p", filesize = 0, vcodec = "page", acodec = "page",
        directUrl = "https://cdn.example.com/v.mp4", userAgent = "UA", previewUrl = "https://cdn.example.com/c.jpg", durationSec = 61
    )

    private fun task(status: TaskStatus) = DownloadTask(
        tag = "/storage/AnyDrop/Videos/Clip [720p].mp4",
        title = "Clip",
        quality = "720p",
        kind = TaskKind.VIDEO,
        request = DownloadRequest(
            url = "https://example.com/watch/1",
            format = pageVideo,
            title = "Clip",
            creator = "someone",
            webpageUrl = "https://example.com/watch/1",
            cookieHeader = "session=secret"
        ),
        status = status,
        percent = 42,
        speed = "1.2 MB/s",
        eta = "0:30",
        phase = "Downloading…",
        error = "Couldn't reach the site.",
        errorDetail = "ERROR: [generic] Unable to download webpage",
        startedAt = 1_700_000_000_000
    )

    @Test
    fun aDownload_comesBackTheSameAfterARestart() {
        val saved = task(TaskStatus.FAILED)
        val back = DownloadListStore.decode(DownloadListStore.encode(listOf(saved))).single()
        assertEquals(saved.tag, back.tag)
        assertEquals(saved.status, back.status)
        assertEquals(saved.error, back.error)
        assertEquals(saved.errorDetail, back.errorDetail)
        assertEquals(saved.startedAt, back.startedAt)
        // The request (with the page video's direct link) is kept so RETRY works...
        assertEquals(saved.request.format, back.request.format)
        assertEquals(saved.request.webpageUrl, back.request.webpageUrl)
        // ...but session cookies and live progress are not.
        assertNull(back.request.cookieHeader)
        assertEquals("", back.speed)
        assertEquals("", back.eta)
    }

    @Test
    fun aBrokenSave_givesAnEmptyList() {
        assertTrue(DownloadListStore.decode("not json").isEmpty())
        assertTrue(DownloadListStore.decode(null).isEmpty())
    }
}
