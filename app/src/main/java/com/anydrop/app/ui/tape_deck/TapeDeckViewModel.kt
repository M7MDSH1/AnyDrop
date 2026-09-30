package com.anydrop.app.ui.tape_deck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anydrop.app.R
import com.anydrop.app.data.model.DownloadState
import com.anydrop.app.pendingSharedUrl
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.repository.CookieRepository
import com.anydrop.app.data.repository.DeckSessionRepository
import com.anydrop.app.data.repository.DownloadQueue
import com.anydrop.app.data.repository.DownloadRepository
import com.anydrop.app.data.repository.DownloadRequest
import com.anydrop.app.data.repository.DownloadTask
import com.anydrop.app.data.repository.LibraryRepository
import com.anydrop.app.data.repository.SettingsRepository
import com.anydrop.app.data.repository.TaskStatus
import com.anydrop.app.engine.MediaSniffer
import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import com.anydrop.app.engine.YtDlpFormatParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shown when the same video (by source URL) is downloaded again. */
data class DuplicatePrompt(
    val title: String,
    val copyCount: Int
)

@HiltViewModel
class TapeDeckViewModel @Inject constructor(
    application: Application,
    private val downloadRepository: DownloadRepository,
    private val libraryRepository: LibraryRepository,
    private val cookieRepository: CookieRepository,
    private val settingsRepository: SettingsRepository,
    private val deckSessionRepository: DeckSessionRepository,
    private val downloadQueue: DownloadQueue
) : AndroidViewModel(application) {

    /** Every download (running, finished, failed) — the deck's tracker list. */
    val tasks: StateFlow<List<DownloadTask>> = downloadQueue.tasks

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _availableFormats = MutableStateFlow<List<FormatInfo>>(emptyList())
    val availableFormats: StateFlow<List<FormatInfo>> = _availableFormats.asStateFlow()

    private val _selectedFormat = MutableStateFlow<FormatInfo?>(null)
    val selectedFormat: StateFlow<FormatInfo?> = _selectedFormat.asStateFlow()

    private val _isLoadingFormats = MutableStateFlow(false)
    val isLoadingFormats: StateFlow<Boolean> = _isLoadingFormats.asStateFlow()

    private val _autoDetectLinks = MutableStateFlow(settingsRepository.autoDetectLinks)
    val autoDetectLinks: StateFlow<Boolean> = _autoDetectLinks.asStateFlow()

    val isCookieImported = cookieRepository.isImported
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var searchJob: Job? = null

    init {
        if (pendingSharedUrl == null) {
            restoreSession()
        } else {
            pendingSharedUrl?.let { url ->
                handleSharedUrl(url)
                pendingSharedUrl = null
            }
        }
        // React to shares that arrive while the app is already running (singleTask
        // delivers them via onNewIntent, after this ViewModel's init has run).
        viewModelScope.launch {
            com.anydrop.app.sharedUrlFlow.collect { url ->
                if (!url.isNullOrBlank()) {
                    handleSharedUrl(url)
                    com.anydrop.app.sharedUrlFlow.value = null
                }
            }
        }
        // The deck's single status (reels, VU meter, share-sheet auto-close) mirrors the
        // download this screen started most recently.
        viewModelScope.launch {
            downloadQueue.tasks.collect { list ->
                val tag = currentNotificationTag ?: return@collect
                list.firstOrNull { it.tag == tag }?.let { _downloadState.value = it.toDownloadState() }
            }
        }
    }

    private fun DownloadTask.toDownloadState(): DownloadState = when (status) {
        TaskStatus.RUNNING -> DownloadState.Progress(percent, speed, eta, phase, sizeText)
        TaskStatus.DONE -> DownloadState.Completed(MediaItem(id = tag, title = title, localFilePath = filePath))
        TaskStatus.FAILED -> DownloadState.Error(error)
        TaskStatus.CANCELLED -> DownloadState.Cancelled
    }

    fun cancelTask(tag: String) = downloadQueue.cancel(tag)
    fun retryTask(tag: String) = downloadQueue.retry(tag)
    fun dismissTask(tag: String) = downloadQueue.dismiss(tag)
    fun openTask(tag: String) = downloadQueue.open(tag)
    fun clearFinishedTasks() = downloadQueue.clearFinished()

    /** Restore the last URL + fetched formats + selection so the deck looks the same. */
    private fun restoreSession() {
        val sessionUrl = deckSessionRepository.lastUrl()
        if (sessionUrl.isBlank()) return
        _url.value = sessionUrl
        val formats = deckSessionRepository.loadFormats()
        if (formats.isNotEmpty()) {
            _availableFormats.value = capByMaxQuality(formats)
            val selectedId = deckSessionRepository.selectedFormatId()
            _selectedFormat.value = _availableFormats.value.firstOrNull { it.id == selectedId }
        }
    }

    fun onUrlChange(newUrl: String) {
        _url.value = newUrl
        _downloadState.value = DownloadState.Idle
        _selectedFormat.value = null
        _availableFormats.value = emptyList()
        _duplicatePrompt.value = null
        pendingSuffixPath = null
        deckSessionRepository.saveSession(newUrl, emptyList(), "")
        searchJob?.cancel()
        if (newUrl.isNotBlank()) {
            searchJob = viewModelScope.launch {
                delay(500)
                fetchFormats()
            }
        }
    }

    fun selectFormat(format: FormatInfo) {
        _selectedFormat.value = format
        deckSessionRepository.saveSession(
            _url.value.trim(),
            _availableFormats.value,
            format.id
        )
    }

    // Non-null when the same video is already downloaded; UI shows the
    // REPLACE / DOWNLOAD ANYWAY / CANCEL dialog. Holds the video's display title.
    private val _duplicatePrompt = MutableStateFlow<DuplicatePrompt?>(null)
    val duplicatePrompt: StateFlow<DuplicatePrompt?> = _duplicatePrompt.asStateFlow()

    private var _forceReplace = false
    private var pendingSuffixPath: String? = null

    /** Tag (output path) of the most recently started download — for user cancel. */
    private var currentNotificationTag: String? = null

    /** User tapped "Replace" — delete the oldest copy, re-download to the base name. */
    fun confirmReplaceDownload() {
        _duplicatePrompt.value = null
        _forceReplace = true
        startDownload()
    }

    /** User tapped "Download anyway" — save as a numbered copy (Title_1, Title_2, ...). */
    fun confirmDownloadAnyway() {
        val urlValue = _url.value.trim()
        val title = downloadRepository.lastFetchedTitle()
        val quality = _selectedFormat.value?.resolution?.ifBlank { "video" } ?: "video"
        val copies = libraryRepository.findVideoCopies(urlValue, title, quality)
        pendingSuffixPath = libraryRepository.getSuffixedVideoOutputPath(urlValue, title, quality, copies)
        _duplicatePrompt.value = null
        startDownload()
    }

    fun dismissDuplicatePrompt() {
        _duplicatePrompt.value = null
        _forceReplace = false
        pendingSuffixPath = null
    }

    /** What the queue needs to (re)start this download. */
    private fun requestFor(format: FormatInfo, urlValue: String): DownloadRequest {
        val webpageUrl = downloadRepository.lastFetchedWebpageUrl().ifBlank { urlValue }
        return DownloadRequest(
            url = urlValue,
            format = format,
            title = downloadRepository.lastFetchedTitle(),
            creator = downloadRepository.lastFetchedUploader(),
            webpageUrl = webpageUrl,
            // Media found on the page is fetched with the same browser session that found it.
            cookieHeader = if (format.isFromPage) MediaSniffer.cookieHeaderFor(format.directUrl) else null
        )
    }

    private fun launch(request: DownloadRequest, outputPath: String) {
        currentNotificationTag = outputPath
        _downloadState.value = DownloadState.Progress(0)
        downloadQueue.launch(request, outputPath)
    }

    fun startDownload() {
        val urlValue = _url.value.trim()
        if (urlValue.isBlank()) return

        val format = _selectedFormat.value ?: return
        val request = requestFor(format, urlValue)
        val title = request.title

        // If the user picked an image (photo posts from Instagram/X/TikTok), download
        // the image file directly.
        if (format.isImage) {
            launch(request, libraryRepository.getImageOutputPath(urlValue, title, "image"))
            return
        }

        // If the user picked an audio-only quality from the format list, extract
        // just the audio to .m4a instead of downloading video.
        if (format.isAudioOnly) {
            val audioQuality = if (format.abr > 0) "${format.abr}kbps" else "audio"
            launch(request, libraryRepository.getAudioOutputPath(urlValue, title, audioQuality))
            return
        }

        val quality = format.resolution.ifBlank { "video" }

        // Resolve what to do about pre-existing files that share this name.
        // Identity is decided by the source URL (sidecar), NOT the filename —
        // platforms that report a generic creator-level title would otherwise
        // mistake different videos for duplicates.
        val copies = libraryRepository.findVideoCopies(urlValue, title, quality)
        val sameVideo = libraryRepository.isSameVideo(copies, urlValue)

        var outputPath: String? = null
        when {
            pendingSuffixPath != null -> {
                outputPath = pendingSuffixPath
                pendingSuffixPath = null
            }
            _forceReplace -> {
                // Replace: drop the oldest copy, re-download at the plain base name.
                libraryRepository.deleteOldestVideoCopy(urlValue, title, quality)
            }
            copies.isNotEmpty() && sameVideo -> {
                _duplicatePrompt.value = DuplicatePrompt(title.ifBlank { "this video" }, copies.size)
                return
            }
            copies.isNotEmpty() -> {
                // Different video already holds this name — keep both, no prompt.
                outputPath = libraryRepository.getSuffixedVideoOutputPath(urlValue, title, quality, copies)
            }
        }
        _forceReplace = false
        launch(request, outputPath ?: libraryRepository.getOutputPath(urlValue, title, quality))
    }

    /**
     * Download several selected images (carousel/multi-image posts) in sequence.
     * Each is saved with a distinct name (index-suffixed via the unique-id naming).
     */
    fun downloadImages(images: List<FormatInfo>) {
        val urlValue = _url.value.trim()
        if (urlValue.isBlank() || images.isEmpty()) return
        val items = images.mapIndexed { idx, img ->
            val request = requestFor(img, urlValue)
            request to libraryRepository.getImageOutputPath(urlValue, request.title, "img${idx + 1}")
        }
        currentNotificationTag = items.last().second
        _downloadState.value = DownloadState.Progress(0)
        downloadQueue.launchBatch(items)
    }

    /** STOP on the deck: stops the download this screen started most recently. */
    fun cancelDownload() {
        currentNotificationTag?.let { downloadQueue.cancel(it) }
        _downloadState.value = DownloadState.Cancelled
    }

    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }

