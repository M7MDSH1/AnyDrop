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
     * Live dark-mode state and the single source of truth for the theme. [darkMode]'s
     * setter updates it directly, so the theme flips the moment the setting flips.
     */
    val darkModeFlow: StateFlow<Boolean> = _darkMode.asStateFlow()

    // Safety net for a write that bypasses [darkMode] (e.g. prefs edited elsewhere).
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

    /**
     * What a pasted or shared link fetches: false = that link's video (the default), true = the
     * whole page (every video on it and the videos it links to, to pick from).
     */
    var fetchWholePage: Boolean
        get() = prefs.getBoolean(KEY_WHOLE_PAGE, false)
        set(value) = prefs.edit().putBoolean(KEY_WHOLE_PAGE, value).apply()

    /**
     * Movie mode in the video player: swiping seeks within the video instead of moving to the
     * next one, and the previous/next buttons are hidden. Kept until the user turns it off.
     */
    var movieMode: Boolean
        get() = prefs.getBoolean(KEY_MOVIE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_MOVIE_MODE, value).apply()

    /**
     * The voice picked last for a video with several audio languages (a language code), so the
     * next such video starts with it when it has it.
     */
    var preferredAudioLanguage: String
        get() = prefs.getString(KEY_AUDIO_LANGUAGE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_AUDIO_LANGUAGE, value).apply()

    /** Subtitles picked last (a language code, blank = off), offered first when a video has them. */
    var preferredSubtitleLanguage: String
        get() = prefs.getString(KEY_SUBTITLE_LANGUAGE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SUBTITLE_LANGUAGE, value).apply()

    /** Downloads wait for Wi-Fi (or Ethernet) instead of using mobile data. */
    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    /** How many downloads run together; the rest wait their turn (playlists add many at once). */
    var maxParallelDownloads: Int
        get() = prefs.getInt(KEY_MAX_PARALLEL, DEFAULT_PARALLEL).coerceIn(1, MAX_PARALLEL)
        set(value) = prefs.edit().putInt(KEY_MAX_PARALLEL, value.coerceIn(1, MAX_PARALLEL)).apply()

    /** Watch the clipboard while the deck is open and offer detected links. */
    var autoDetectLinks: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DETECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_DETECT, value).apply()

    /**
     * Dark mode is the default; false switches the whole UI to the light palette.
     * The setter updates [darkModeFlow] itself instead of waiting on the prefs
     * listener, so the theme can never miss a switch.
     */
    var darkMode: Boolean
        get() = _darkMode.value
        set(value) {
            prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
            _darkMode.value = value
        }

    /** Flips the mode the user is currently looking at. */
    fun toggleDarkMode() {
        darkMode = !_darkMode.value
    }

    /** Last time (epoch millis) a yt-dlp update check was attempted, to throttle it. */
    fun lastYtDlpUpdateCheck(): Long = prefs.getLong(KEY_YTDLP_LAST_UPDATE, 0L)

    fun markYtDlpUpdateCheck() {
        prefs.edit().putLong(KEY_YTDLP_LAST_UPDATE, System.currentTimeMillis()).apply()
    }

    companion object {
        private const val KEY_MAX_QUALITY = "max_quality_height"
        private const val KEY_AUTO_DETECT = "auto_detect_links"
        private const val KEY_WHOLE_PAGE = "fetch_whole_page"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_MOVIE_MODE = "player_movie_mode"
        private const val KEY_AUDIO_LANGUAGE = "preferred_audio_language"
        private const val KEY_SUBTITLE_LANGUAGE = "preferred_subtitle_language"
        private const val KEY_WIFI_ONLY = "download_wifi_only"
        private const val KEY_MAX_PARALLEL = "download_max_parallel"

        const val DEFAULT_PARALLEL = 3
        const val MAX_PARALLEL = 5
        private const val KEY_YTDLP_LAST_UPDATE = "ytdlp_last_update"

        const val MAX_QUALITY_BEST = 4320
        const val MAX_QUALITY_4K = 2160
        const val MAX_QUALITY_1080P = 1080
        const val MAX_QUALITY_720P = 720
        const val MAX_QUALITY_480P = 480
    }
}
