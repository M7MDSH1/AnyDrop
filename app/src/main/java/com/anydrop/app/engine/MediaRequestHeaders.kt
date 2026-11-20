package com.anydrop.app.engine

import com.anydrop.app.engine.YtDlpEngine.FormatInfo

/** Retain only request context we need, never arbitrary browser headers or credentials. */
internal fun browserRequestHeader(headers: Map<String, String>, name: String): String =
    headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        ?.takeIf { it.isNotBlank() && '\r' !in it && '\n' !in it }.orEmpty()

/** Shared by probing and downloading so both replay the same embedded-player context. */
internal fun FormatInfo.directRequestHeaders(pageUrl: String): Map<String, String> = buildMap {
    val values = mapOf("User-Agent" to userAgent, "Referer" to referer.ifBlank { pageUrl }, "Origin" to origin)
    for ((name, value) in values) {
        val safe = browserRequestHeader(mapOf(name to value), name)
        if (safe.isNotBlank()) put(name, safe)
    }
}
