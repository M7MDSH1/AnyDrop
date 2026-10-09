package com.anydrop.app.engine

import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeParsingTest {

    @Test
    fun isoDurations() {
        assertEquals(3723, ProbeParsing.isoSeconds("PT1H2M3S"))
        assertEquals(46, ProbeParsing.isoSeconds("PT45.6S"))
        assertEquals(90, ProbeParsing.isoSeconds("PT1M30S"))
        assertEquals(0, ProbeParsing.isoSeconds("PT"))
        assertEquals(0, ProbeParsing.isoSeconds("soon"))
    }

    @Test
    fun hlsLength_isTheSumOfItsSegments_andLiveStreamsHaveNone() {
        val vod = "#EXTM3U\n#EXTINF:6.0,\na.ts\n#EXTINF:6.0,\nb.ts\n#EXTINF:2.5,\nc.ts\n#EXT-X-ENDLIST\n"
        assertEquals(15, ProbeParsing.hlsSeconds(vod))
        assertEquals(0, ProbeParsing.hlsSeconds(vod.replace("#EXT-X-ENDLIST\n", "")))
    }

    @Test
    fun hlsMaster_listsVariants_withTheirOwnBandwidth() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=900000,BANDWIDTH=1200000,RESOLUTION=1280x720
            720/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=400000,RESOLUTION=640x360
            360/index.m3u8
        """.trimIndent()
        val variants = ProbeParsing.hlsVariants(master)
        assertEquals(2, variants.size)
        assertEquals(ProbeParsing.HlsVariant("720/index.m3u8", 1_200_000, 1280, 720), variants[0])
        assertTrue(ProbeParsing.hlsVariants("#EXTM3U\n#EXTINF:4,\na.ts\n").isEmpty())
    }

    @Test
    fun dashManifest_givesLengthAndLargestPicture() {
        val mpd = """<MPD mediaPresentationDuration="PT2M5.2S"><Period><AdaptationSet>
            <Representation id="1" width="640" height="360"/><Representation id="2" width="1920" height="1080"/>
            </AdaptationSet></Period></MPD>"""
        assertEquals(125 to 1080, ProbeParsing.dashInfo(mpd))
    }

    @Test
    fun onlyLabelsWithoutAResolutionGetOne() {
        assertTrue(ProbeParsing.isUnsized("Original"))
        assertTrue(ProbeParsing.isUnsized("Auto (2)"))
        assertTrue(ProbeParsing.isUnsized("Video 2 · Original"))
        assertFalse(ProbeParsing.isUnsized("720p"))
        assertEquals("720p", ProbeParsing.relabel("Original (2)", 720))
        assertEquals("Video 2 · 1080p", ProbeParsing.relabel("Video 2 · Original", 1080))
        assertEquals("480p", ProbeParsing.relabel("480p", 720))
        assertEquals("Original", ProbeParsing.relabel("Original", 0))
    }

    @Test
    fun labelsStayUniqueWithinEachVideo() {
        fun f(label: String, group: String = "") =
            FormatInfo(id = label + group, extension = "mp4", resolution = label, filesize = 0, vcodec = "page", acodec = "page", group = group)
        val out = ProbeParsing.uniqueLabels(listOf(f("720p"), f("720p"), f("720p (2)"), f("720p", group = "page:1")))
        assertEquals(listOf("720p", "720p (2)", "720p (3)", "720p"), out.map { it.resolution })
    }
}
