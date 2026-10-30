package com.anydrop.app.data.repository

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.anydrop.app.data.model.DownloadState
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.model.MediaSource
import com.anydrop.app.R
import com.anydrop.app.engine.TorrentEngine
import com.anydrop.app.engine.TorrentEvent
import com.anydrop.app.engine.TorrentException
import com.anydrop.app.engine.TorrentFile
import com.anydrop.app.engine.TorrentFileKind
import com.anydrop.app.engine.TorrentMeta
import com.anydrop.app.engine.YtDlpEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import java.io.File

class DownloadRepository(
    private val context: Context,
    private val ytDlpEngine: YtDlpEngine,
    private val torrentEngine: TorrentEngine,
    private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun getFormats(
        url: String,
        cookieFile: File? = null
    ): YtDlpEngine.FetchResult = ytDlpEngine.getFormats(url, cookieFile)

    /** The videos of a playlist link, or null when it isn't one. */
    suspend fun getPlaylist(url: String, cookieFile: File? = null): YtDlpEngine.FetchResult? =
        ytDlpEngine.getPlaylist(url, cookieFile)

    /** Every video on the page, and the videos it links to ("whole page" mode). */
    suspend fun getPageMedia(
        url: String,
        cookieFile: File? = null
    ): YtDlpEngine.FetchResult = ytDlpEngine.getPageMedia(url, cookieFile)

    /** Length, real resolution and a cover for video files found on [pageUrl]. */
    suspend fun probePageMedia(formats: List<YtDlpEngine.FormatInfo>, pageUrl: String): List<YtDlpEngine.FormatInfo> =
        ytDlpEngine.probePageMedia(formats, pageUrl)

    fun download(
        url: String,
        outputPath: String,
        formatId: String = "bestvideo+bestaudio/best",
        cookieFile: File? = null
    ): Flow<DownloadState> {
        return ytDlpEngine.download(url, outputPath, formatId, cookieFile)
            .map { state ->
                if (state is DownloadState.Completed) {
                    indexInMediaStore(state.mediaItem)
                }
                state
            }
            .flowOn(ioDispatcher)
    }

    fun downloadWithMerge(
        url: String,
        outputPath: String,
        formatId: String,
        height: Int,
        cookieFile: File? = null,
        audioLanguage: String = ""
    ): Flow<DownloadState> {
        return ytDlpEngine.downloadWithMerge(url, outputPath, formatId, height, cookieFile, audioLanguage)
            .map { state ->
                if (state is DownloadState.Completed) {
                    indexInMediaStore(state.mediaItem)
                }
                state
            }
            .flowOn(ioDispatcher)
    }

    suspend fun linkSummary(url: String, cookieFile: File? = null): YtDlpEngine.LinkSummary? =
        ytDlpEngine.getLinkSummary(url, cookieFile)

    /** The video's subtitles next to it (see [YtDlpEngine.downloadSubtitles]). */
    suspend fun downloadSubtitles(url: String, language: String, videoPath: String, cookieFile: File? = null): File? =
        withContext(ioDispatcher) { ytDlpEngine.downloadSubtitles(url, language, videoPath, cookieFile) }

    fun downloadAudio(
        url: String,
        outputPath: String,
        cookieFile: File? = null,
        language: String = "",
        formatId: String = "",
        abr: Int = 0
    ): Flow<DownloadState> {
        return ytDlpEngine.downloadAudio(url, outputPath, cookieFile, language, formatId, abr)
            .map { state ->
                if (state is DownloadState.Completed) {
                    indexInMediaStore(state.mediaItem)
                }
                state
            }
            .flowOn(ioDispatcher)
    }

    fun downloadImage(
        url: String,
        outputPath: String,
        formatId: String,
        cookieFile: File? = null,
        directImageUrl: String = ""
    ): Flow<DownloadState> {
        return ytDlpEngine.downloadImage(url, outputPath, formatId, cookieFile, directImageUrl)
            .map { state ->
                if (state is DownloadState.Completed) {
                    indexInMediaStore(state.mediaItem)
                }
                state
            }
            .flowOn(ioDispatcher)
    }

    /** Media the web extractor found on the page (no login); see [YtDlpEngine.downloadDirect]. */
    fun downloadDirect(
        pageUrl: String,
        format: YtDlpEngine.FormatInfo,
        outputPath: String,
        cookieHeader: String? = null
    ): Flow<DownloadState> {
        return ytDlpEngine.downloadDirect(pageUrl, format, outputPath, cookieHeader)
            .map { state ->
                if (state is DownloadState.Completed) {
                    indexInMediaStore(state.mediaItem)
                }
                state
            }
            .flowOn(ioDispatcher)
    }

    /** A torrent link's files (magnet link or .torrent address), and the link downloads use. */
    suspend fun torrentFiles(link: String): Pair<TorrentMeta, String> = torrentEngine.open(link)

    /**
     * Downloads the chosen files of a torrent. When it finishes, its videos, music and pictures
     * go into the library folders ([folderFor]) and the public gallery like any download; a
     * torrent's other files (subtitles, text…) are left out. The tracker shows the largest file.
     */
    fun downloadTorrent(link: String, files: Set<Int>, folderFor: (TorrentFileKind) -> File?): Flow<DownloadState> = flow {
        torrentEngine.download(link, files).collect { event ->
            when (event) {
                is TorrentEvent.Progress -> emit(event.state)
                is TorrentEvent.Done -> {
                    emit(DownloadState.Progress(99, phase = context.getString(R.string.torrent_saving)))
                    val saved = event.files.mapNotNull { saveTorrentFile(it, folderFor) }
                    torrentEngine.cleanUp(event.workDir)
                    val main = saved.maxByOrNull { it.fileSize }
                    emit(
                        if (main != null) DownloadState.Completed(main)
                        else DownloadState.Error(context.getString(R.string.torrent_nothing_saved))
                    )
                }
            }
        }
    }.catch { e ->
        emit(
            DownloadState.Error(
                e.message?.takeIf { e is TorrentException } ?: context.getString(R.string.torrent_failed),
                detail = e.toString()
            )
        )
    }.flowOn(ioDispatcher)

    /**
     * Moves a finished torrent file into the library and the gallery. Torrents are often big, so
     * once the gallery has its copy the private one is removed instead of keeping both.
     */
    private fun saveTorrentFile(file: File, folderFor: (TorrentFileKind) -> File?): MediaItem? {
        val kind = TorrentFile(0, file.name, file.length()).kind
        val dir = folderFor(kind) ?: return null
        val target = freeFile(dir, file.name)
        if (!file.renameTo(target)) {
            file.copyTo(target)
            file.delete()
        }
        val item = MediaItem(
            id = target.nameWithoutExtension,
            title = target.nameWithoutExtension,
            localFilePath = target.absolutePath,
            fileSize = target.length(),
            isAudio = kind == TorrentFileKind.AUDIO,
            isImage = kind == TorrentFileKind.IMAGE
        )
        val galleryUri = indexInMediaStore(item) ?: return item
        target.delete()
        return item.copy(localFilePath = galleryUri.toString(), localUri = galleryUri)
    }

    /** [name] in [dir], or "name (2).ext", "name (3).ext"… when taken. */
    private fun freeFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, "${name.substringBeforeLast('.')} ($n).${name.substringAfterLast('.', "")}".trimEnd('.'))
            n++
        }
        return candidate
    }

    /** Stops one download (by its output path), or all of them when [tag] is null. */
    fun cancelDownload(tag: String? = null) {
        ytDlpEngine.cancel(tag)
    }

    /** False on phones where the bundled ffmpeg can't load (16 KB memory pages). */
    fun ffmpegUsable(): Boolean = ytDlpEngine.ffmpegUsable

    /**
     * Re-export a finished private download into the public MediaStore on a later
     * launch (the process died before the normal export ran). Returns true when a
     * complete file was written to the public gallery. [MediaSource] is inferred
     * from the owning AnyDrop subfolder.
     */
    fun reexportToMediaStore(outputPath: String): Boolean {
        val file = File(outputPath)
        if (!file.exists() || file.length() <= 0L) return false
        val parentName = file.parentFile?.name.orEmpty()
        val item = MediaItem(
            id = file.nameWithoutExtension,
            title = file.nameWithoutExtension,
            localFilePath = file.absolutePath,
            source = MediaSource.UNKNOWN,
            isAudio = parentName == "Audio",
            isImage = parentName == "Images"
        )
        return runCatching { indexInMediaStore(item); true }.getOrDefault(false)
    }

    /**
     * Make a finished download visible to OTHER apps (Gallery, WhatsApp, etc.) by
     * copying it into the public MediaStore collection. Files in the app-private
     * folder (Android/data/...) are invisible to other apps and to the media
     * scanner, so a plain scanFile does nothing — we must insert via MediaStore.
     *
     * Works on Android 10+ with NO storage permission. On Android 9- it falls back
     * to scanning the file path.
     */
    private fun indexInMediaStore(item: MediaItem): android.net.Uri? {
        try {
            val file = File(item.localFilePath)
            if (!file.exists()) {
                android.util.Log.w("MediaExport", "File missing, cannot export: ${item.localFilePath}")
                return null
            }

            val ext = file.extension.lowercase()
            // The system's own type for the extension (e.g. video/x-matroska for .mkv), else a guess.
            val known = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            val mime = when {
                item.isImage -> if (ext == "jpg") "image/jpeg" else known ?: "image/${ext.ifBlank { "jpeg" }}"
                item.isAudio -> if (ext == "m4a") "audio/mp4" else known ?: "audio/$ext"
                else -> known?.takeIf { it.startsWith("video/") } ?: "video/${ext.ifBlank { "mp4" }}"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return exportToMediaStore(file, mime, item)
            } else {
                // Legacy: file is already on public storage; just scan it.
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
            }
        } catch (_: Exception) {}
        return null
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToMediaStore(file: File, mime: String, item: MediaItem): android.net.Uri? {
        val resolver = context.contentResolver
        val subDir = "AnyDrop"

        val (collection, relativeBase, nameKey, dataless) = when {
            item.isImage -> Quad(
                android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY),
                android.os.Environment.DIRECTORY_PICTURES,
                android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                Unit
            )
            item.isAudio -> Quad(
                android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY),
                android.os.Environment.DIRECTORY_MUSIC,
                android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
                Unit
            )
            else -> Quad(
                android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY),
                android.os.Environment.DIRECTORY_MOVIES,
                android.provider.MediaStore.Video.Media.DISPLAY_NAME,
                Unit
            )
        }

        val values = android.content.ContentValues().apply {
            put(nameKey, file.name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "$relativeBase/$subDir")
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values)
        if (uri == null) {
            android.util.Log.w("MediaExport", "insert() returned null for ${file.name} ($mime) into $relativeBase/$subDir")
            return null
        }
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            val done = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
            android.util.Log.i("MediaExport", "Exported ${file.name} -> $uri")
            return uri
        } catch (e: Exception) {
            android.util.Log.w("MediaExport", "Export copy failed for ${file.name}", e)
            resolver.delete(uri, null, null)
            return null
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    fun canWriteToPublicDir(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return true
    }
}
