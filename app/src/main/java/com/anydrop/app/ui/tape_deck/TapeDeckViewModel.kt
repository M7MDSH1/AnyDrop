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
import com.anydrop.app.engine.AudioVoice
import com.anydrop.app.engine.Captions
import com.anydrop.app.engine.LinkList
import com.anydrop.app.engine.Magnet
import com.anydrop.app.engine.Playlists
import com.anydrop.app.engine.asAudio
import com.anydrop.app.engine.isPlaylist
import com.anydrop.app.engine.MediaProbe
import com.anydrop.app.engine.TorrentFile
import com.anydrop.app.engine.asDownload
import com.anydrop.app.engine.toFormat
import com.anydrop.app.engine.MediaSniffer
import com.anydrop.app.engine.YtDlpEngine
import com.anydrop.app.engine.YtDlpEngine.FetchResult
import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import com.anydrop.app.engine.YtDlpFormatParser
import com.anydrop.app.engine.isWholePage
import com.anydrop.app.engine.withoutRepeatsOfMain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    /** The fetched list as it came (before the Maximum quality cap); what the session saves. */
    private var rawFormats: List<FormatInfo> = emptyList()

    /** The voices of the fetched video when it has several (its own and translations); else empty. */
    private val _audioVoices = MutableStateFlow<List<AudioVoice>>(emptyList())
    val audioVoices: StateFlow<List<AudioVoice>> = _audioVoices.asStateFlow()

    /** The voice saved with the video (a language code) when it has several. */
    private val _audioLanguage = MutableStateFlow("")
    val audioLanguage: StateFlow<String> = _audioLanguage.asStateFlow()

    /** Subtitles the fetched video offers. */
    private val _captions = MutableStateFlow<List<Captions.Caption>>(emptyList())
    val captions: StateFlow<List<Captions.Caption>> = _captions.asStateFlow()

    /** Subtitles saved with the video (a language code; blank = none). */
    private val _subtitleLanguage = MutableStateFlow("")
    val subtitleLanguage: StateFlow<String> = _subtitleLanguage.asStateFlow()

    private val _selectedFormat = MutableStateFlow<FormatInfo?>(null)
    val selectedFormat: StateFlow<FormatInfo?> = _selectedFormat.asStateFlow()

    private val _isLoadingFormats = MutableStateFlow(false)
    val isLoadingFormats: StateFlow<Boolean> = _isLoadingFormats.asStateFlow()

    private val _autoDetectLinks = MutableStateFlow(settingsRepository.autoDetectLinks)
    val autoDetectLinks: StateFlow<Boolean> = _autoDetectLinks.asStateFlow()

    /** Fetch the link's video (false) or everything on its page (true). */
    private val _fetchWholePage = MutableStateFlow(settingsRepository.fetchWholePage)
    val fetchWholePage: StateFlow<Boolean> = _fetchWholePage.asStateFlow()

    /** Title of the fetched page / video (blank when the site gave none). */
    private val _pageTitle = MutableStateFlow("")
    val pageTitle: StateFlow<String> = _pageTitle.asStateFlow()
    private var fetchedUploader = ""
    private var fetchedWebpageUrl = ""

    /** The link is a video inside a playlist: the deck offers the whole list. */
    private val _playlistOffer = MutableStateFlow(false)
    val playlistOffer: StateFlow<Boolean> = _playlistOffer.asStateFlow()

    /** A playlist is being listed (for the loading text). */
    private val _fetchingPlaylist = MutableStateFlow(false)
    val fetchingPlaylist: StateFlow<Boolean> = _fetchingPlaylist.asStateFlow()

    /** Linked videos whose qualities are being looked up (by [FormatInfo.group]). */
    private val _resolving = MutableStateFlow<Set<String>>(emptySet())
    val resolving: StateFlow<Set<String>> = _resolving.asStateFlow()

    /** Short messages for a toast (e.g. a linked video that has nothing to download). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val isCookieImported = cookieRepository.isImported
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var searchJob: Job? = null
    private var probeJob: Job? = null
    /** Bumped by every fetch, so a cancelled one can't touch the state of the one after it. */
    private var fetchGeneration = 0

    // Non-null when the same video is already downloaded; UI shows the
    // REPLACE / DOWNLOAD ANYWAY / CANCEL dialog. Holds the video's display title.
    private val _duplicatePrompt = MutableStateFlow<DuplicatePrompt?>(null)
    val duplicatePrompt: StateFlow<DuplicatePrompt?> = _duplicatePrompt.asStateFlow()

    private var _forceReplace = false
    private var pendingSuffixPath: String? = null

    /** Tag (output path) of the most recently started download — for user cancel. */
    private var currentNotificationTag: String? = null

    // Keep every property above this block: init may handle a pending share,
    // which writes these fields, and Kotlin initializes in declaration order.
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
        TaskStatus.FAILED -> DownloadState.Error(error, errorDetail)
        TaskStatus.CANCELLED -> DownloadState.Cancelled
    }

    fun cancelTask(tag: String) = downloadQueue.cancel(tag)
    fun retryTask(tag: String) = downloadQueue.retry(tag)
    fun dismissTask(tag: String) = downloadQueue.dismiss(tag)
    fun openTask(tag: String) = downloadQueue.open(tag)
    fun clearFinishedTasks() = downloadQueue.clearFinished()
    fun pauseAllTasks() = downloadQueue.pauseAll()
    fun resumeAllTasks() = downloadQueue.resumeAll()
    fun useMobileData(tag: String) = downloadQueue.useMobileData(tag)

    /** Restore the last URL + fetched formats + selection so the deck looks the same. */
    private fun restoreSession() {
        val sessionUrl = deckSessionRepository.lastUrl()
        if (sessionUrl.isBlank()) return
        _url.value = sessionUrl
        _pageTitle.value = deckSessionRepository.lastTitle()
        val formats = deckSessionRepository.loadFormats()
        if (formats.isNotEmpty()) {
            rawFormats = formats
            _availableFormats.value = shown(formats)
            val selectedId = deckSessionRepository.selectedFormatId()
            _selectedFormat.value = _availableFormats.value.firstOrNull { it.id == selectedId }
            _playlistOffer.value = !formats.isPlaylist && Playlists.hasList(sessionUrl) && !Magnet.isTorrentLink(sessionUrl)
        }
    }

    /** Forget what was fetched for the previous link. */
    private fun clearFetched() {
        _downloadState.value = DownloadState.Idle
        _selectedFormat.value = null
        _availableFormats.value = emptyList()
        rawFormats = emptyList()
        _audioVoices.value = emptyList()
        _captions.value = emptyList()
        _subtitleLanguage.value = ""
        _pageTitle.value = ""
        fetchedUploader = ""
        fetchedWebpageUrl = ""
        _resolving.value = emptySet()
        _playlistOffer.value = false
        _duplicatePrompt.value = null
        pendingSuffixPath = null
        probeJob?.cancel()
        linkSummaryJob?.cancel()
        searchJob?.cancel()
    }

    private var linkSummaryJob: Job? = null

    /** The pasted links' titles, covers and lengths, three at a time, filled in as they arrive. */
    private fun fillLinkSummaries(urlValue: String) {
        linkSummaryJob?.cancel()
        val links = rawFormats.filter { it.group.startsWith(LinkList.GROUP) }.map { it.pageUrl }
        val cookieFile = cookieRepository.getCookieFile().takeIf { it.exists() }
        linkSummaryJob = viewModelScope.launch {
            val slots = Semaphore(3)
            links.map { link ->
                async {
                    val summary = slots.withPermit { downloadRepository.linkSummary(link, cookieFile) } ?: return@async
                    if (_url.value.trim() != urlValue) return@async
                    rawFormats = rawFormats.map { f ->
                        if (f.group != LinkList.GROUP + link || !f.isLink) f
                        else f.copy(
                            title = summary.title.ifBlank { f.title },
                            previewUrl = summary.thumbnail.ifBlank { f.previewUrl },
                            durationSec = summary.durationSec.takeIf { it > 0 } ?: f.durationSec
                        )
                    }
                    _availableFormats.value = shown(rawFormats)
                }
            }.awaitAll()
            if (_url.value.trim() == urlValue) saveSession()
        }
    }

    fun onUrlChange(text: String) {
        // Several links (a note, a message): kept on one line; the deck lists them.
        val newUrl = LinkList.extract(text).takeIf { it.size >= 2 }?.let(LinkList::oneLine) ?: text
        _url.value = newUrl
        clearFetched()
        deckSessionRepository.saveSession(newUrl, emptyList(), "")
        if (newUrl.isNotBlank()) {
            searchJob = viewModelScope.launch {
                delay(500)
                fetchFormats()
            }
        }
    }

    /** Switch between fetching the link's video and the whole page; fetches the current link again. */
    fun setFetchWholePage(wholePage: Boolean) {
        if (_fetchWholePage.value == wholePage) return
        _fetchWholePage.value = wholePage
        settingsRepository.fetchWholePage = wholePage
        val current = _url.value
        clearFetched()
        if (current.isNotBlank()) {
            deckSessionRepository.saveSession(current, emptyList(), "")
            searchJob = viewModelScope.launch { fetchFormats() }
        }
    }

    fun selectFormat(format: FormatInfo) {
        _selectedFormat.value = format
        saveSession(format.id)
    }

    private fun saveSession(selectedId: String = _selectedFormat.value?.id.orEmpty()) {
        deckSessionRepository.saveSession(_url.value.trim(), rawFormats, selectedId, _pageTitle.value)
    }

    /** User tapped "Replace" — delete the oldest copy, re-download to the base name. */
    fun confirmReplaceDownload() {
        _duplicatePrompt.value = null
        _forceReplace = true
        startDownload()
    }

    /** User tapped "Download anyway" — save as a numbered copy (Title_1, Title_2, ...). */
    fun confirmDownloadAnyway() {
        val urlValue = _url.value.trim()
        val title = _pageTitle.value
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
        return DownloadRequest(
            url = urlValue,
            format = format,
            title = _pageTitle.value,
            creator = fetchedUploader,
            webpageUrl = fetchedWebpageUrl.ifBlank { urlValue },
            // Media found on the page is fetched with the same browser session that found it.
            cookieHeader = if (format.isFromPage) MediaSniffer.cookieHeaderFor(format.directUrl) else null,
            audioLanguage = AudioVoice.toRequest(_audioVoices.value, _audioLanguage.value, format),
            subtitleLanguage = _subtitleLanguage.value.takeIf { !format.isAudioOnly && !format.isImage }.orEmpty()
        )
    }

    /** Subtitles to save with the video ([language] blank = none). */
    fun selectSubtitles(language: String) {
        if (language.isNotBlank() && _captions.value.none { it.language == language }) return
        _subtitleLanguage.value = language
        settingsRepository.preferredSubtitleLanguage = language
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
     * Download the chosen slides of a multi-photo/video post, each as its own file
     * ("Title [img2].jpg", "Title [vid3].mp4"; numbered by the slide's place in the post).
     */
    fun downloadPostItems(items: List<FormatInfo>) {
        val urlValue = _url.value.trim()
        if (urlValue.isBlank() || items.isEmpty()) return
        val batch = items.mapIndexed { idx, item ->
            val request = requestFor(item, urlValue)
            val n = (if (item.slide >= 0) item.slide else idx) + 1
            val path = if (item.isImage) {
                libraryRepository.getImageOutputPath(urlValue, request.title, "img$n")
            } else {
                val label = "vid$n"
                val copies = libraryRepository.findVideoCopies(urlValue, request.title, label)
                if (copies.isEmpty()) libraryRepository.getOutputPath(urlValue, request.title, label)
                else libraryRepository.getSuffixedVideoOutputPath(urlValue, request.title, label, copies)
            }
            request to path
        }
        currentNotificationTag = batch.last().second
        _downloadState.value = DownloadState.Progress(0)
        downloadQueue.launchBatch(batch)
    }

    /**
     * Download what was picked from a whole-page fetch: one entry per video, in the quality
     * chosen for it. Linked videos download from their own page (best quality when none was
     * chosen); each video is saved under its own title when the page gave one.
     */
    fun downloadPageItems(chosen: List<FormatInfo>, audioOnly: Boolean = false) {
        val urlValue = _url.value.trim()
        if (urlValue.isBlank() || chosen.isEmpty()) return
        val used = mutableSetOf<String>()
        val batch = chosen.mapIndexed { idx, picked ->
            // A playlist can be saved as sound only (music mixes): each video becomes an .m4a.
            val f = if (audioOnly && !picked.isImage) picked.asAudio() else picked
            val source = f.pageUrl.ifBlank { urlValue }
            val title = f.title.ifBlank { _pageTitle.value }
            val request = DownloadRequest(
                url = source,
                format = f,
                title = title,
                creator = if (f.pageUrl.isBlank()) fetchedUploader else "",
                webpageUrl = f.pageUrl.ifBlank { fetchedWebpageUrl.ifBlank { urlValue } },
                cookieHeader = if (f.isFromPage) MediaSniffer.cookieHeaderFor(f.directUrl) else null,
                audioLanguage = if (f.group == YtDlpEngine.MAIN_GROUP) AudioVoice.toRequest(_audioVoices.value, _audioLanguage.value, f) else ""
            )
            val path = if (f.isImage) {
                libraryRepository.getImageOutputPath(source, title, "img${idx + 1}")
            } else if (f.isAudioOnly) {
                libraryRepository.getAudioOutputPath(source, title, "audio")
            } else {
                val label = if (f.isLink) "best" else f.resolution.ifBlank { "video" }
                val copies = libraryRepository.findVideoCopies(source, title, label)
                if (copies.isEmpty()) libraryRepository.getOutputPath(source, title, label)
                else libraryRepository.getSuffixedVideoOutputPath(source, title, label, copies)
            }
            request to uniquePath(path, used)
        }
        currentNotificationTag = batch.last().second
        _downloadState.value = DownloadState.Progress(0)
        downloadQueue.launchBatch(batch)
    }

    /**
     * Downloads the chosen files of the torrent on the deck as one tracker entry; its videos,
     * music and pictures land in the library when it finishes.
     */
    fun downloadTorrent(files: List<TorrentFile>) {
        val link = _url.value.trim()
        if (link.isBlank() || files.isEmpty()) return
        val title = _pageTitle.value.ifBlank { files.maxBy { it.size }.name.substringBeforeLast('.') }
        val format = files.asDownload(title)
        // One tracker entry per torrent: starting the same torrent again replaces it.
        launch(
            DownloadRequest(url = link, format = format, title = title),
            libraryRepository.getOutputPath(link, title, "torrent")
        )
    }

    /** Two picks with the same title and quality would share a file: number the repeats. */
    private fun uniquePath(path: String, used: MutableSet<String>): String {
        var candidate = path
        var n = 2
        while (candidate in used) {
            candidate = path.substringBeforeLast('.') + "_$n." + path.substringAfterLast('.')
            n++
        }
        used += candidate
        return candidate
    }

    /**
     * Looks up the qualities of a linked video (whole-page mode) so one can be chosen. Without
     * this the best one is picked when it downloads.
     */
    fun resolvePageItem(group: String) {
        val link = rawFormats.firstOrNull { it.group == group && it.isLink } ?: return
        if (group in _resolving.value) return
        _resolving.update { it + group }
        val urlValue = _url.value.trim()
        viewModelScope.launch {
            try {
                val result = downloadRepository.getFormats(link.pageUrl, cookieRepository.getCookieFile().takeIf { it.exists() })
                val videos = result.formats.filter { !it.isAudioOnly && !it.isImage }.map { f ->
                    f.copy(
                        group = group,
                        pageUrl = link.pageUrl,
                        title = result.title.ifBlank { link.title },
                        previewUrl = f.previewUrl.ifBlank { link.previewUrl },
                        durationSec = f.durationSec.takeIf { it > 0 } ?: link.durationSec
                    )
                }
                if (videos.isEmpty()) {
                    _messages.tryEmit(getApplication<Application>().getString(R.string.error_no_media_found))
                    return@launch
                }
                val probed = if (videos.any(MediaProbe::needsProbe)) {
                    downloadRepository.probePageMedia(videos, result.webpageUrl.ifBlank { link.pageUrl })
                } else videos
                if (_url.value.trim() != urlValue) return@launch
                val at = rawFormats.indexOfFirst { it.group == group }
                if (at < 0) return@launch
                rawFormats = rawFormats.filterNot { it.group == group }.toMutableList().apply { addAll(at.coerceAtMost(size), probed) }
                _availableFormats.value = shown(rawFormats)
                saveSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: getApplication<Application>().getString(R.string.error_fetch_formats))
            } finally {
                _resolving.update { it - group }
            }
        }
    }

    /** STOP on the deck: stops the download this screen started most recently. */
    fun cancelDownload() {
        currentNotificationTag?.let { downloadQueue.cancel(it) }
        _downloadState.value = DownloadState.Cancelled
    }

    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }

    fun handleSharedUrl(text: String) {
        val url = LinkList.extract(text).takeIf { it.size >= 2 }?.let(LinkList::oneLine) ?: text
        _url.value = url
        clearFetched()
        deckSessionRepository.saveSession(url, emptyList(), "")
        searchJob = viewModelScope.launch {
            delay(200)
            fetchFormats()
        }
    }

    /** Lists every video of the playlist the deck's link is in (the link was one of its videos). */
    fun loadPlaylist() {
        if (_url.value.isBlank()) return
        searchJob?.cancel()
        probeJob?.cancel()
        _playlistOffer.value = false
        _availableFormats.value = emptyList()
        searchJob = viewModelScope.launch { fetchFormats(forcePlaylist = true) }
    }

    private suspend fun fetchFormats(forcePlaylist: Boolean = false) {
        val urlValue = _url.value.trim()
        if (urlValue.isBlank()) return
        val wholePage = _fetchWholePage.value
        val generation = ++fetchGeneration
        val linkList = LinkList.isList(urlValue)
        val playlistWanted = !linkList && !Magnet.isTorrentLink(urlValue) &&
            (forcePlaylist || Playlists.isListOnly(urlValue) || (wholePage && Playlists.hasList(urlValue)))
        _fetchingPlaylist.value = playlistWanted
        _isLoadingFormats.value = true
        _selectedFormat.value = null
        try {
            val cookieFile = cookieRepository.getCookieFile().takeIf { it.exists() }
            val result: FetchResult = when {
                // Several links: each listed as its own video (read in the background below).
                linkList -> FetchResult(LinkList.entries(LinkList.extract(urlValue)))
                // A torrent: its files instead of qualities (the page modes don't apply).
                Magnet.isTorrentLink(urlValue) -> downloadRepository.torrentFiles(urlValue).let { (meta, _) ->
                    FetchResult(meta.files.map { it.toFormat(meta.name) }, title = meta.name)
                }
                // A playlist: its videos to pick from. A video inside one opens as that video
                // (the deck then offers the whole list), unless the whole page was asked for.
                playlistWanted ->
                    downloadRepository.getPlaylist(urlValue, cookieFile)
                        ?: if (wholePage) downloadRepository.getPageMedia(urlValue, cookieFile)
                        else downloadRepository.getFormats(urlValue, cookieFile)
                wholePage -> downloadRepository.getPageMedia(urlValue, cookieFile)
                else -> downloadRepository.getFormats(urlValue, cookieFile)
            }
            if (generation != fetchGeneration) return
            _pageTitle.value = result.title
            _captions.value = result.captions
            // The subtitles picked last time, when this video has them.
            _subtitleLanguage.value = settingsRepository.preferredSubtitleLanguage
                .takeIf { wanted -> result.captions.any { it.language == wanted } }.orEmpty()
            fetchedUploader = result.uploader
            fetchedWebpageUrl = result.webpageUrl
            rawFormats = if (wholePage && !linkList) result.formats.withoutRepeatsOfMain() else result.formats
            if (linkList) fillLinkSummaries(urlValue)
            _availableFormats.value = shown(rawFormats)
            // Pre-pick the best entry so RECORD works with one tap (images are picked by the user;
            // a whole page or a torrent is picked from its list).
            if (_selectedFormat.value == null && !wholePage && rawFormats.none { it.isTorrent } && !rawFormats.isWholePage) {
                _selectedFormat.value = _availableFormats.value.firstOrNull { !it.isImage }
            }
            saveSession()
            // Not offered again when listing was just tried (YouTube doesn't list every mix).
            _playlistOffer.value = !playlistWanted && !rawFormats.isPlaylist && Playlists.hasList(urlValue) &&
                !Magnet.isTorrentLink(urlValue)
            if (rawFormats.isEmpty()) {
                _downloadState.value = DownloadState.Error(
                    getApplication<Application>().getString(R.string.error_no_media_found)
                )
            } else {
                probeInBackground(urlValue, result.webpageUrl.ifBlank { urlValue })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (generation != fetchGeneration) return
            _availableFormats.value = emptyList()
            // Surface a clear reason (e.g. "needs login / import cookies") instead of
            // silently showing an empty list.
            _downloadState.value = DownloadState.Error(
                e.message
                    ?: getApplication<Application>().getString(R.string.error_fetch_formats),
                detail = (e.cause ?: e).toString()
            )
        } finally {
            if (generation == fetchGeneration) _isLoadingFormats.value = false
        }
    }

    /**
     * Video files read off a page come without a length or cover; look inside them after the
     * list is shown (it fills in as results arrive), so ads and other clips can be told apart.
     */
    private fun probeInBackground(urlValue: String, pageUrl: String) {
        probeJob?.cancel()
        if (rawFormats.none(MediaProbe::needsProbe)) return
        val formats = rawFormats
        probeJob = viewModelScope.launch {
            val probed = try {
                downloadRepository.probePageMedia(formats, pageUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            if (_url.value.trim() != urlValue || rawFormats !== formats) return@launch
            rawFormats = if (probed.isWholePage) probed.withoutRepeatsOfMain() else probed
            _availableFormats.value = shown(rawFormats)
            _selectedFormat.value = _selectedFormat.value?.let { sel ->
                _availableFormats.value.firstOrNull { it.id == sel.id && it.directUrl == sel.directUrl && it.imageUrl == sel.imageUrl }
            }
            saveSession()
        }
    }

    /**
     * Apply the "Maximum quality" setting: drop video formats above the chosen
     * height. Audio and image entries are never capped. A video whose every quality is
     * above the cap (e.g. only 4K exists and the user chose 480p) keeps them all, so
     * there is still something to download.
     */
    private fun capByMaxQuality(formats: List<FormatInfo>): List<FormatInfo> {
        val max = settingsRepository.maxQualityHeight
        if (max >= SettingsRepository.MAX_QUALITY_BEST) return formats
        // A carousel slide, an unresolved link or a torrent file has one version only: capping
        // would drop it.
        fun fits(f: FormatInfo) = f.isAudioOnly || f.isImage || f.slide >= 0 || f.isLink || f.isTorrent ||
            YtDlpFormatParser.extractHeight(f.resolution) <= max
        val keepAll = formats.groupBy { it.group }.filterValues { list -> list.none(::fits) }.keys
        return formats.filter { fits(it) || it.group in keepAll }
    }

    /**
     * What the deck lists from [formats]: capped by Maximum quality and, when the video has
     * several voices, only the chosen voice's audio qualities. Also refreshes the voices offered.
     */
    private fun shown(formats: List<FormatInfo>): List<FormatInfo> {
        val voices = AudioVoice.of(formats)
        _audioVoices.value = voices
        if (voices.isNotEmpty() && voices.none { it.language == _audioLanguage.value }) {
            _audioLanguage.value = AudioVoice.pick(voices, settingsRepository.preferredAudioLanguage)
        }
        val language = _audioLanguage.value
        return capByMaxQuality(formats).filter { !it.isAudioOnly || it.language.isBlank() || it.language == language }
    }

    /** Picks the voice to save with the video, for a video that has several. */
    fun selectAudioLanguage(language: String) {
        if (_audioVoices.value.none { it.language == language }) return
        _audioLanguage.value = language
        settingsRepository.preferredAudioLanguage = language
        val selected = _selectedFormat.value
        _availableFormats.value = shown(rawFormats)
        // An audio quality of another voice is no longer listed: pick this voice's best instead.
        if (selected != null && selected.isAudioOnly && selected.language != language) {
            _selectedFormat.value = _availableFormats.value.firstOrNull { it.isAudioOnly }
        }
    }
}
