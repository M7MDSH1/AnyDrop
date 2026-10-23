package com.anydrop.app.engine

import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistsTest {

    @Test
    fun playlistLinks_areRecognised() {
        val mix = "https://www.youtube.com/watch?v=_9hOZHbfbHg&list=RD_9hOZHbfbHg&start_radio=1"
        assertTrue(Playlists.hasList(mix))
        assertFalse(Playlists.isListOnly(mix)) // a video inside the mix
        assertTrue(Playlists.isListOnly("https://www.youtube.com/playlist?list=PL590L5WQmH8fJ54F369BLDSqIwcs-TCfs"))
        assertFalse(Playlists.hasList("https://www.youtube.com/watch?v=aqz-KE-bpKQ"))
    }

    // What `yt-dlp --flat-playlist -J` gives for a YouTube mix (trimmed).
    private val mixJson = """
        {"_type": "playlist", "id": "RD_9hOZHbfbHg", "title": "Mix - Song", "uploader": null,
         "entries": [
           {"_type": "url", "ie_key": "Youtube", "id": "_9hOZHbfbHg", "url": "https://www.youtube.com/watch?v=_9hOZHbfbHg",
            "title": "First song", "duration": 213.0,
            "thumbnails": [{"url": "https://i.ytimg.com/vi/_9hOZHbfbHg/default.jpg", "width": 120, "height": 90},
                           {"url": "https://i.ytimg.com/vi/_9hOZHbfbHg/mqdefault.jpg", "width": 320, "height": 180},
                           {"url": "https://i.ytimg.com/vi/_9hOZHbfbHg/hqdefault.jpg", "width": 480, "height": 360}]},
           {"_type": "url", "ie_key": "Youtube", "id": "aqz-KE-bpKQ", "title": "Second song", "duration": null},
           {"_type": "url", "ie_key": "Youtube", "id": "xxxxxxxxxxx", "title": "[Private video]"},
           {"_type": "url", "ie_key": "Youtube", "id": "_9hOZHbfbHg", "url": "https://www.youtube.com/watch?v=_9hOZHbfbHg", "title": "First song again"}
         ]}
    """.trimIndent()

    @Test
    fun aMix_listsItsWatchableVideosOnce() {
        val playlist = Playlists.parse(mixJson)!!
        assertEquals("Mix - Song", playlist.title)
        assertEquals("", playlist.uploader)
        assertEquals(listOf("First song", "Second song"), playlist.entries.map { it.title })
        val first = playlist.entries[0]
        assertEquals(213, first.durationSec)
        // About 320 px wide: enough for the list.
        assertEquals("https://i.ytimg.com/vi/_9hOZHbfbHg/mqdefault.jpg", first.thumbnail)
        // No url or thumbnail listed: built from the video id.
        val second = playlist.entries[1]
        assertEquals("https://www.youtube.com/watch?v=aqz-KE-bpKQ", second.url)
        assertEquals("https://i.ytimg.com/vi/aqz-KE-bpKQ/mqdefault.jpg", second.thumbnail)
        assertEquals(0, second.durationSec)
    }

    @Test
    fun aSingleVideo_isNoPlaylist() {
        assertNull(Playlists.parse("""{"_type": "video", "id": "aqz-KE-bpKQ", "title": "One"}"""))
        assertNull(Playlists.parse("not json"))
    }

    @Test
    fun playlistVideos_canBeSavedAsSound() {
        val link = FormatInfo(
            id = "", extension = "mp4", resolution = "Best", filesize = 0, vcodec = FormatInfo.LINK_CODEC, acodec = FormatInfo.LINK_CODEC,
            title = "First song", group = YtDlpEngine.PLAYLIST_GROUP + "https://www.youtube.com/watch?v=_9hOZHbfbHg",
            pageUrl = "https://www.youtube.com/watch?v=_9hOZHbfbHg", durationSec = 213
        )
        assertTrue(listOf(link).isPlaylist)
        val audio = link.asAudio()
        assertTrue(audio.isAudioOnly)
        assertFalse(audio.isLink)
        assertEquals("m4a", audio.extension)
        assertEquals(link.pageUrl, audio.pageUrl)
        assertEquals("First song", audio.title)
    }
}
