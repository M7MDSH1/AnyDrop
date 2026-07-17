package com.anydrop.app.data.model

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Progress(
        val percentage: Int = 0,
        val speed: String = "",
        val eta: String = ""
    ) : DownloadState
    data class Completed(val mediaItem: MediaItem) : DownloadState
    data class Error(val message: String) : DownloadState
    data object Cancelled : DownloadState
}
