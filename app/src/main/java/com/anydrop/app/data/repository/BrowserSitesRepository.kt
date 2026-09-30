package com.anydrop.app.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** A shortcut on the browser's start page. [argb] tints its tile. */
data class BrowserSite(val name: String, val url: String, val argb: Long)

/**
 * The browser start page's sites. The user can add their own, edit or delete any of
 * them (the shipped ones included) and restore the defaults. Saved as JSON in
 * SharedPreferences; until the user changes anything the defaults are used as is.
 */
@Singleton
class BrowserSitesRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("anydrop_browser_sites", Context.MODE_PRIVATE)

    private val _sites = MutableStateFlow(load())
    val sites: StateFlow<List<BrowserSite>> = _sites.asStateFlow()

    fun add(name: String, url: String) {
        val site = BrowserSite(name, url, tintFor(url))
        save(_sites.value + site)
    }

    fun update(old: BrowserSite, name: String, url: String) {
        // Keep the tile's colour unless the site itself changed.
        val argb = if (hostOf(old.url) == hostOf(url)) old.argb else tintFor(url)
        save(_sites.value.map { if (it == old) BrowserSite(name, url, argb) else it })
    }

    fun remove(site: BrowserSite) = save(_sites.value - site)

    fun moveToTop(site: BrowserSite) = save(listOf(site) + (_sites.value - site))

    fun restoreDefaults() {
        prefs.edit().remove(KEY_SITES).apply()
        _sites.value = DEFAULT_SITES
    }

    private fun save(list: List<BrowserSite>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url).put("argb", it.argb)) }
        prefs.edit().putString(KEY_SITES, arr.toString()).apply()
        _sites.value = list
    }

    private fun load(): List<BrowserSite> {
        val raw = prefs.getString(KEY_SITES, null) ?: return DEFAULT_SITES
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BrowserSite(o.getString("name"), o.getString("url"), o.optLong("argb", tintFor(o.getString("url"))))
            }
        }.getOrDefault(DEFAULT_SITES)
    }

    companion object {
        private const val KEY_SITES = "sites"

        val DEFAULT_SITES = listOf(
            BrowserSite("Instagram", "https://www.instagram.com/", 0xFFE1306C),
            BrowserSite("TikTok", "https://www.tiktok.com/", 0xFF25F4EE),
            BrowserSite("YouTube", "https://m.youtube.com/", 0xFFFF3B30),
            BrowserSite("Facebook", "https://m.facebook.com/watch/", 0xFF1877F2),
            BrowserSite("X", "https://x.com/", 0xFF9E9E9E),
            BrowserSite("Reddit", "https://www.reddit.com/", 0xFFFF4500),
            BrowserSite("Vimeo", "https://vimeo.com/", 0xFF1AB7EA),
            BrowserSite("Pinterest", "https://www.pinterest.com/", 0xFFE60023),
            BrowserSite("Dailymotion", "https://www.dailymotion.com/", 0xFF0A7CFF),
            BrowserSite("Twitch", "https://m.twitch.tv/", 0xFF9146FF),
            BrowserSite("Threads", "https://www.threads.net/", 0xFFBDBDBD),
            BrowserSite("Snapchat", "https://www.snapchat.com/spotlight", 0xFFFFD600),
        )

        // Tile colours for the user's own sites, picked from the host so a site keeps its colour.
        private val TINTS = longArrayOf(
            0xFFFFB000, 0xFF26C6DA, 0xFF7E57C2, 0xFF66BB6A, 0xFFEF5350,
            0xFF42A5F5, 0xFFEC407A, 0xFF8D6E63, 0xFF26A69A, 0xFFFFA726
        )

        fun tintFor(url: String): Long = TINTS[Math.floorMod(hostOf(url).hashCode(), TINTS.size)]

        fun hostOf(url: String): String =
            runCatching { Uri.parse(url).host }.getOrNull().orEmpty().removePrefix("www.").removePrefix("m.")

        /**
         * Turns what the user typed into a web address: adds https:// when missing and
         * rejects anything that is not a plain http(s) address with a dotted host.
         */
        fun normalizeUrl(input: String): String? {
            val t = input.trim()
            if (t.isEmpty() || t.contains(' ')) return null
            val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(t)) t else "https://$t"
            val uri = runCatching { Uri.parse(withScheme) }.getOrNull() ?: return null
            if (uri.scheme != "https" && uri.scheme != "http") return null
            val host = uri.host ?: return null
            if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) return null
            return withScheme
        }

        /**
         * A short name from a page title, for "Add to start page": the title part that is
         * the site's name ("TikTok - Make Your Day" → "TikTok"), else its first part.
         */
        fun suggestName(pageTitle: String, url: String): String {
            val parts = pageTitle.split(Regex("""\s+[-|–—·:]\s+""")).map { it.trim() }.filter { it.isNotEmpty() }
            val site = defaultName(url)
            return parts.firstOrNull { it.equals(site, ignoreCase = true) }
                ?: parts.firstOrNull()?.takeIf { it.length <= 30 }
                ?: site
        }

        /** A name for a site the user did not name: "news.example.com" → "Example". */
        fun defaultName(url: String): String {
            val parts = hostOf(url).split('.').filter { it.isNotEmpty() }
            val core = if (parts.size >= 2) parts[parts.size - 2] else parts.firstOrNull().orEmpty()
            return core.replaceFirstChar { it.uppercase() }
        }
    }
}
