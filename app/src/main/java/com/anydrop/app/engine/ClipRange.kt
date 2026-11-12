package com.anydrop.app.engine

import java.util.Locale

/** Times for saving only a part of a video ("from 1:05 to 2:10"). */
object ClipRange {

    /** "1:05", "1:02:03" or "75" (seconds) as seconds; null when it isn't a time. */
    fun parse(text: String): Int? {
        val parts = text.trim().split(':')
        if (parts.isEmpty() || parts.size > 3 || parts.any { it.isEmpty() || it.length > 5 || !it.all(Char::isDigit) }) return null
        // Only the first part may run past 59 ("90:00" is an hour and a half).
        if (parts.drop(1).any { it.toInt() > 59 }) return null
        return parts.fold(0) { total, part -> total * 60 + part.toInt() }
    }

    /** Seconds as "1:05" or "1:02:03". */
    fun format(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = s % 3600 / 60
        val sec = s % 60
        // Always Western digits: the times also go into file names and are read back by parse.
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    /** True when [start] to [end] is a real stretch of a video [durationSec] long (0 = length unknown). */
    fun isValid(start: Int?, end: Int?, durationSec: Int): Boolean =
        start != null && end != null && start >= 0 && end > start && (durationSec <= 0 || end <= durationSec)

    /** The value for yt-dlp's --download-sections. */
    fun section(start: Int, end: Int): String = "*$start-$end"

    /** For the file name, which keeps only letters and digits: "clip1m05to2m10". */
    fun label(start: Int, end: Int): String = "clip" + format(start).forFileName() + "to" + format(end).forFileName()

    // "1:02:03" -> "1h02m03", "1:05" -> "1m05"
    private fun String.forFileName(): String =
        if (count { it == ':' } == 2) replaceFirst(":", "h").replace(":", "m") else replace(":", "m")
}
