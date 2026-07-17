package com.anydrop.app.util

import android.net.Uri
import com.anydrop.app.data.model.MediaSource

fun String?.detectSource(): MediaSource {
    if (this == null) return MediaSource.UNKNOWN
    val url = lowercase()
    return when {
        url.contains("instagram.com") || url.contains("instagr.am") -> MediaSource.INSTAGRAM
        url.contains("tiktok.com") -> MediaSource.TIKTOK
        url.contains("youtube.com") || url.contains("youtu.be") -> MediaSource.YOUTUBE
        url.contains("facebook.com") || url.contains("fb.watch") -> MediaSource.FACEBOOK
        url.contains("reddit.com") || url.contains("redd.it") -> MediaSource.REDDIT
        url.contains("twitter.com") || url.contains("x.com") -> MediaSource.TWITTER
        else -> MediaSource.UNKNOWN
    }
}

fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

fun Long.formatFileSize(): String {
    val kb = this / 1024f
    val mb = kb / 1024f
    val gb = mb / 1024f
    return when {
        gb >= 1.0f -> "%.1f GB".format(gb)
        mb >= 1.0f -> "%.1f MB".format(mb)
        kb >= 1.0f -> "%.0f KB".format(kb)
        else -> "$this B"
    }
}

fun Uri.isValidMediaUrl(): Boolean {
    val url = toString()
    return Constants.SUPPORTED_DOMAINS.any { url.contains(it, ignoreCase = true) }
}
