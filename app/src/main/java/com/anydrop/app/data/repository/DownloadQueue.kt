package com.anydrop.app.data.repository

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.anydrop.app.R
import com.anydrop.app.appDownloadScope
import com.anydrop.app.data.model.DownloadState
import com.anydrop.app.engine.MediaSniffer
import com.anydrop.app.engine.TorrentFileKind
import com.anydrop.app.engine.YtDlpEngine
import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import com.anydrop.app.engine.YtDlpFormatParser
import com.anydrop.app.service.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class TaskStatus { RUNNING, DONE, FAILED, CANCELLED }
enum class TaskKind { VIDEO, AUDIO, IMAGE }

/** Everything needed to (re)start a download — kept on the task so RETRY works from anywhere. */
data class DownloadRequest(
    val url: String,
    val format: FormatInfo,
    val title: String,
    val creator: String = "",
    val webpageUrl: String = "",
    /** Browser session cookies for media found on a page (never written to disk). */
    val cookieHeader: String? = null
)

/** One row of the download tracker. [tag] is the output path — also the notification tag. */
data class DownloadTask(
    val tag: String,
    val title: String,
    val quality: String,
    val kind: TaskKind,
    val request: DownloadRequest,
    val status: TaskStatus = TaskStatus.RUNNING,
    val percent: Int = 0,
    val phase: String = "",
    val speed: String = "",
    val eta: String = "",
    val sizeText: String = "",
    val error: String = "",
    /** The tool's own error output behind [error], for "Copy error details". */
    val errorDetail: String = "",
    val filePath: String = "",
    val startedAt: Long = System.currentTimeMillis()
) {
    val isActive: Boolean get() = status == TaskStatus.RUNNING
}

/**
 * The download tracker: every download started from the deck, the share sheet or the
 * browser shows up here with live progress, and can be stopped, retried or opened.
 * Downloads run in [appDownloadScope], not in a ViewModel. The list itself is kept on disk
 * ([DownloadListStore]), so it's still there after Android closes the app.
 */
