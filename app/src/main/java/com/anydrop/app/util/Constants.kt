package com.anydrop.app.util

object Constants {
    const val TAG = "AnyDrop"

    const val FOREGROUND_SERVICE_NOTIFICATION_ID = 1
    const val DOWNLOAD_PROGRESS_NOTIFICATION_ID = 1001
    const val DOWNLOAD_CHANNEL_ID = "anydrop_downloads"
    // Opens the deck's downloads list (from any download notification).
    const val EXTRA_SHOW_DOWNLOADS = "com.anydrop.app.SHOW_DOWNLOADS"

    // In-app updates from the project's GitHub releases.
    const val GITHUB_REPO = "M7MDSH1/AnyDrop"
    const val UPDATE_CHANNEL_ID = "anydrop_updates"
    const val UPDATE_NOTIFICATION_ID = 2001
    const val EXTRA_SHOW_UPDATE = "com.anydrop.app.SHOW_UPDATE"

    const val COOKIE_FILE_KEY = "cookies.txt"
    const val DEFAULT_DOWNLOAD_DIR = "AnyDrop"

    const val ACTION_DOWNLOAD_START = "com.anydrop.app.DOWNLOAD_START"
    const val ACTION_DOWNLOAD_CANCEL = "com.anydrop.app.DOWNLOAD_CANCEL"
    const val ACTION_DOWNLOAD_COMPLETE = "com.anydrop.app.DOWNLOAD_COMPLETE"
    const val ACTION_DOWNLOAD_FAILED = "com.anydrop.app.DOWNLOAD_FAILED"
    const val ACTION_DOWNLOAD_PROGRESS = "com.anydrop.app.DOWNLOAD_PROGRESS"
    const val ACTION_DOWNLOAD_WAITING = "com.anydrop.app.DOWNLOAD_WAITING"

    val SUPPORTED_DOMAINS = listOf(
        "instagram.com", "tiktok.com", "youtube.com",
        "youtu.be", "facebook.com", "reddit.com",
        "twitter.com", "x.com"
    )
}
