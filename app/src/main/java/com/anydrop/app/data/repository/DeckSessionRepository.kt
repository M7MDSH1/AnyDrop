package com.anydrop.app.data.repository

import android.content.Context
import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray

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

    /** Persist URL + the freshly fetched format list (raw, uncapped) + selection + the page's title. */
    fun saveSession(url: String, formats: List<FormatInfo>, selectedFormatId: String, title: String = "") {
        prefs.edit()
            .putString(KEY_URL, url)
            .putString(KEY_FORMATS, formatsToJson(formats))
            .putString(KEY_SELECTED_ID, selectedFormatId)
            .putString(KEY_TITLE, title)
            .apply()
    }

    fun lastTitle(): String = prefs.getString(KEY_TITLE, "").orEmpty()

    fun loadFormats(): List<FormatInfo> {
        val raw = prefs.getString(KEY_FORMATS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> FormatJson.fromJson(arr.getJSONObject(i)) }
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
        private const val KEY_TITLE = "last_title"
        private const val KEY_LAST_TAB = "last_tab"
        private const val KEY_LAST_PLAYED = "last_played_media"

        private fun formatsToJson(formats: List<FormatInfo>): String =
            JSONArray().apply { formats.forEach { put(FormatJson.toJson(it)) } }.toString()
    }
}