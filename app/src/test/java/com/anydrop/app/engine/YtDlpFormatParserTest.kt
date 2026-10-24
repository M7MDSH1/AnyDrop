package com.anydrop.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpFormatParserTest {

    @Test
    fun extractHeight_parsesResolutionNumbers() {
        assertEquals(1080, YtDlpFormatParser.extractHeight("1080p60"))
        assertEquals(720, YtDlpFormatParser.extractHeight("720p"))
        assertEquals(4320, YtDlpFormatParser.extractHeight("4320p"))
    }

    @Test
    fun extractHeight_returnsZeroWhenNoDigits() {
        assertEquals(0, YtDlpFormatParser.extractHeight("unknown"))
        assertEquals(0, YtDlpFormatParser.extractHeight(""))
        assertEquals(0, YtDlpFormatParser.extractHeight("N/A"))
    }

    @Test
    fun extractHeight_readsAudioBitrateLabels() {
        assertEquals(128, YtDlpFormatParser.extractHeight("Audio 128kbps"))
        assertEquals(320, YtDlpFormatParser.extractHeight("Audio 320kbps"))
    }

    @Test
    fun imageExtFromUrl_extractsExtensionIgnoringQueryAndCase() {
        assertEquals("png", YtDlpFormatParser.imageExtFromUrl("https://cdn.example.com/photo.PNG?e=123&x=1"))
        assertEquals("webp", YtDlpFormatParser.imageExtFromUrl("https://x.com/a.webp"))
        assertEquals("gif", YtDlpFormatParser.imageExtFromUrl("https://x.com/a.gif"))
        assertEquals("jpeg", YtDlpFormatParser.imageExtFromUrl("https://x.com/a.jpeg"))
    }

    @Test
    fun videoLinks_endingInCodes_getARealFileType() {
        // Snapchat's CDN ends links in codes, not file types.
        val snap = "https://cf-st.sc-cdn.net/d/7aF3kQ.27.IRZXSOY?mo=Gk0aFBoAGgAyAX06AQRCBgjZ&uc=46"
        assertEquals("mp4", YtDlpFormatParser.videoExtFromUrl(snap))
        assertTrue(YtDlpFormatParser.hasOddExt(snap))
        assertEquals("webm", YtDlpFormatParser.videoExtFromUrl("https://x.com/a.b/clip.WEBM?t=1"))
        assertFalse(YtDlpFormatParser.hasOddExt("https://x.com/clip.mp4?t=1"))
        assertFalse(YtDlpFormatParser.hasOddExt("https://x.com/live/index.m3u8"))
        // No file type at all: yt-dlp names those itself, as before.
        assertFalse(YtDlpFormatParser.hasOddExt("https://cdn.x.com/v/12345"))
        assertEquals("mp4", YtDlpFormatParser.videoExtFromUrl("https://cdn.x.com/v/12345"))
    }

    @Test
    fun imageExtFromUrl_defaultsToJpg() {
        assertEquals("jpg", YtDlpFormatParser.imageExtFromUrl("https://scontent.cdninstagram.com/v/t51.123"))
        assertEquals("jpg", YtDlpFormatParser.imageExtFromUrl(""))
    }

    @Test
    fun isImageUrl_matchesImageExtensionsAndIgHosts() {
        assertTrue(YtDlpFormatParser.isImageUrl("https://scontent.cdninstagram.com/v/t51.2885-15/photo.jpg"))
        assertTrue(YtDlpFormatParser.isImageUrl("https://cdninstagram.com/some/thing"))
        assertTrue(YtDlpFormatParser.isImageUrl("https://example.com/image.jpeg?x=1"))
        assertTrue(YtDlpFormatParser.isImageUrl("https://example.com/scontent/foo"))
    }

    @Test
    fun isImageUrl_rejectsNonImages() {
        assertFalse(YtDlpFormatParser.isImageUrl("https://example.com/video.mp4"))
        assertFalse(YtDlpFormatParser.isImageUrl("https://example.com/plain"))
        assertFalse(YtDlpFormatParser.isImageUrl(""))
    }

    @Test
    fun parseCarouselImages_extractsEveryEntryInOrder() {
        val json = """
            {
              "id": "post1",
              "entries": [
                {"url": "https://scontent.cdninstagram.com/a.jpg?e=1"},
                {"display_url": "https://scontent2.jpg"},
                {"thumbnails": [{"url": "https://thumb-small.jpg"}, {"url": "https://thumb-big.jpg"}]},
                {"thumbnail": "https://plain-thumb.jpg"}
              ]
            }
        """.trimIndent()
        val images = YtDlpFormatParser.parseCarouselImages(json)
        assertEquals(4, images.size)
        assertEquals("https://scontent.cdninstagram.com/a.jpg?e=1", images[0].imageUrl)
        assertEquals("https://scontent2.jpg", images[1].imageUrl)
        assertEquals("https://thumb-big.jpg", images[2].imageUrl)
        assertEquals("https://plain-thumb.jpg", images[3].imageUrl)
        assertTrue(images.all { it.isImage })
        assertEquals("Image 1/4", images[0].resolution)
        assertEquals("Image 4/4", images[3].resolution)
        assertEquals("jpg", images[0].extension)
    }

    @Test
    fun parseCarouselImages_deduplicatesUrls() {
        val json = """
            {"entries": [
              {"url": "https://x.com/a.png"},
              {"url": "https://x.com/a.png"}
            ]}
        """.trimIndent()
        val images = YtDlpFormatParser.parseCarouselImages(json)
        assertEquals(1, images.size)
    }

    @Test
    fun parseCarouselImages_singleNodeYieldsPhotoEntry() {
        val json = """{"display_url": "https://x.com/cover.png"}"""
        val images = YtDlpFormatParser.parseCarouselImages(json)
        assertEquals(1, images.size)
        assertEquals("Image (photo)", images[0].resolution)
        assertEquals("png", images[0].extension)
    }

    @Test
    fun parseCarouselImages_returnsEmptyWhenNoImages() {
        assertEquals(0, YtDlpFormatParser.parseCarouselImages("""{"entries": [{"title": "x"}]}""").size)
        assertEquals(0, YtDlpFormatParser.parseCarouselImages("""{"foo": "bar"}""").size)
        assertEquals(0, YtDlpFormatParser.parseCarouselImages("""{"url": "https://x.com/video.mp4"}""").size)
    }

    @Test
    fun parseCarouselImages_returnsEmptyForMalformedJson() {
        assertEquals(0, YtDlpFormatParser.parseCarouselImages("not json at all").size)
        assertEquals(0, YtDlpFormatParser.parseCarouselImages("").size)
    }
}
