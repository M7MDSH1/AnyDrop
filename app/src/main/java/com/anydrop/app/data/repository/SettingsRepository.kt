package com.anydrop.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("anydrop_settings", Context.MODE_PRIVATE)

    var potServerUrl: String
        get() = prefs.getString(KEY_POT_URL, DEFAULT_POT_URL) ?: DEFAULT_POT_URL
        set(value) = prefs.edit().putString(KEY_POT_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    /** Maximum video height the user wants offered, in pixels (0 = best available). */
    var maxQualityHeight: Int
        get() = prefs.getInt(KEY_MAX_QUALITY, MAX_QUALITY_BEST)
        set(value) = prefs.edit().putInt(KEY_MAX_QUALITY, value).apply()

    /** Watch the clipboard while the deck is open and offer detected links. */
    var autoDetectLinks: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DETECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_DETECT, value).apply()

    companion object {
        private const val KEY_POT_URL = "pot_server_url"
        private const val KEY_API_KEY = "pot_api_key"
        private const val KEY_MAX_QUALITY = "max_quality_height"
        private const val KEY_AUTO_DETECT = "auto_detect_links"
        const val DEFAULT_POT_URL = "http://127.0.0.1:4416"

        const val MAX_QUALITY_BEST = 4320
        const val MAX_QUALITY_4K = 2160
        const val MAX_QUALITY_1080P = 1080
        const val MAX_QUALITY_720P = 720
        const val MAX_QUALITY_480P = 480
    }
}
