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
}
