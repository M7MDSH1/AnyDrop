package com.anydrop.app.data.repository

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.anydrop.app.R
import com.anydrop.app.appDownloadScope
import com.anydrop.app.data.model.DownloadState
import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import com.anydrop.app.engine.YtDlpFormatParser
import com.anydrop.app.service.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val filePath: String = "",
    val startedAt: Long = System.currentTimeMillis()
) {
    val isActive: Boolean get() = status == TaskStatus.RUNNING
}

/**
 * The download tracker: every download started from the deck, the share sheet or the
 * browser shows up here with live progress, and can be stopped, retried or opened.
 * Lives for the app process (downloads run in [appDownloadScope], not in a ViewModel).
 */
@Singleton
class DownloadQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val libraryRepository: LibraryRepository,
    private val cookieRepository: CookieRepository
) {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()

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
        items.forEach { (request, path) -> addTask(request, path) }
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
        launch(task.request, tag)
    }

    fun dismiss(tag: String) {
        _tasks.update { list -> list.filterNot { it.tag == tag && !it.isActive } }
    }

    fun clearFinished() {
        _tasks.update { list -> list.filter { it.isActive } }
    }

    /** Opens a finished download in whatever app handles it. */
    fun open(tag: String) {
        val task = _tasks.value.firstOrNull { it.tag == tag } ?: return
        val file = File(task.filePath)
        if (!file.exists()) return
        val mime = when (task.kind) {
            TaskKind.IMAGE -> "image/*"
            TaskKind.AUDIO -> "audio/*"
            TaskKind.VIDEO -> "video/*"
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
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

    private fun addTask(request: DownloadRequest, tag: String) {
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
            phase = context.getString(R.string.progress_starting)
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
                            sizeText = File(path).length().takeIf { l -> l > 0 }?.let(::sizeLabel) ?: it.sizeText
                        )
                    }
                    DownloadService.finishDownload(context, tag, request.title.ifBlank { context.getString(R.string.download_complete) })
                    libraryRepository.refreshLibrary()
                }
                is DownloadState.Error -> {
                    update(tag) { it.copy(status = TaskStatus.FAILED, error = state.message, speed = "", eta = "") }
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
