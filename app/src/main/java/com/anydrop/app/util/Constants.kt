package com.anydrop.app.util

object Constants {
    const val TAG = "AnyDrop"

    const val DOWNLOAD_NOTIFICATION_ID = 1001
    const val DOWNLOAD_CHANNEL_ID = "anydrop_downloads"

    const val COOKIE_FILE_KEY = "cookies.txt"
    const val DEFAULT_DOWNLOAD_DIR = "AnyDrop"

    const val ACTION_DOWNLOAD_START = "com.anydrop.app.DOWNLOAD_START"
    const val ACTION_DOWNLOAD_CANCEL = "com.anydrop.app.DOWNLOAD_CANCEL"
    const val ACTION_DOWNLOAD_PROGRESS = "com.anydrop.app.DOWNLOAD_PROGRESS"

    val SUPPORTED_DOMAINS = listOf(
        "instagram.com", "tiktok.com", "youtube.com",
        "youtu.be", "facebook.com", "reddit.com",
        "twitter.com", "x.com"
    )
}
