package com.anydrop.app.engine

import com.anydrop.app.engine.YtDlpEngine.FormatInfo

/**
 * The separate pieces of a multi-photo/video post (an Instagram carousel, an X post
 * with several pictures...), one entry per slide in the post's order, or an empty list
 * for a single-media post. These are what the "pick what to download" grid offers.
 *
 * Slides found on the page carry their position ([FormatInfo.slide]); the first entry
 * seen for a slide is its best one. Photo carousels read by yt-dlp have no positions,
 * so there every photo counts as a slide.
 */
fun List<FormatInfo>.postItems(): List<FormatInfo> {
    val slides = filter { it.slide >= 0 }
        .distinctBy { it.slide }
        .sortedBy { it.slide }
    if (slides.size >= 2) return slides
    val photos = filter { it.isImage && it.imageUrl.isNotBlank() }
    if (photos.size >= 2 && none { !it.isImage }) return photos
    return emptyList()
}
