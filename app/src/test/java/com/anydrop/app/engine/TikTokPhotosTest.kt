package com.anydrop.app.engine

import com.anydrop.app.engine.TikTokPhotos.toFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokPhotosTest {

    private val page = "https://www.tiktok.com/@brand/photo/7594868072926792982"

    // The post as TikTok's pages hold it (trimmed); addresses escaped the way the page writes them.
    private fun item(images: Int, music: Boolean = true) = """
        {"id":"7594868072926792982","desc":"#gym #mindset ","author":{"uniqueId":"brand","nickname":"Brand"},
         "imagePost":{"title":"ultimate motivation.","images":[${
            (1..images).joinToString(",") { """{"imageURL":{"urlList":["https://p16.tiktokcdn.com/photo$it~tplv-photomode-image.jpeg?x=1","https://p19.tiktokcdn.com/photo$it.jpeg"]},"imageWidth":1290,"imageHeight":1612}""" }
         }]},
         "music":{${if (music) """"playUrl":"https://sf16-ies-music.tiktokcdn.com/obj/song","coverLarge":"https://p16.tiktokcdn.com/cover.jpeg","duration":60""" else """"title":"original sound""""}}}
    """.trimIndent()

    private fun mobilePage(item: String) =
        """<html><script id="api-data" type="application/json">{"videoDetail":{"itemInfo":{"itemStruct":$item}}}</script></html>"""

    private fun desktopPage(item: String) =
        """<html><script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">{"__DEFAULT_SCOPE__":{"webapp.video-detail":{"itemInfo":{"itemStruct":$item}}}}</script></html>"""

    @Test
    fun readsEveryPhotoAndTheSong_fromTheMobilePage() {
        val post = TikTokPhotos.parse(mobilePage(item(images = 3)))!!
        assertEquals(3, post.photos.size)
        assertEquals("https://p16.tiktokcdn.com/photo1~tplv-photomode-image.jpeg?x=1", post.photos[0].url)
        assertEquals(1290, post.photos[0].width)
        assertEquals("https://sf16-ies-music.tiktokcdn.com/obj/song", post.song?.url)
        assertEquals(60, post.song?.durationSec)
        assertEquals("ultimate motivation.", post.title)
        assertEquals("Brand", post.uploader)
    }

    @Test
    fun aLongCaptionWithoutTitle_isCutForFileNames() {
        val caption = "word ".repeat(400)
        val untitled = item(images = 2).replace("\"title\":\"ultimate motivation.\",", "").replace("#gym #mindset ", caption)
        val title = TikTokPhotos.parse(mobilePage(untitled))!!.title
        assertEquals(72, title.length)
        assertTrue(title.startsWith("word word") && title.endsWith("…"))
    }

    @Test
    fun readsTheDesktopPage_andThePageAShareLinkLandsOn() {
        assertEquals(2, TikTokPhotos.parse(desktopPage(item(images = 2)))?.photos?.size)
        // vt.tiktok.com/… → www.tiktok.com/@/video/<id>: the mobile page without api-data.
        val reflow = """<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">{"__DEFAULT_SCOPE__":{"webapp.reflow.video.detail":{"itemInfo":{"itemStruct":${item(images = 3)}}}}}</script>"""
        assertEquals(3, TikTokPhotos.parse(reflow)?.photos?.size)
    }

    @Test
    fun aVideo_theBotCheckPage_andNoSong() {
        val video = """{"id":"1","desc":"a video","video":{"duration":12},"music":{"playUrl":"https://x/s"}}"""
        assertNull(TikTokPhotos.parse(mobilePage(video)))
        assertNull(TikTokPhotos.parse("<html><body>Please wait...</body></html>"))
        assertNull(TikTokPhotos.parse(mobilePage(item(images = 1, music = false)))!!.song)
    }

    @Test
    fun photosAreSlides_thenTheSound() {
        val formats = TikTokPhotos.parse(mobilePage(item(images = 2)))!!.toFormats(page, "UA")
        assertEquals(listOf(0, 1, 2), formats.map { it.slide })
        assertEquals(listOf(true, true, false), formats.map { it.isImage })
        val sound = formats.last()
        assertTrue(sound.isAudioOnly && sound.isFromPage)
        assertEquals(page, sound.referer)
        assertEquals("jpeg", formats.first().extension)
    }

    @Test
    fun thePickerOffersPhotosAndSound() {
        // One photo with its sound: both offered, not just the sound.
        val one = TikTokPhotos.parse(mobilePage(item(images = 1)))!!.toFormats(page, "UA").postItems()
        assertEquals(listOf(true, false), one.map { it.isImage })
        val many = TikTokPhotos.parse(mobilePage(item(images = 4)))!!.toFormats(page, "UA").postItems()
        assertEquals(5, many.size)
        val silent = TikTokPhotos.parse(mobilePage(item(images = 3, music = false)))!!.toFormats(page, "UA").postItems()
        assertEquals(3, silent.size)
        assertTrue(silent.all { it.isImage })
    }

    @Test
    fun knowsShareLinksAndPhotoLinks() {
        assertTrue(TikTokPhotos.isShortLink("https://vt.tiktok.com/ZSabc123/"))
        assertTrue(TikTokPhotos.isShortLink("https://vm.tiktok.com/ZTR45GpSF/"))
        assertTrue(TikTokPhotos.isShortLink("https://www.tiktok.com/t/ZTRC5xgJp"))
        assertFalse(TikTokPhotos.isShortLink(page))
        assertTrue(TikTokPhotos.isPhotoLink("$page?_r=1"))
        assertFalse(TikTokPhotos.isPhotoLink("https://www.tiktok.com/@brand/video/7594868072926792982"))
    }
}
