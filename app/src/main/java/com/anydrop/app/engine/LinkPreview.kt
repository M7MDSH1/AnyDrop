package com.anydrop.app.engine

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/**
 * A link's title and cover from the site's own "oEmbed" address. It answers in a moment,
 * so the deck can show what the link is while its qualities are still being read.
 */
object LinkPreview {

    data class Preview(val title: String, val author: String, val cover: String)

    /** Where to ask about [url], or null for a site without a preview address we know. */
    fun endpointFor(url: String): String? {
        val link = url.trim()
        val host = runCatching { URI(link).host }.getOrNull()?.lowercase() ?: return null
        fun on(domain: String) = host == domain || host.endsWith(".$domain")
        val encoded = URLEncoder.encode(link, "UTF-8")
        return when {
            on("youtube.com") || on("youtu.be") -> "https://www.youtube.com/oembed?format=json&url=$encoded"
            on("tiktok.com") -> "https://www.tiktok.com/oembed?url=$encoded"
            on("vimeo.com") -> "https://vimeo.com/api/oembed.json?url=$encoded"
            on("dailymotion.com") || on("dai.ly") -> "https://www.dailymotion.com/services/oembed?format=json&url=$encoded"
            on("soundcloud.com") -> "https://soundcloud.com/oembed?format=json&url=$encoded"
            else -> null
        }
    }

    /** Blocking. Null when the site has no preview or doesn't answer quickly. */
    fun fetch(url: String): Preview? {
        val endpoint = endpointFor(url) ?: return null
        return try {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
            }
            try {
                if (connection.responseCode != 200) return null
                // An answer is a few hundred bytes; anything huge isn't one.
                val text = connection.inputStream.use { it.readNBytesCompat(MAX_BYTES) }.toString(Charsets.UTF_8)
                val json = JSONObject(text)
                Preview(
                    title = json.optString("title").trim(),
                    author = json.optString("author_name").trim(),
                    cover = json.optString("thumbnail_url").trim().takeIf { it.startsWith("http") }.orEmpty()
                ).takeIf { it.title.isNotBlank() || it.cover.isNotBlank() }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    private const val MAX_BYTES = 200_000

    // InputStream.readNBytes needs API 33.
    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
