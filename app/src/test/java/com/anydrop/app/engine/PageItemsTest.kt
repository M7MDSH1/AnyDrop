package com.anydrop.app.engine

import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageItemsTest {

    private fun main(res: String, seconds: Int = 0) =
        FormatInfo(id = res, extension = "mp4", resolution = res, filesize = 0, vcodec = "avc1", acodec = "mp4a", group = YtDlpEngine.MAIN_GROUP, durationSec = seconds)

    private fun onPage(group: String, url: String, seconds: Int = 0) =
        FormatInfo(id = "web:$group", extension = "mp4", resolution = "Original", filesize = 0, vcodec = "page", acodec = "page", directUrl = url, group = group, durationSec = seconds)

    private fun linked(url: String, title: String) =
        FormatInfo(id = "", extension = "mp4", resolution = "Best", filesize = 0, vcodec = FormatInfo.LINK_CODEC, acodec = FormatInfo.LINK_CODEC, group = url, pageUrl = url, title = title)

    @Test
    fun pageItems_groupQualitiesPerVideo_inTheOrderFound() {
        val audio = FormatInfo(id = "a", extension = "m4a", resolution = "Audio", filesize = 0, vcodec = "none", acodec = "mp4a", isAudioOnly = true, group = YtDlpEngine.MAIN_GROUP)
        val formats = listOf(main("1080p"), main("720p"), audio, onPage("page:0", "https://cdn/ad.mp4"), linked("https://site/v/2", "Next video"))
        val items = formats.pageItems()
        assertEquals(listOf(YtDlpEngine.MAIN_GROUP, "page:0", "https://site/v/2"), items.map { it.key })
        assertEquals(listOf("1080p", "720p"), items[0].formats.map { it.resolution }) // audio isn't offered
        assertTrue(items[0].isMain)
        assertTrue(items[2].isLink)
        assertEquals("Next video", items[2].title("Page title"))
        assertEquals("Page title", items[1].title("Page title"))
    }

    @Test
    fun onlyWholePageResultsAreGrouped() {
        val plain = FormatInfo(id = "18", extension = "mp4", resolution = "360p", filesize = 0, vcodec = "avc1", acodec = "mp4a")
        assertFalse(listOf(plain).isWholePage)
        assertTrue(listOf(plain).pageItems().isEmpty())
        assertTrue(listOf(main("720p")).isWholePage)
    }

    @Test
    fun aPageCopyOfTheMainVideo_isDropped_butOtherClipsStay() {
        val formats = listOf(
            main("720p", seconds = 212),
            onPage("page:0", "https://cdn/same.mp4", seconds = 213), // the player's own file
            onPage("page:1", "https://cdn/ad.mp4", seconds = 15)     // an ad
        )
        assertEquals(listOf(YtDlpEngine.MAIN_GROUP, "page:1"), formats.withoutRepeatsOfMain().pageItems().map { it.key })
        // Without a known length nothing can be compared, so nothing is dropped.
        val unknown = listOf(main("720p"), onPage("page:0", "https://cdn/x.mp4", seconds = 0))
        assertEquals(2, unknown.withoutRepeatsOfMain().size)
    }

    @Test
    fun distinctPageVideos_countsFilesNotQualities() {
        val audio = FormatInfo(id = "web:audio", extension = "m4a", resolution = "Audio", filesize = 0, vcodec = "none", acodec = "page", isAudioOnly = true, directUrl = "https://cdn/a.mp4")
        val one = listOf(onPage("", "https://cdn/a.mp4"), audio)
        assertEquals(1, one.distinctPageVideos())
        assertEquals(2, (one + onPage("", "https://cdn/ad.mp4")).distinctPageVideos())
    }

    @Test
    fun clockText_readsLikeAPlayer() {
        assertEquals("", clockText(0))
        assertEquals("0:09", clockText(9))
        assertEquals("1:15", clockText(75))
        assertEquals("1:02:03", clockText(3723))
    }
}
