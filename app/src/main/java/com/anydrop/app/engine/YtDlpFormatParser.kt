package com.anydrop.app.engine

import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import org.json.JSONObject

/**
 * Pure parsing helpers for yt-dlp output, extracted from [YtDlpEngine] so the
 * logic can be unit-tested on the JVM (no Android/native dependency).
 */
object YtDlpFormatParser {

    /** First number in a resolution label, e.g. "1080p60" -> 1080, "Audio 128kbps" -> 128. */
    fun extractHeight(resolution: String): Int {
        return Regex("(\\d+)").find(resolution)?.value?.toIntOrNull() ?: 0
    }

    fun imageExtFromUrl(url: String): String {
        val clean = url.substringBefore("?").lowercase()
        return listOf("jpg", "jpeg", "png", "webp", "heic", "gif").firstOrNull { clean.endsWith(".$it") } ?: "jpg"
    }

    fun isImageUrl(url: String): Boolean {
        val c = url.substringBefore("?").lowercase()
        return listOf(".jpg", ".jpeg", ".png", ".webp", ".heic").any { c.endsWith(it) } ||
            url.contains("cdninstagram") || url.contains("scontent")
    }

    /**
     * Parse the `-J` dump of a carousel/multi-image post into one [FormatInfo]
     * per slide. For each node, prefer an explicit image url, then display_url,
     * then the largest thumbnail, then thumbnail. Duplicate urls collapse.
     */
    fun parseCarouselImages(jsonText: String): List<FormatInfo> {
        return try {
            val json = JSONObject(jsonText)

            fun bestImageFromNode(node: JSONObject): String? {
                node.optString("url").takeIf { it.startsWith("http") && isImageUrl(it) }?.let { return it }
                node.optString("display_url").takeIf { it.startsWith("http") }?.let { return it }
                val thumbs = node.optJSONArray("thumbnails")
                if (thumbs != null && thumbs.length() > 0) {
                    // thumbnails are usually ordered small→large; take the last.
                    for (i in thumbs.length() - 1 downTo 0) {
                        val u = thumbs.getJSONObject(i).optString("url")
                        if (u.startsWith("http")) return u
                    }
                }
                node.optString("thumbnail").takeIf { it.startsWith("http") }?.let { return it }
                return null
            }

            val urls = mutableListOf<String>()
            val entries = json.optJSONArray("entries")
            if (entries != null && entries.length() > 0) {
                for (i in 0 until entries.length()) {
                    val entry = entries.optJSONObject(i) ?: continue
                    bestImageFromNode(entry)?.let { urls.add(it) }
                }
            } else {
                // Not a playlist — single image node.
                bestImageFromNode(json)?.let { urls.add(it) }
            }

            val distinct = urls.distinct()
            distinct.mapIndexed { i, u ->
                FormatInfo(
                    id = "",
                    extension = imageExtFromUrl(u),
                    resolution = if (distinct.size > 1) "Image ${i + 1}/${distinct.size}" else "Image (photo)",
                    filesize = 0L,
                    vcodec = "none",
                    acodec = "none",
                    isImage = true,
                    imageUrl = u
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
