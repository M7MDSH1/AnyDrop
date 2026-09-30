package com.anydrop.app.data.model

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Progress(
        val percentage: Int = 0,
        val speed: String = "",
        val eta: String = "",
        /** What is happening right now, e.g. "Downloading audio…" (blank = plain download). */
        val phase: String = "",
        /** "12.3 / 45.6 MB" when the size is known. */
        val sizeText: String = ""
    ) : DownloadState
    data class Completed(val mediaItem: MediaItem) : DownloadState
    data class Error(val message: String) : DownloadState
    data object Cancelled : DownloadState
}