fun handleSharedUrl(url: String) {
    _url.value = url
    _downloadState.value = DownloadState.Idle
    _selectedFormat.value = null
    _availableFormats.value = emptyList()
    _duplicatePrompt.value = null
    pendingSuffixPath = null
    deckSessionRepository.saveSession(url, emptyList(), "")
    searchJob?.cancel()
    viewModelScope.launch {
        delay(200)
        fetchFormats()
    }
}

    private suspend fun fetchFormats() {
        val urlValue = _url.value.trim()
        if (urlValue.isBlank()) return
        _isLoadingFormats.value = true
        _selectedFormat.value = null
        try {
            val formats = downloadRepository.getFormats(
                url = urlValue,
                cookieFile = cookieRepository.getCookieFile().takeIf { it.exists() }
            )
            _availableFormats.value = capByMaxQuality(formats)
            // Pre-pick the best entry so RECORD works with one tap (images are picked by the user).
            if (_selectedFormat.value == null) {
                _selectedFormat.value = _availableFormats.value.firstOrNull { !it.isImage }
            }
            deckSessionRepository.saveSession(
                urlValue,
                formats,
                _selectedFormat.value?.id.orEmpty()
            )
            if (formats.isEmpty()) {
                _downloadState.value = DownloadState.Error(
                    getApplication<Application>().getString(R.string.error_no_media_found)
                )
            }
        } catch (e: Exception) {
            _availableFormats.value = emptyList()
            // Surface a clear reason (e.g. "needs login / import cookies") instead of
            // silently showing an empty list.
            _downloadState.value = DownloadState.Error(
                e.message
                    ?: getApplication<Application>().getString(R.string.error_fetch_formats)
            )
        } finally {
            _isLoadingFormats.value = false
        }
    }

    /**
     * Apply the "Maximum quality" setting: drop video formats above the chosen
     * height. Audio and image entries are never capped. If the cap would remove
     * everything (e.g. only 4K exists and the user chose 480p), keep the original
     * list so there is still something to download.
     */
    private fun capByMaxQuality(formats: List<FormatInfo>): List<FormatInfo> {
        val max = settingsRepository.maxQualityHeight
        if (max >= SettingsRepository.MAX_QUALITY_BEST) return formats
        val capped = formats.mapNotNull { f ->
            if (f.isAudioOnly || f.isImage) f
            else if (YtDlpFormatParser.extractHeight(f.resolution) <= max) f
            else null
        }
        return if (capped.isEmpty()) formats else capped
    }

}