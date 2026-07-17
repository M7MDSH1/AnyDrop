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

    companion object {
        private const val KEY_POT_URL = "pot_server_url"
        private const val KEY_API_KEY = "pot_api_key"
        const val DEFAULT_POT_URL = "http://127.0.0.1:4416"
    }
}
