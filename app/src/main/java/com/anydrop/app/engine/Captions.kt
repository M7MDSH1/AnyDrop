package com.anydrop.app.engine

import org.json.JSONObject

/** Subtitles a video offers, from yt-dlp's JSON (`subtitles` and `automatic_captions`). */
object Captions {

    /**
     * One subtitle choice. [language] is what yt-dlp is asked for ("en", or "en-orig" for
     * YouTube's automatic captions in the video's own language); [auto] = made by the site.
     */
    data class Caption(val language: String, val auto: Boolean)

    /**
     * The choices worth offering: the creator's own subtitles, then the site's automatic ones in
     * the video's language and in [appLanguage] (YouTube translates its automatic captions into
     * a hundred-odd languages; listing them all would bury the useful ones).
     */
    fun parse(json: String, appLanguage: String): List<Caption> {
        val obj = runCatching { JSONObject(json.trim()) }.getOrNull() ?: return emptyList()
        val manual = obj.optJSONObject("subtitles")?.keys()?.asSequence()?.toList().orEmpty()
            .filter { it.isNotBlank() && it != "live_chat" }
        val auto = obj.optJSONObject("automatic_captions")?.keys()?.asSequence()?.toList().orEmpty()
        val wantedAuto = auto.filter { lang ->
            lang.endsWith("-orig") || (appLanguage.isNotBlank() && baseOf(lang) == appLanguage && lang == appLanguage)
        }
        val manualBases = manual.map(::baseOf).toSet()
        return manual.sortedBy { it }.map { Caption(it, auto = false) } +
            wantedAuto.filter { baseOf(it) !in manualBases }.map { Caption(it, auto = true) }
    }

    /** "en-orig" / "en-US" → "en". */
    fun baseOf(language: String): String = language.substringBefore('-').lowercase()
}
