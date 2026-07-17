package com.anydrop.app.data.model

import android.net.Uri

data class MediaItem(
    val id: String,
    val title: String = "",
    val url: String = "",
    val thumbnailUrl: String = "",
    val localFilePath: String = "",
    val localUri: Uri? = null,
    val source: MediaSource = MediaSource.UNKNOWN,
    val quality: String = "",
    val durationMs: Long = 0L,
    val fileSize: Long = 0L,
    val downloadedAt: Long = System.currentTimeMillis(),
    val isAudio: Boolean = false,
    val isImage: Boolean = false,
    val creator: String = "",
    val creatorUrl: String = ""
)

enum class MediaSource(val displayName: String) {
    INSTAGRAM("Instagram"),
    TIKTOK("TikTok"),
    YOUTUBE("YouTube"),
    FACEBOOK("Facebook"),
    REDDIT("Reddit"),
    TWITTER("Twitter"),
    UNKNOWN("Unknown")
}
