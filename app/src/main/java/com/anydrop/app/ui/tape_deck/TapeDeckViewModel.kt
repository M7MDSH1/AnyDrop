package com.anydrop.app.ui.tape_deck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anydrop.app.data.model.DownloadState
import com.anydrop.app.pendingSharedUrl
import com.anydrop.app.data.repository.CookieRepository
import com.anydrop.app.data.repository.DownloadRepository
import com.anydrop.app.data.repository.LibraryRepository
import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import com.anydrop.app.service.DownloadService
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

@HiltViewModel
class TapeDeckViewModel @Inject constructor(
    application: Application,
    private val downloadRepository: DownloadRepository,
    private val libraryRepository: LibraryRepository,
    private val cookieRepository: CookieRepository
) : AndroidViewModel(application) {

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

    val isCookieImported = cookieRepository.isImported
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var searchJob: Job? = null

    init {
        pendingSharedUrl?.let { url ->
            handleSharedUrl(url)
            pendingSharedUrl = null
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
    }

    fun onUrlChange(newUrl: String) {
        _url.value = newUrl
        _downloadState.value = DownloadState.Idle
        _selectedFormat.value = null
        _availableFormats.value = emptyList()
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
    }

    // Non-null when a same title+quality video already exists; UI shows a
    // "download again & replace?" dialog. Holds the video's display title.
    private val _duplicatePrompt = MutableStateFlow<String?>(null)
    val duplicatePrompt: StateFlow<String?> = _duplicatePrompt.asStateFlow()

    private var _forceReplace = false

    /** User tapped "Yes, replace" — re-run the download, overwriting the old file. */
    fun confirmReplaceDownload() {
        _duplicatePrompt.value = null
        _forceReplace = true
        startDownload()
    }

    fun dismissDuplicatePrompt() {
        _duplicatePrompt.value = null
        _forceReplace = false
    }

    fun startDownload() {
        val urlValue = _url.value.trim()
        if (urlValue.isBlank()) return

        val format = _selectedFormat.value ?: return
        val title = downloadRepository.lastFetchedTitle()
        val creator = downloadRepository.lastFetchedUploader()
        val webpageUrl = downloadRepository.lastFetchedWebpageUrl().ifBlank { urlValue }

        fun saveMeta(path: String) {
            libraryRepository.writeMetadata(
                mediaFilePath = path,
                url = webpageUrl,
                creator = creator,
                creatorUrl = "",
                title = title
            )
        }

        // Keep the foreground notification in sync with the download flow.
        fun reportToNotification(state: DownloadState) {
            when (state) {
                is DownloadState.Progress ->
                    DownloadService.updateProgress(getApplication(), state.percentage)
                is DownloadState.Completed ->
                    DownloadService.finishDownload(getApplication(), title.ifBlank { "Download complete" })
                is DownloadState.Error, is DownloadState.Cancelled ->
                    // Dismiss the ongoing notification without a false "complete".
                    DownloadService.cancelDownload(getApplication())
                else -> {}
            }
        }

        // If the user picked an image (photo posts from Instagram/X/TikTok), download
        // the image file directly.
        if (format.isImage) {
            val imageFlow = downloadRepository.downloadImage(
                url = urlValue,
                outputPath = libraryRepository.getImageOutputPath(urlValue, title, "image"),
                formatId = format.id,
                cookieFile = cookieRepository.getCookieFile().takeIf { it.exists() },
                directImageUrl = format.imageUrl
            )
            _downloadState.value = DownloadState.Progress(0, "Starting...", "")
            DownloadService.startDownload(getApplication(), urlValue)
            com.anydrop.app.appDownloadScope.launch {
                imageFlow.collect { state ->
                    _downloadState.value = state
                    reportToNotification(state)
                    if (state is DownloadState.Completed) {
                        saveMeta(state.mediaItem.localFilePath)
                        libraryRepository.refreshLibrary()
                    }
                }
            }
            return
        }

        // If the user picked an audio-only quality from the format list, extract
        // just the audio to .m4a instead of downloading video.
        if (format.isAudioOnly) {
            val audioQuality = if (format.abr > 0) "${format.abr}kbps" else "audio"
            val audioFlow = downloadRepository.downloadAudio(
                url = urlValue,
                outputPath = libraryRepository.getAudioOutputPath(urlValue, title, audioQuality),
                cookieFile = cookieRepository.getCookieFile().takeIf { it.exists() }
            )
            _downloadState.value = DownloadState.Progress(0, "Starting...", "")
            DownloadService.startDownload(getApplication(), urlValue)
            com.anydrop.app.appDownloadScope.launch {
                audioFlow.collect { state ->
                    _downloadState.value = state
                    reportToNotification(state)
                    if (state is DownloadState.Completed) {
                        saveMeta(state.mediaItem.localFilePath)
                        libraryRepository.refreshLibrary()
                    }
                }
            }
            return
        }

        val quality = format.resolution.ifBlank { "video" }

        // Duplicate check: same title + same quality already downloaded?
        if (libraryRepository.videoExists(urlValue, title, quality) && !_forceReplace) {
            _duplicatePrompt.value = title.ifBlank { "this video" }
            return
        }
        _forceReplace = false
        // Replacing: remove the existing same-name file(s) first.
        libraryRepository.deleteVideoByName(urlValue, title, quality)

        val hasAudio = format.acodec.isNotEmpty() && format.acodec != "none"
        val flow = if (hasAudio) {
            downloadRepository.download(
                url = urlValue,
                outputPath = libraryRepository.getOutputPath(urlValue, title, quality),
                formatId = format.id,
                cookieFile = cookieRepository.getCookieFile().takeIf { it.exists() }
            )
        } else {
            val height = Regex("(\\d+)").find(format.resolution)?.value?.toIntOrNull() ?: 1080
            downloadRepository.downloadWithMerge(
                url = urlValue,
                outputPath = libraryRepository.getOutputPath(urlValue, title, quality),
                formatId = format.id,
                height = height,
                cookieFile = cookieRepository.getCookieFile().takeIf { it.exists() }
            )
        }

        _downloadState.value = DownloadState.Progress(0, "Starting...", "")
        DownloadService.startDownload(getApplication(), urlValue)

        com.anydrop.app.appDownloadScope.launch {
            flow.collect { state ->
                _downloadState.value = state
                reportToNotification(state)
                if (state is DownloadState.Completed) {
                    saveMeta(state.mediaItem.localFilePath)
                    libraryRepository.refreshLibrary()
                }
            }
        }
    }

    /**
     * Download several selected images (carousel/multi-image posts) in sequence.
     * Each is saved with a distinct name (index-suffixed via the unique-id naming).
     */
    fun downloadImages(images: List<FormatInfo>) {
        val urlValue = _url.value.trim()
        if (urlValue.isBlank() || images.isEmpty()) return
        val title = downloadRepository.lastFetchedTitle()
        val creator = downloadRepository.lastFetchedUploader()
        val webpageUrl = downloadRepository.lastFetchedWebpageUrl().ifBlank { urlValue }
        val cookie = cookieRepository.getCookieFile().takeIf { it.exists() }

        _downloadState.value = DownloadState.Progress(0, "Starting...", "")
        DownloadService.startDownload(getApplication(), urlValue)
        com.anydrop.app.appDownloadScope.launch {
            var lastState: DownloadState = DownloadState.Idle
            images.forEachIndexed { idx, img ->
                val quality = "img${idx + 1}"
                val flow = downloadRepository.downloadImage(
                    url = urlValue,
                    outputPath = libraryRepository.getImageOutputPath(urlValue, title, quality),
                    formatId = img.id,
                    cookieFile = cookie,
                    directImageUrl = img.imageUrl
                )
                flow.collect { state ->
                    lastState = state
                    _downloadState.value = state
                    if (state is DownloadState.Completed) {
                        libraryRepository.writeMetadata(
                            state.mediaItem.localFilePath, webpageUrl, creator, "", title
                        )
                    }
                }
            }
            libraryRepository.refreshLibrary()
            if (lastState is DownloadState.Completed) {
                _downloadState.value = lastState
            }
            DownloadService.finishDownload(getApplication(), "${images.size} images downloaded")
        }
    }

    fun cancelDownload() {
        downloadRepository.cancelDownload()
        DownloadService.cancelDownload(getApplication())
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
            _availableFormats.value = formats
            if (formats.isEmpty()) {
                _downloadState.value = DownloadState.Error("No downloadable media found for this link.")
            }
        } catch (e: Exception) {
            _availableFormats.value = emptyList()
            // Surface a clear reason (e.g. "needs login / import cookies") instead of
            // silently showing an empty list.
            _downloadState.value = DownloadState.Error(
                e.message ?: "Couldn't fetch formats for this link."
            )
        } finally {
            _isLoadingFormats.value = false
        }
    }

}