@Singleton
class DownloadQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val libraryRepository: LibraryRepository,
    private val cookieRepository: CookieRepository,
    private val settingsRepository: SettingsRepository,
    private val store: DownloadListStore
) {
    private val _tasks = MutableStateFlow(restore(store.load()))
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()

    init {
        // Saved whenever a download is added, removed or changes state; progress ticks aren't.
        appDownloadScope.launch {
            _tasks
                .map { list -> list.map { it.copy(phase = "", speed = "", eta = "", percent = if (it.isActive) 0 else it.percent) } }
                .distinctUntilChanged()
                .collect(store::save)
        }
    }

    /**
     * The saved list as the app reopens. A download that was running is no longer: it finished
     * if its file is complete, otherwise it was interrupted and RETRY carries on from its
     * partial file.
     */
    private fun restore(saved: List<DownloadTask>): List<DownloadTask> = saved.map { task ->
        if (!task.isActive) return@map task
        val out = File(task.tag)
        val complete = out.isFile && out.length() > 0L && !File(task.tag + ".part").exists()
        if (complete) {
            task.copy(status = TaskStatus.DONE, percent = 100, filePath = out.absolutePath, sizeText = sizeLabel(out.length()))
        } else {
            task.copy(status = TaskStatus.FAILED, error = context.getString(R.string.download_interrupted_retry))
        }
    }

    /** Starts one download. Returns its tag (= [outputPath]). */
    fun launch(request: DownloadRequest, outputPath: String): String {
        jobs.remove(outputPath)?.cancel()
        addTask(request, outputPath)
        DownloadService.startDownload(context, outputPath, request.title)
        jobs[outputPath] = appDownloadScope.launch { run(request, outputPath) }
        return outputPath
    }

    /**
     * Several images of one carousel, one after another. The service is told about the whole
     * batch up front so its notification isn't torn down between images.
     */
    fun launchBatch(items: List<Pair<DownloadRequest, String>>) {
        if (items.isEmpty()) return
        items.forEachIndexed { i, (request, path) ->
            addTask(request, path, if (i == 0) R.string.progress_starting else R.string.progress_queued)
        }
        DownloadService.startDownload(context, items.first().second, items.first().first.title, batchTags = items.map { it.second })
        val job = appDownloadScope.launch {
            items.forEach { (request, path) ->
                if (_tasks.value.firstOrNull { it.tag == path }?.status != TaskStatus.RUNNING) return@forEach
                DownloadService.startDownload(context, path, request.title)
                run(request, path)
            }
        }
        items.forEach { (_, path) -> jobs[path] = job }
    }

    fun cancel(tag: String) {
        val task = _tasks.value.firstOrNull { it.tag == tag } ?: return
        if (!task.isActive) return
        downloadRepository.cancelDownload(tag)
        update(tag) { it.copy(status = TaskStatus.CANCELLED, speed = "", eta = "") }
        DownloadService.cancelDownload(context, tag)
        // Batch jobs are shared by several images; only stop single-download jobs outright.
        if (jobs.values.count { it == jobs[tag] } <= 1) jobs.remove(tag)?.cancel()
    }

    fun retry(tag: String) {
        val task = _tasks.value.firstOrNull { it.tag == tag } ?: return
        if (task.isActive) return
        // Session cookies aren't saved with the list: a restored page video gets them again.
        val request = task.request.let { r ->
            if (r.cookieHeader == null && r.format.isFromPage) r.copy(cookieHeader = MediaSniffer.cookieHeaderFor(r.format.directUrl)) else r
        }
        launch(request, tag)
    }

    fun dismiss(tag: String) {
        val removed = _tasks.value.filter { it.tag == tag && !it.isActive }
        _tasks.update { list -> list.filterNot { it.tag == tag && !it.isActive } }
        removed.forEach(::deletePartials)
    }

    fun clearFinished() {
        val removed = _tasks.value.filter { !it.isActive }
        _tasks.update { list -> list.filter { it.isActive } }
        removed.forEach(::deletePartials)
    }

    /** A download leaving the list won't be resumed: its partial files go too. */
    private fun deletePartials(task: DownloadTask) {
        if (task.status == TaskStatus.DONE) return
        val out = File(task.tag)
        val prefixes = YtDlpEngine.partialFilePrefixes(task.tag)
        appDownloadScope.launch {
            out.parentFile?.listFiles()?.forEach { f ->
                val unfinished = f.name.startsWith(".temp_") || f.name.endsWith(".part") || f.name.endsWith(".ytdl")
                if (f.isFile && unfinished && prefixes.any { f.name.startsWith(it) }) f.delete()
            }
        }
    }

    /** Opens a finished download in whatever app handles it. */
    fun open(tag: String) {
        val task = _tasks.value.firstOrNull { it.tag == tag } ?: return
        val mime = when (task.kind) {
            TaskKind.IMAGE -> "image/*"
            TaskKind.AUDIO -> "audio/*"
            TaskKind.VIDEO -> "video/*"
        }
        // Torrent files end up in the gallery only (a content:// address).
        val uri = if (task.filePath.startsWith("content://")) {
            Uri.parse(task.filePath)
        } else {
            val file = File(task.filePath)
            if (!file.exists()) return
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.error_no_app_to_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun addTask(request: DownloadRequest, tag: String, phase: Int = R.string.progress_starting) {
        val format = request.format
        val kind = when {
            format.isImage -> TaskKind.IMAGE
            format.isAudioOnly -> TaskKind.AUDIO
            else -> TaskKind.VIDEO
        }
        val task = DownloadTask(
            tag = tag,
            title = request.title.ifBlank { File(tag).nameWithoutExtension },
            quality = format.resolution,
            kind = kind,
            request = request,
            phase = context.getString(phase)
        )
        _tasks.update { list -> listOf(task) + list.filterNot { it.tag == tag } }
    }

    private fun update(tag: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.tag == tag) transform(it) else it } }
    }

    private fun flowFor(request: DownloadRequest, outputPath: String): Flow<DownloadState> {
        val format = request.format
        val cookieFile = cookieRepository.getCookieFile().takeIf { it.exists() }
        return when {
            format.isTorrent -> downloadRepository.downloadTorrent(request.url, format.torrentFiles, ::torrentFolder)
            format.isLink -> linkFlow(request, outputPath, cookieFile)
            format.isImage -> downloadRepository.downloadImage(request.url, outputPath, format.id, cookieFile, format.imageUrl)
            format.isFromPage -> downloadRepository.downloadDirect(
                request.webpageUrl.ifBlank { request.url }, format, outputPath, request.cookieHeader
            )
            format.isAudioOnly -> downloadRepository.downloadAudio(request.url, outputPath, cookieFile)
            format.acodec.isNotEmpty() && format.acodec != "none" ->
                downloadRepository.download(request.url, outputPath, format.id, cookieFile)
            else -> {
                val height = YtDlpFormatParser.extractHeight(format.resolution).takeIf { it > 0 } ?: 1080
                downloadRepository.downloadWithMerge(request.url, outputPath, format.id, height, cookieFile)
            }
        }
    }

    /**
     * A video linked from a page (picked in "whole page" mode without choosing a quality): its
     * page is read now and the best quality the Maximum quality setting allows is downloaded.
     */
    private fun linkFlow(request: DownloadRequest, outputPath: String, cookieFile: File?): Flow<DownloadState> = flow {
        emit(DownloadState.Progress(0, phase = context.getString(R.string.progress_finding_video)))
        val formats = try {
            downloadRepository.getFormats(request.url, cookieFile).formats
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: context.getString(R.string.error_no_media_found)))
            return@flow
        }
        val best = bestVideo(formats)
        if (best == null) {
            emit(DownloadState.Error(context.getString(R.string.error_no_media_found)))
            return@flow
        }
        val resolved = request.copy(
            format = best,
            cookieHeader = if (best.isFromPage) MediaSniffer.cookieHeaderFor(best.directUrl) else null
        )
        // The tracker shows the quality found, and RETRY starts from it instead of looking again.
        update(outputPath) { it.copy(quality = best.resolution, request = resolved) }
        emitAll(flowFor(resolved, outputPath))
    }

    /** Where a torrent's finished files go, by kind (null = not kept). */
    private fun torrentFolder(kind: TorrentFileKind): File? = when (kind) {
        TorrentFileKind.VIDEO -> libraryRepository.getVideoDir()
        TorrentFileKind.AUDIO -> libraryRepository.getAudioDir()
        TorrentFileKind.IMAGE -> libraryRepository.getImageDir()
        TorrentFileKind.OTHER -> null
    }

    private fun bestVideo(formats: List<FormatInfo>): FormatInfo? {
        val videos = formats.filter { !it.isImage && !it.isAudioOnly && !it.isLink }
        val max = settingsRepository.maxQualityHeight
        return videos.firstOrNull { YtDlpFormatParser.extractHeight(it.resolution) in 1..max } ?: videos.firstOrNull()
    }

    private suspend fun run(request: DownloadRequest, tag: String) {
        flowFor(request, tag).collect { state ->
            // A STOP from the user wins over late events from the dying process.
            if (_tasks.value.firstOrNull { it.tag == tag }?.status == TaskStatus.CANCELLED) return@collect
            when (state) {
                is DownloadState.Progress -> {
                    update(tag) {
                        it.copy(
                            percent = maxOf(it.percent, state.percentage).coerceIn(0, 100),
                            phase = state.phase,
                            speed = state.speed,
                            eta = state.eta,
                            sizeText = state.sizeText.ifBlank { it.sizeText }
                        )
                    }
                    DownloadService.updateProgress(context, tag, state.percentage)
                }
                is DownloadState.Completed -> {
                    val path = state.mediaItem.localFilePath
                    libraryRepository.writeMetadata(
                        mediaFilePath = path,
                        url = request.webpageUrl.ifBlank { request.url },
                        creator = request.creator,
                        creatorUrl = "",
                        title = request.title
                    )
                    update(tag) {
                        it.copy(
                            status = TaskStatus.DONE, percent = 100, phase = "", speed = "", eta = "",
                            filePath = path,
                            sizeText = (File(path).length().takeIf { l -> l > 0 } ?: state.mediaItem.fileSize.takeIf { l -> l > 0 })
                                ?.let(::sizeLabel) ?: it.sizeText
                        )
                    }
                    DownloadService.finishDownload(context, tag, request.title.ifBlank { context.getString(R.string.download_complete) })
                    libraryRepository.refreshLibrary()
                }
                is DownloadState.Error -> {
                    update(tag) {
                        it.copy(status = TaskStatus.FAILED, error = state.message, errorDetail = state.detail, speed = "", eta = "")
                    }
                    DownloadService.notifyFailed(context, tag, request.title.ifBlank { context.getString(R.string.error_download_failed) })
                }
                is DownloadState.Cancelled -> update(tag) { it.copy(status = TaskStatus.CANCELLED, speed = "", eta = "") }
                DownloadState.Idle -> Unit
            }
        }
    }

    private fun sizeLabel(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1e9)
        bytes >= 1_000_000 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1e6)
        else -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1e3)
    }
}
