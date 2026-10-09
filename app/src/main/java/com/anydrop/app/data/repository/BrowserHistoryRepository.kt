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

/** A page the in-app browser visited. */
data class HistoryEntry(val url: String, val title: String, val visitedAt: Long)

/**
 * The in-app browser's history: every page visited, newest first, one entry per address (a
 * page visited again moves to the top). Kept on the device only (SharedPreferences JSON),
 * capped at [HistoryList.MAX] entries; entries can be removed one by one or all at once.
 */
@Singleton
class BrowserHistoryRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("anydrop_browser_history", Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    fun record(url: String, title: String) {
        val updated = HistoryList.record(_entries.value, url, title, System.currentTimeMillis())
        if (updated !== _entries.value) save(updated)
    }

    fun remove(entry: HistoryEntry) = save(_entries.value.filterNot { it.url == entry.url })

    fun clear() = save(emptyList())

    private fun save(list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title).put("at", it.visitedAt)) }
        prefs.edit().putString(KEY_ENTRIES, arr.toString()).apply()
        _entries.value = list
    }

    private fun load(): List<HistoryEntry> = HistoryList.fromJson(prefs.getString(KEY_ENTRIES, null))

    private companion object {
        const val KEY_ENTRIES = "entries"
    }
}

/** The history list's rules, kept free of Android so they are unit-tested. */
object HistoryList {
    const val MAX = 300

    /**
     * [list] with a visit to [url] at the top. Visiting the page that is already on top only
     * updates its title (a page renames itself while it loads), and returns [list] itself when
     * nothing changed.
     */
    fun record(list: List<HistoryEntry>, url: String, title: String, now: Long): List<HistoryEntry> {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return list
        val cleanTitle = title.trim().takeUnless { looksLikeAddress(it, url) }.orEmpty()
        val top = list.firstOrNull()
        if (top != null && sameAddress(top.url, url)) {
            if (cleanTitle.isEmpty() || cleanTitle == top.title) return list
            return listOf(top.copy(title = cleanTitle)) + list.drop(1)
        }
        val previous = list.firstOrNull { sameAddress(it.url, url) }
        val entry = HistoryEntry(url, cleanTitle.ifEmpty { previous?.title.orEmpty() }, now)
        return (listOf(entry) + list.filterNot { sameAddress(it.url, url) }).take(MAX)
    }

    /** Entries whose title or address contains [query] (all of them, newest first, when blank). */
    fun search(list: List<HistoryEntry>, query: String, limit: Int): List<HistoryEntry> {
        val q = query.trim()
        val matches = if (q.isEmpty()) list else list.filter { it.title.contains(q, ignoreCase = true) || it.url.contains(q, ignoreCase = true) }
        return matches.take(limit)
    }

    /** 0 for today, 1 for yesterday, n for n days ago (by local calendar day). */
    fun daysAgo(time: Long, now: Long, zoneOffsetMs: Long): Long {
        val day = 24L * 60 * 60 * 1000
        return Math.floorDiv(now + zoneOffsetMs, day) - Math.floorDiv(time + zoneOffsetMs, day)
    }

    fun sameAddress(a: String, b: String): Boolean = a.trimEnd('/') == b.trimEnd('/')

    // WebView reports the address as the title until the page names itself; don't keep that.
    private fun looksLikeAddress(title: String, url: String): Boolean {
        val bare = url.removePrefix("https://").removePrefix("http://").trimEnd('/')
        val t = title.removePrefix("https://").removePrefix("http://").trimEnd('/')
        return t == bare || title.startsWith("http://") || title.startsWith("https://")
    }

    fun fromJson(raw: String?): List<HistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url").takeIf { it.startsWith("http") } ?: return@mapNotNull null
                HistoryEntry(url, o.optString("title"), o.optLong("at"))
            }
        }.getOrDefault(emptyList())
    }
}
