package com.anydrop.app.data.repository

import android.content.Context
import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the deck's working session (last URL, fetched format list, selected
 * format), the last active bottom-nav tab, and the last played media id, so a
 * closed or killed app reopens looking the way the user left it. SharedPreferences
 * (not DataStore) so the last tab is readable synchronously for the NavHost
 * start destination.
 */
@Singleton
class DeckSessionRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("anydrop_deck_session", Context.MODE_PRIVATE)

    fun lastUrl(): String = prefs.getString(KEY_URL, "").orEmpty()

    fun saveUrl(url: String) {
        prefs.edit().putString(KEY_URL, url).apply()
    }

    /** Persist URL + the freshly fetched format list (raw, uncapped) + selection. */
    fun saveSession(url: String, formats: List<FormatInfo>, selectedFormatId: String) {
        prefs.edit()
            .putString(KEY_URL, url)
            .putString(KEY_FORMATS, formatsToJson(formats))
            .putString(KEY_SELECTED_ID, selectedFormatId)
            .apply()
    }

    fun loadFormats(): List<FormatInfo> {
        val raw = prefs.getString(KEY_FORMATS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        FormatInfo(
                            id = o.optString("id"),
                            extension = o.optString("extension"),
                            resolution = o.optString("resolution"),
                            filesize = o.optLong("filesize"),
                            vcodec = o.optString("vcodec"),
                            acodec = o.optString("acodec"),
                            isAudioOnly = o.optBoolean("isAudioOnly"),
                            abr = o.optInt("abr"),
                            isImage = o.optBoolean("isImage"),
                            imageUrl = o.optString("imageUrl"),
                            directUrl = o.optString("directUrl"),
                            userAgent = o.optString("userAgent")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun selectedFormatId(): String = prefs.getString(KEY_SELECTED_ID, "").orEmpty()

    fun lastTab(default: String): String = prefs.getString(KEY_LAST_TAB, default) ?: default

    fun saveTab(route: String) {
        prefs.edit().putString(KEY_LAST_TAB, route).apply()
    }

    fun lastPlayedMediaId(): String = prefs.getString(KEY_LAST_PLAYED, "").orEmpty()

    fun saveLastPlayedMediaId(mediaId: String) {
        prefs.edit().putString(KEY_LAST_PLAYED, mediaId).apply()
    }

    companion object {
        private const val KEY_URL = "last_url"
        private const val KEY_FORMATS = "last_formats"
        private const val KEY_SELECTED_ID = "last_selected_format"
        private const val KEY_LAST_TAB = "last_tab"
        private const val KEY_LAST_PLAYED = "last_played_media"

        private fun formatsToJson(formats: List<FormatInfo>): String {
            val arr = JSONArray()
            formats.forEach { f ->
                arr.put(
                    JSONObject().apply {
                        put("id", f.id)
                        put("extension", f.extension)
                        put("resolution", f.resolution)
                        put("filesize", f.filesize)
                        put("vcodec", f.vcodec)
                        put("acodec", f.acodec)
                        put("isAudioOnly", f.isAudioOnly)
                        put("abr", f.abr)
                        put("isImage", f.isImage)
                        put("imageUrl", f.imageUrl)
                        // Media found on a web page: without these a restored session couldn't download it.
                        put("directUrl", f.directUrl)
                        put("userAgent", f.userAgent)
                    }
                )
            }
            return arr.toString()
        }
    }
}