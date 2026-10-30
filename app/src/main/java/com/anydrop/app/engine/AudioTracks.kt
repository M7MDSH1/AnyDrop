package com.anydrop.app.engine

import org.json.JSONObject

/**
 * Audio languages of a video, from yt-dlp's JSON: YouTube videos with several audio tracks
 * (including its automatic dubbing) and DASH videos whose manifest lists translated voices.
 */
object AudioTracks {

    /** How a track relates to the video: its own voice, or a translation of it. */
    enum class Role { ORIGINAL, DUBBED, OTHER }

    data class Track(val language: String, val role: Role)

    /** Format id → its audio language, for the formats yt-dlp gives a language. */
    fun parse(json: String): Map<String, Track> {
        val formats = runCatching { JSONObject(json.trim()).optJSONArray("formats") }.getOrNull() ?: return emptyMap()
        val tracks = mutableMapOf<String, Track>()
        for (i in 0 until formats.length()) {
            val f = formats.optJSONObject(i) ?: continue
            val id = f.optString("format_id").takeIf { it.isNotBlank() } ?: continue
            val language = f.optString("language").takeIf { it.isNotBlank() && it != "null" } ?: continue
            val note = f.optString("format_note").lowercase()
            val role = when {
                "dubbed" in note -> Role.DUBBED
                "original" in note || f.optInt("language_preference", -1) >= 10 -> Role.ORIGINAL
                else -> Role.OTHER
            }
            tracks[id] = Track(language, role)
        }
        return tracks
    }

    /**
     * yt-dlp format selector for the best audio in [language], preferring M4A (which Android's
     * own muxer can put in an MP4), then any audio if the language isn't there after all.
     */
    fun selector(language: String): String =
        "bestaudio[language=$language][ext=m4a]/bestaudio[language=$language]/bestaudio[ext=m4a]/bestaudio"
}
