package com.anydrop.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("anydrop_settings", Context.MODE_PRIVATE)

    private val _darkMode = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, true))

    /**
     * Live dark-mode state. Backed by a SharedPreferences listener so ANY writer
     * (any ViewModel/screen) instantly updates every collector — the theme flips
     * the moment the setting flips, with no restart.
     */
    val darkModeFlow: StateFlow<Boolean> = _darkMode.asStateFlow()

    private val darkModeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_DARK_MODE) {
            _darkMode.value = prefs.getBoolean(KEY_DARK_MODE, true)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(darkModeListener)
    }

    /** Maximum video height the user wants offered, in pixels (0 = best available). */
    var maxQualityHeight: Int
        get() = prefs.getInt(KEY_MAX_QUALITY, MAX_QUALITY_BEST)
        set(value) = prefs.edit().putInt(KEY_MAX_QUALITY, value).apply()

    /** Watch the clipboard while the deck is open and offer detected links. */
    var autoDetectLinks: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DETECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_DETECT, value).apply()

    /** Dark mode is the default; false switches the whole UI to the light palette. */
    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    /** Last time (epoch millis) a yt-dlp update check was attempted, to throttle it. */
    fun lastYtDlpUpdateCheck(): Long = prefs.getLong(KEY_YTDLP_LAST_UPDATE, 0L)

    fun markYtDlpUpdateCheck() {
        prefs.edit().putLong(KEY_YTDLP_LAST_UPDATE, System.currentTimeMillis()).apply()
    }

    companion object {
        private const val KEY_MAX_QUALITY = "max_quality_height"
        private const val KEY_AUTO_DETECT = "auto_detect_links"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_YTDLP_LAST_UPDATE = "ytdlp_last_update"

        const val MAX_QUALITY_BEST = 4320
        const val MAX_QUALITY_4K = 2160
        const val MAX_QUALITY_1080P = 1080
        const val MAX_QUALITY_720P = 720
        const val MAX_QUALITY_480P = 480
    }
}
