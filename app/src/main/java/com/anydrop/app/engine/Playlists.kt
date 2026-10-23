package com.anydrop.app.engine

import org.json.JSONArray
import org.json.JSONObject

/** Playlists (a YouTube playlist or mix, a channel's videos…): links and yt-dlp's listing of them. */
object Playlists {

    private val LIST = Regex("""[?&]list=([^&#]+)""")
    private val VIDEO = Regex("""[?&]v=([^&#]+)""")

    /** The link names a playlist (`list=`), possibly next to one video of it. */
    fun hasList(url: String): Boolean = LIST.containsMatchIn(url)

    /** A link to the playlist itself rather than to one of its videos (youtube.com/playlist?list=…). */
    fun isListOnly(url: String): Boolean =
        hasList(url) && !VIDEO.containsMatchIn(url) && !url.contains("youtu.be/", ignoreCase = true)

    /** One video of a playlist, as listed (its qualities are looked up later). */
    data class Entry(val url: String, val title: String, val durationSec: Int, val thumbnail: String?)

    data class Playlist(val title: String, val uploader: String, val entries: List<Entry>)

    private val YOUTUBE_ID = Regex("""^[A-Za-z0-9_-]{11}$""")

    /**
     * Reads `yt-dlp --flat-playlist -J`. Null when it isn't a playlist. Videos that can't be
     * watched (private, deleted) are left out, and a video listed twice counts once.
     */
    fun parse(json: String): Playlist? {
        val obj = runCatching { JSONObject(json.trim()) }.getOrNull() ?: return null
        if (obj.optString("_type") != "playlist") return null
        val entries = obj.optJSONArray("entries") ?: JSONArray()
        val list = (0 until entries.length()).mapNotNull { i ->
            val e = entries.optJSONObject(i) ?: return@mapNotNull null
            val id = e.optString("id")
            val youtube = e.optString("ie_key").equals("Youtube", ignoreCase = true) || YOUTUBE_ID.matches(id)
            val url = e.optString("url").takeIf { it.startsWith("http") }
                ?: e.optString("webpage_url").takeIf { it.startsWith("http") }
                ?: id.takeIf { youtube && it.isNotEmpty() }?.let { "https://www.youtube.com/watch?v=$it" }
                ?: return@mapNotNull null
            val title = e.optString("title").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
            if (title.equals("[Private video]", true) || title.equals("[Deleted video]", true)) return@mapNotNull null
            Entry(
                url = url,
                title = title,
                durationSec = e.optDouble("duration", 0.0).takeIf { !it.isNaN() }?.toInt()?.coerceAtLeast(0) ?: 0,
                thumbnail = thumbnailOf(e) ?: id.takeIf { youtube && it.isNotEmpty() }?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" }
            )
        }.distinctBy { it.url }
        return Playlist(
            title = obj.optString("title").takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
            uploader = obj.optString("uploader").takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
            entries = list
        )
    }

    /** A thumbnail around 320 px wide: big enough for the list, small enough to load fast. */
    private fun thumbnailOf(e: JSONObject): String? {
        val thumbs = e.optJSONArray("thumbnails")
        if (thumbs != null && thumbs.length() > 0) {
            val all = (0 until thumbs.length()).mapNotNull { thumbs.optJSONObject(it) }
                .filter { it.optString("url").startsWith("http") }
            val sized = all.filter { it.optInt("width") > 0 }
            val pick = sized.filter { it.optInt("width") >= 300 }.minByOrNull { it.optInt("width") }
                ?: sized.maxByOrNull { it.optInt("width") }
                ?: all.lastOrNull()
            pick?.optString("url")?.let { return it }
        }
        return e.optString("thumbnail").takeIf { it.startsWith("http") }
    }
}
