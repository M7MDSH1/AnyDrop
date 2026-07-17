package com.anydrop.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A login platform the user can authenticate with to grab cookies.
 * @param name      Display name (e.g. "Instagram", "Facebook").
 * @param loginUrl  Page to open in the WebView for logging in.
 * @param domain    Cookie domain yt-dlp matches on (e.g. "instagram.com").
 * @param builtIn   True for shipped defaults (can't be deleted).
 */
data class LoginPlatform(
    val name: String,
    val loginUrl: String,
    val domain: String,
    val builtIn: Boolean = false
)

/**
 * Stores the user's login platforms in SharedPreferences (JSON). SharedPreferences
 * survive app updates automatically — only an uninstall clears them — so the user's
 * added platforms (and their saved cookies in filesDir/cookies.txt) persist across
 * updates with no re-entry needed.
 */
@Singleton
class LoginPlatformRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("anydrop_login_platforms", Context.MODE_PRIVATE)

    private val builtIns = listOf(
        LoginPlatform(
            name = "Instagram",
            loginUrl = "https://www.instagram.com/accounts/login/?hl=en",
            domain = "instagram.com",
            builtIn = true
        )
    )

    private val _platforms = MutableStateFlow(loadAll())
    val platforms: StateFlow<List<LoginPlatform>> = _platforms.asStateFlow()

    private fun loadAll(): List<LoginPlatform> = builtIns + loadCustom()

    private fun loadCustom(): List<LoginPlatform> {
        val raw = prefs.getString(KEY_PLATFORMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LoginPlatform(
                    name = o.getString("name"),
                    loginUrl = o.getString("loginUrl"),
                    domain = o.getString("domain"),
                    builtIn = false
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCustom(custom: List<LoginPlatform>) {
        val arr = JSONArray()
        custom.forEach { p ->
            arr.put(
                JSONObject()
                    .put("name", p.name)
                    .put("loginUrl", p.loginUrl)
                    .put("domain", p.domain)
            )
        }
        prefs.edit().putString(KEY_PLATFORMS, arr.toString()).apply()
        _platforms.value = loadAll()
    }

    /** Add a custom platform. Domain is derived from the URL if not given. */
    fun addPlatform(name: String, loginUrl: String, domain: String? = null) {
        val cleanName = name.trim()
        val cleanUrl = loginUrl.trim().let { if (it.startsWith("http")) it else "https://$it" }
        val cleanDomain = (domain?.takeIf { it.isNotBlank() } ?: deriveDomain(cleanUrl)).trim()
        if (cleanName.isBlank() || cleanUrl.isBlank() || cleanDomain.isBlank()) return
        val custom = loadCustom().toMutableList()
        // Replace if a platform with the same name already exists.
        custom.removeAll { it.name.equals(cleanName, ignoreCase = true) }
        custom.add(LoginPlatform(cleanName, cleanUrl, cleanDomain, builtIn = false))
        saveCustom(custom)
    }

    fun removePlatform(name: String) {
        val custom = loadCustom().filterNot { it.name.equals(name, ignoreCase = true) }
        saveCustom(custom)
    }

    /** Strip scheme/path/www to a bare registrable domain for cookie matching. */
    private fun deriveDomain(url: String): String {
        return url
            .substringAfter("//")
            .substringBefore("/")
            .substringBefore("?")
            .removePrefix("www.")
    }

    companion object {
        private const val KEY_PLATFORMS = "custom_platforms"
    }
}
