package com.anydrop.app.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PageScanTest {

    private val raw = JSONObject(
        """
        {"title":"Cooking show","url":"https://site.com/v/1","thumb":"https://site.com/og.jpg",
         "hits":[
           {"url":"https://cdn.site.com/main.mp4","score":78,"src":"video","w":1280,"h":720,"cover":"https://site.com/poster.jpg","dur":212},
           {"url":"https://ads.example/clip.mp4","score":70,"src":"json","dur":15,"title":"Sponsored"},
           {"url":"https://cdn.site.com/guess.mp4","score":40,"src":"html"}
         ],
         "imgs":[],
         "links":[
           {"url":"https://site.com/v/2","thumb":"https://site.com/t2.jpg","dur":95,"title":"Next one"},
           {"url":"https://site.com/v/2","thumb":"https://site.com/t2.jpg","dur":95,"title":"Next one again"},
           {"url":"javascript:void(0)","thumb":null,"dur":null,"title":"x"}
         ]}
        """
    ).toString()

    @Test
    fun wholePageScan_keepsEveryRealVideo_andTheLinks() {
        val scan = MediaSniffer.parse(raw, emptyList(), "https://site.com/v/1", "UA", wholePage = true)
        // The raw-text guess is dropped because real videos were found.
        assertEquals(listOf("https://cdn.site.com/main.mp4", "https://ads.example/clip.mp4"), scan.videos.map { it.url })
        assertEquals(listOf(PageLink("https://site.com/v/2", "https://site.com/t2.jpg", 95, "Next one")), scan.links)
        assertEquals(listOf(212, 15), scan.videos.map { it.duration })
        assertEquals("Sponsored", scan.videos[1].title)
    }

    @Test
    fun pageFormats_carryCoverLengthAndTitle() {
        val formats = MediaSniffer.parse(raw, emptyList(), "https://site.com/v/1", "UA", wholePage = true).toFormatInfos()
        val videos = formats.filter { !it.isAudioOnly }
        assertEquals("https://site.com/poster.jpg", videos[0].previewUrl)
        assertEquals(212, videos[0].durationSec)
        assertEquals("720p", videos[0].resolution)
        // Two videos on the page: the page's own picture isn't guessed as the ad's cover.
        assertEquals("", videos[1].previewUrl)
        assertEquals("Sponsored", videos[1].title)
    }

    @Test
    fun thePagePicture_coversThePageOnlyVideo() {
        val single = JSONObject(raw).apply {
            put("hits", JSONObject(raw).getJSONArray("hits").let { org.json.JSONArray().put(it.getJSONObject(1)) })
        }.toString()
        val formats = MediaSniffer.parse(single, emptyList(), "https://site.com/v/1", "UA").toFormatInfos()
        assertEquals("https://site.com/og.jpg", formats.first().previewUrl)
    }

    @Test
    fun equallyLikelyFiles_theLongerOneComesFirst() {
        // Two players on a page, found the same way: the 5-second clip is the ad.
        val ad = MediaCandidate("https://ads.example/clip.mp4", 78, "video", 1280, 720, duration = 5)
        val video = MediaCandidate("https://cdn.site.com/main.mp4", 78, "video", 854, 480, duration = 52)
        assertEquals(listOf(video.url, ad.url), MediaSniffer.rank("https://site.com/v/1", listOf(ad, video)).map { it.url })
    }

    // An Instagram page carries other posts too (suggested reels, "more posts"), and the same
    // photo under several addresses (sizes). Seen as: a stranger's video, its sound, the photo twice.
    private fun igPage(postUrl: String, hits: String, imgs: String) =
        JSONObject("""{"title":"Someone on Instagram: \"hi\"","url":"$postUrl","hits":$hits,"imgs":$imgs,"links":[]}""").toString()

    private val photo1080 = """{"url":"https://scontent.cdninstagram.com/v/t51/111_n.jpg?stp=s1080x1350","code":"PHOTO1","w":1080,"h":1350}"""
    private val photo640 = """{"url":"https://scontent.cdninstagram.com/v/t51/111_n.jpg?stp=s640x800","code":"PHOTO1","w":640,"h":800}"""

    @Test
    fun photoPost_listsOnlyItsPhoto_once() {
        val raw = igPage(
            "https://www.instagram.com/p/PHOTO1/",
            hits = """[{"url":"https://scontent.cdninstagram.com/o1/other.mp4","score":100,"src":"ig","code":"OTHER1","w":720,"h":1280}]""",
            imgs = "[$photo640,$photo1080," +
                """{"url":"https://scontent.cdninstagram.com/v/t51/999_n.jpg","code":"OTHER1","w":1080,"h":1080}]"""
        )
        val network = listOf(MediaCandidate("https://scontent.cdninstagram.com/o1/autoplay.mp4", 62, "net"))
        val formats = MediaSniffer.parse(raw, network, "https://www.instagram.com/p/PHOTO1/", "UA").toFormatInfos()
        assertEquals(listOf("https://scontent.cdninstagram.com/v/t51/111_n.jpg?stp=s1080x1350"), formats.map { it.imageUrl })
        assertEquals(1, formats.size) // no video, no "Audio"
    }

    @Test
    fun reel_keepsItsOwnVideo_only() {
        val raw = igPage(
            "https://www.instagram.com/reel/REEL1/",
            hits = """[{"url":"https://cdn/other.mp4","score":100,"src":"ig","code":"OTHER1"},""" +
                """{"url":"https://cdn/reel.mp4","score":100,"src":"ig","code":"REEL1","w":720,"h":1280}]""",
            imgs = "[]"
        )
        val scan = MediaSniffer.parse(raw, emptyList(), "https://www.instagram.com/reel/REEL1/", "UA")
        assertEquals(listOf("https://cdn/reel.mp4"), scan.videos.map { it.url })
    }

    @Test
    fun reelWhoseVideoCameWithoutItsCode_isStillFound_otherPostsAreNot() {
        val raw = igPage(
            "https://www.instagram.com/reel/REEL2/",
            hits = """[{"url":"https://cdn/other.mp4","score":100,"src":"ig","code":"OTHER1"}]""",
            imgs = "[]"
        )
        val network = listOf(MediaCandidate("https://cdn/reel2.mp4", 62, "net"))
        val scan = MediaSniffer.parse(raw, network, "https://www.instagram.com/reel/REEL2/", "UA")
        assertEquals(listOf("https://cdn/reel2.mp4"), scan.videos.map { it.url })
    }

    @Test
    fun carousel_keepsEverySlide_onceEach() {
        val raw = igPage(
            "https://www.instagram.com/p/CARO1/",
            hits = """[{"url":"https://cdn/slide1.mp4","score":100,"src":"ig","code":"CARO1","slide":1}]""",
            imgs = """[{"url":"https://cdn/a_n.jpg?stp=s640","code":"CARO1","slide":0,"w":640,"h":640},""" +
                """{"url":"https://cdn/a_n.jpg?stp=s1080","code":"CARO1","slide":0,"w":1080,"h":1080},""" +
                """{"url":"https://cdn/c_n.jpg","code":"CARO1","slide":2,"w":1080,"h":1080}]"""
        )
        val scan = MediaSniffer.parse(raw, emptyList(), "https://www.instagram.com/p/CARO1/", "UA")
        assertEquals(listOf("https://cdn/slide1.mp4"), scan.videos.map { it.url })
        assertEquals(listOf("https://cdn/a_n.jpg?stp=s1080", "https://cdn/c_n.jpg"), scan.images.map { it.url })
    }

    @Test
    fun aPostsVideoUnderTwoAddresses_isListedOnce() {
        val raw = igPage(
            "https://www.threads.com/@someone/post/THR1",
            hits = """[{"url":"https://cdn/o1/v/AQO_video.mp4?efg=a&oh=1","score":100,"src":"ig","code":"THR1","w":1280,"h":720},""" +
                """{"url":"https://cdn/o1/v/AQO_video.mp4?efg=b&oh=2","score":100,"src":"ig","code":"THR1","w":1280,"h":720},""" +
                """{"url":"https://cdn/o1/v/OTHER.mp4","score":100,"src":"ig","code":"OTHER2"}]""",
            imgs = "[]"
        )
        val scan = MediaSniffer.parse(raw, emptyList(), "https://www.threads.com/@someone/post/THR1", "UA")
        assertEquals(listOf("https://cdn/o1/v/AQO_video.mp4?efg=a&oh=1"), scan.videos.map { it.url })
    }
}
