package com.anydrop.app.engine

import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import java.util.Locale
import kotlin.math.abs

/** One video (or photo) of a whole-page fetch, with the qualities it can be saved in (best first). */
data class PageItem(val key: String, val formats: List<FormatInfo>) {
    val first: FormatInfo get() = formats.first()
    val isMain: Boolean get() = key == YtDlpEngine.MAIN_GROUP
    val isLink: Boolean get() = first.isLink
    val isImage: Boolean get() = first.isImage
    val cover: String get() = formats.firstNotNullOfOrNull { it.previewUrl.ifBlank { null } } ?: first.imageUrl
    val durationSec: Int get() = formats.maxOf { it.durationSec }

    /** The item's own title, else the page's. */
    fun title(pageTitle: String): String = formats.firstNotNullOfOrNull { it.title.ifBlank { null } } ?: pageTitle
}

/** Whole-page results (entries carry a [FormatInfo.group]). */
val List<FormatInfo>.isWholePage: Boolean get() = any { it.group.isNotBlank() }

/** The videos of a playlist (see [YtDlpEngine.getPlaylist]). */
val List<FormatInfo>.isPlaylist: Boolean get() = any { it.group.startsWith(YtDlpEngine.PLAYLIST_GROUP) }

/** The same video, saved as its sound only (.m4a). */
fun FormatInfo.asAudio(): FormatInfo = FormatInfo(
    id = "bestaudio",
    extension = "m4a",
    resolution = "Audio",
    filesize = 0L,
    vcodec = "none",
    acodec = "audio",
    isAudioOnly = true,
    previewUrl = previewUrl,
    durationSec = durationSec,
    title = title,
    group = group,
    pageUrl = pageUrl
)

/** The videos and photos of a whole-page fetch, in the order found (the page's own video first). */
fun List<FormatInfo>.pageItems(): List<PageItem> =
    filter { it.group.isNotBlank() && !it.isAudioOnly }
        .groupBy { it.group }
        .map { (key, list) -> PageItem(key, list) }

/**
 * Drops video files found on the page that are just the page's own video again (yt-dlp already
 * lists it in every quality): same length, give or take a second.
 */
fun List<FormatInfo>.withoutRepeatsOfMain(): List<FormatInfo> {
    val mainLength = filter { it.group == YtDlpEngine.MAIN_GROUP }.maxOfOrNull { it.durationSec } ?: 0
    if (mainLength <= 0) return this
    val repeats = filter { it.group.startsWith("page:") }
        .groupBy { it.group }
        .filterValues { list -> list.maxOf { it.durationSec }.let { it > 0 && abs(it - mainLength) <= 1 } }
        .keys
    return filterNot { it.group in repeats }
}

/** How many different video files a page gave (several means they need telling apart). */
fun List<FormatInfo>.distinctPageVideos(): Int =
    filter { it.isFromPage && !it.isAudioOnly && !it.isImage }.distinctBy { it.directUrl }.size

/** 75 → "1:15", 3723 → "1:02:03"; blank when unknown. */
fun clockText(seconds: Int): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
}
