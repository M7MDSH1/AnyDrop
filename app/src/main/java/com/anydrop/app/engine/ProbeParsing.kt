package com.anydrop.app.engine

import com.anydrop.app.engine.YtDlpEngine.FormatInfo

/** Plain-text helpers behind [MediaProbe] (no Android types, so they're unit-tested). */
object ProbeParsing {

    /** ISO-8601 length as used by DASH and schema.org ("PT1H2M3.5S") → whole seconds, 0 if unreadable. */
    fun isoSeconds(text: String): Int {
        val m = Regex("""^P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?$""", RegexOption.IGNORE_CASE)
            .find(text.trim()) ?: return 0
        val (d, h, min, s) = m.destructured
        if (d.isEmpty() && h.isEmpty() && min.isEmpty() && s.isEmpty()) return 0
        val total = (d.toIntOrNull() ?: 0) * 86400 + (h.toIntOrNull() ?: 0) * 3600 +
            (min.toIntOrNull() ?: 0) * 60 + (s.toDoubleOrNull() ?: 0.0)
        return Math.round(total.toDouble()).toInt()
    }

    /** Length of an HLS media playlist; 0 for a live stream (no #EXT-X-ENDLIST). */
    fun hlsSeconds(playlist: String): Int {
        if (!playlist.contains("#EXT-X-ENDLIST")) return 0
        val total = Regex("""#EXTINF:\s*([\d.]+)""").findAll(playlist).sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
        return Math.round(total).toInt()
    }

    /** A variant of an HLS master playlist. */
    data class HlsVariant(val uri: String, val bandwidth: Long, val width: Int, val height: Int)

    /** The variants of an HLS master playlist (empty for a media playlist). */
    fun hlsVariants(master: String): List<HlsVariant> {
        val lines = master.lines().map { it.trim() }
        val out = mutableListOf<HlsVariant>()
        lines.forEachIndexed { i, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF")) return@forEachIndexed
            val uri = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return@forEachIndexed
            val bandwidth = Regex("""(?<![-A-Z])BANDWIDTH=(\d+)""").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val res = Regex("""RESOLUTION=(\d+)x(\d+)""").find(line)
            out += HlsVariant(uri, bandwidth, res?.groupValues?.get(1)?.toIntOrNull() ?: 0, res?.groupValues?.get(2)?.toIntOrNull() ?: 0)
        }
        return out
    }

    /** DASH manifest → (seconds, short side of the largest picture). */
    fun dashInfo(mpd: String): Pair<Int, Int> {
        val seconds = Regex("""mediaPresentationDuration="([^"]+)"""").find(mpd)?.groupValues?.get(1)?.let(::isoSeconds) ?: 0
        val short = Regex("""<Representation\b[^>]*>""").findAll(mpd).mapNotNull { tag ->
            val w = Regex("""\bwidth="(\d+)"""").find(tag.value)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val h = Regex("""\bheight="(\d+)"""").find(tag.value)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            w to h
        }.maxByOrNull { it.first.toLong() * it.second }?.let { minOf(it.first, it.second) } ?: 0
        return seconds to short
    }

    private val UNSIZED = Regex("""\b(Original|Auto|HD|SD)\b( \(\d+\))?""")

    /** A label that names no resolution ("Original", "Auto", "HD (2)", "Video 2 · Original"). */
    fun isUnsized(label: String): Boolean = !Regex("""\d{3,4}p""").containsMatchIn(label) && UNSIZED.containsMatchIn(label)

    /** "Video 2 · Original" with a 720-pixel short side → "Video 2 · 720p". */
    fun relabel(label: String, shortSide: Int): String =
        if (shortSide > 0 && isUnsized(label)) label.replace(UNSIZED, "${shortSide}p") else label

    /**
     * Keeps every entry's label unique within its video (a picker lists them by label), adding
     * " (2)", " (3)"… to repeats. Photos keep their own labels.
     */
    fun uniqueLabels(list: List<FormatInfo>): List<FormatInfo> {
        val used = mutableMapOf<String, MutableSet<String>>()
        return list.map { f ->
            if (f.isImage) return@map f
            val taken = used.getOrPut(f.group) { mutableSetOf() }
            var label = f.resolution
            if (label in taken) {
                val base = label.replace(Regex(""" \(\d+\)$"""), "")
                var n = 2
                while ("$base ($n)" in taken) n++
                label = "$base ($n)"
            }
            taken += label
            if (label == f.resolution) f else f.copy(resolution = label)
        }
    }
}
