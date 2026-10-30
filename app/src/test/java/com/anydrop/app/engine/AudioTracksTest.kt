package com.anydrop.app.engine

import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTracksTest {

    // What yt-dlp reports for a YouTube video with dubbed voices (trimmed).
    private val json = """
        {"id": "x", "formats": [
          {"format_id": "140", "language": "en-US", "language_preference": 10, "format_note": "English (US) original (default), medium", "acodec": "mp4a.40.2", "vcodec": "none"},
          {"format_id": "140-18", "language": "ar", "language_preference": -1, "format_note": "Arabic, medium", "acodec": "mp4a.40.2", "vcodec": "none"},
          {"format_id": "251-3", "language": "es-US", "format_note": "Spanish (US) dubbed-auto, medium", "acodec": "opus", "vcodec": "none"},
          {"format_id": "137", "format_note": "1080p", "acodec": "none", "vcodec": "avc1"}
        ]}
    """.trimIndent()

    @Test
    fun audioLanguages_areReadFromYtDlp() {
        val tracks = AudioTracks.parse(json)
        assertEquals(AudioTracks.Track("en-US", AudioTracks.Role.ORIGINAL), tracks["140"])
        assertEquals(AudioTracks.Track("ar", AudioTracks.Role.OTHER), tracks["140-18"])
        assertEquals(AudioTracks.Track("es-US", AudioTracks.Role.DUBBED), tracks["251-3"])
        // A video format without a language isn't listed.
        assertTrue("137" !in tracks)
        assertTrue(AudioTracks.parse("not json").isEmpty())
    }

    private fun audio(id: String, language: String, role: AudioTracks.Role) = FormatInfo(
        id = id, extension = "m4a", resolution = "Audio", filesize = 0, vcodec = "none", acodec = "mp4a",
        isAudioOnly = true, abr = 128, language = language, audioRole = role
    )

    private val video = FormatInfo(id = "137", extension = "mp4", resolution = "1080p", filesize = 0, vcodec = "avc1", acodec = "none")

    @Test
    fun voices_putTheOriginalFirst_andTheRestAreTranslations() {
        val formats = listOf(
            video,
            audio("140-18", "ar", AudioTracks.Role.OTHER),
            audio("140", "en-US", AudioTracks.Role.ORIGINAL),
            audio("139", "en-US", AudioTracks.Role.ORIGINAL)
        )
        val voices = AudioVoice.of(formats)
        assertEquals(listOf("en-US", "ar"), voices.map { it.language })
        assertEquals(AudioTracks.Role.DUBBED, voices[1].role)
        // One language: nothing to choose.
        assertTrue(AudioVoice.of(listOf(video, audio("140", "en-US", AudioTracks.Role.ORIGINAL))).isEmpty())
    }

    @Test
    fun theVoicePickedLastTime_isUsedAgain_andOnlyTranslationsAreAskedFor() {
        val voices = listOf(AudioVoice("en-US", AudioTracks.Role.ORIGINAL), AudioVoice("ar", AudioTracks.Role.DUBBED))
        assertEquals("ar", AudioVoice.pick(voices, preferred = "ar"))
        assertEquals("en-US", AudioVoice.pick(voices, preferred = "fr"))
        // A video in its own voice downloads as usual; a translation is asked for by language.
        assertEquals("", AudioVoice.toRequest(voices, "en-US", video))
        assertEquals("ar", AudioVoice.toRequest(voices, "ar", video))
        // Sound only always says which voice.
        assertEquals("en-US", AudioVoice.toRequest(voices, "en-US", audio("140", "en-US", AudioTracks.Role.ORIGINAL)))
    }
}
