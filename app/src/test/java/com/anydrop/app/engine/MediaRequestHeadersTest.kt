package com.anydrop.app.engine

import com.anydrop.app.data.repository.FormatJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaRequestHeadersTest {
    @Test fun restoredDownloadKeepsTheEmbeddedPlayersHeaders() {
        val saved = JSONObject("""{"id":"web:0","extension":"mp4","resolution":"Auto","vcodec":"page","acodec":"page","directUrl":"https://cdn.example/master.m3u8","referer":"https://player.example/","origin":"https://player.example"}""")
        val restored = FormatJson.toJson(FormatJson.fromJson(saved))
        assertEquals("https://player.example/", restored.optString("referer"))
        assertEquals("https://player.example", restored.optString("origin"))
    }

    @Test fun networkHeadersReachVideoAndAudioEvenWhenDomCandidateRanksHigher() {
        val url = "https://cdn.example/master.m3u8?token=a%2Fb"
        val network = MediaSniffer.classifyNetworkUrl(url, mapOf(
            "referer" to "https://player.example/", "ORIGIN" to "https://player.example",
            "Cookie" to "private=value", "Authorization" to "private"
        ))!!
        val dom = MediaCandidate(url, 95, "video", width = 1280, height = 720)
        for (whole in listOf(false, true)) {
            val raw = JSONObject().put("url", "https://outer.example/watch").toString()
            val scan = MediaSniffer.parse(raw, listOf(dom, network), "https://outer.example/watch", "test-UA", whole)
            assertEquals(720, scan.videos.single().height)
            val formats = scan.toFormatInfos()
            assertEquals(2, formats.size)
            for (format in formats) {
                val restored = FormatJson.fromJson(FormatJson.toJson(format))
                assertEquals(mapOf("User-Agent" to "test-UA", "Referer" to "https://player.example/", "Origin" to "https://player.example"),
                    restored.directRequestHeaders("https://outer.example/watch"))
                assertEquals(url, restored.directUrl)
            }
        }
    }

    @Test fun legacyFormatsFallBackToPageWithoutInventingAnOrigin() {
        val format = FormatJson.fromJson(JSONObject("""{"userAgent":"test-UA"}"""))
        assertEquals(mapOf("User-Agent" to "test-UA", "Referer" to "https://outer.example/watch"),
            format.directRequestHeaders("https://outer.example/watch"))
    }

    @Test fun headerLineBreaksAreRejected() {
        assertEquals("", browserRequestHeader(mapOf("Referer" to "https://player.example/\r\nCookie: x"), "Referer"))
    }
}
