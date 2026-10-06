package com.anydrop.app.engine

import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostItemsTest {

    private fun video(id: String, slide: Int = -1) =
        FormatInfo(id = id, extension = "mp4", resolution = "720p", filesize = 0, vcodec = "page", acodec = "page", directUrl = "https://v/$id.mp4", slide = slide)

    private fun photo(url: String, slide: Int = -1) =
        FormatInfo(id = "", extension = "jpg", resolution = "Image", filesize = 0, vcodec = "none", acodec = "none", isImage = true, imageUrl = url, slide = slide)

    private val audio = FormatInfo(id = "web:audio", extension = "m4a", resolution = "Audio", filesize = 0, vcodec = "none", acodec = "page", isAudioOnly = true)

    @Test
    fun mixedCarousel_listsEverySlideInPostOrder() {
        // The page reader ranks videos by quality, so they arrive out of slide order.
        val formats = listOf(video("b", slide = 2), video("a", slide = 0), audio, photo("p1", slide = 1), photo("p3", slide = 3))
        val items = formats.postItems()
        assertEquals(listOf(0, 1, 2, 3), items.map { it.slide })
        assertEquals(listOf(false, true, false, true), items.map { it.isImage })
    }

    @Test
    fun aSlideFoundTwiceCountsOnce_keepingItsBestEntry() {
        val items = listOf(video("best", slide = 0), video("worse", slide = 0), photo("p", slide = 1)).postItems()
        assertEquals(2, items.size)
        assertEquals("best", items.first().id)
    }

    @Test
    fun singleMediaPosts_haveNoPicker() {
        assertTrue(listOf(video("only"), audio).postItems().isEmpty())
        assertTrue(listOf(photo("only")).postItems().isEmpty())
        // One slide is not a carousel either.
        assertTrue(listOf(video("a", slide = 0), audio).postItems().isEmpty())
    }

    @Test
    fun photoCarouselsWithoutPositions_useEveryPhoto() {
        val items = listOf(photo("1"), photo("2"), photo("3")).postItems()
        assertEquals(listOf("1", "2", "3"), items.map { it.imageUrl })
        // ...but not when the post also has a video without positions.
        assertTrue(listOf(video("v"), photo("1"), photo("2")).postItems().isEmpty())
    }
}
