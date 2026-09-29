package com.anydrop.app.data.repository

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.anydrop.app.data.model.DownloadState
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.model.MediaSource
import com.anydrop.app.engine.YtDlpEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.File

class DownloadRepository(
    private val context: Context,
    private val ytDlpEngine: YtDlpEngine,
    private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun getFormats(
        url: String,
        cookieFile: File? = null
    ) = ytDlpEngine.getFormats(url, cookieFile)

    /** Metadata of the most recently fetched media (empty if the site gave none). */
    fun lastFetchedTitle(): String = ytDlpEngine.lastFetchedTitle
    fun lastFetchedUploader(): String = ytDlpEngine.lastFetchedUploader
    fun lastFetchedWebpageUrl(): String = ytDlpEngine.lastFetchedWebpageUrl

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
        cookieFile: File? = null
    ): Flow<DownloadState> {
        return ytDlpEngine.downloadWithMerge(url, outputPath, formatId, height, cookieFile)
            .map { state ->
                if (state is DownloadState.Completed) {
                    indexInMediaStore(state.mediaItem)
                }
                state
            }
            .flowOn(ioDispatcher)
    }

    fun downloadAudio(
        url: String,
        outputPath: String,
        cookieFile: File? = null
    ): Flow<DownloadState> {
        return ytDlpEngine.downloadAudio(url, outputPath, cookieFile)
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

    fun cancelDownload() {
        ytDlpEngine.cancel()
    }

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
    private fun indexInMediaStore(item: MediaItem) {
        try {
            val file = File(item.localFilePath)
            if (!file.exists()) {
                android.util.Log.w("MediaExport", "File missing, cannot export: ${item.localFilePath}")
                return
            }

            val ext = file.extension.lowercase()
            val mime = when {
                item.isImage -> "image/${if (ext == "jpg") "jpeg" else ext.ifBlank { "jpeg" }}"
                item.isAudio -> if (ext == "m4a") "audio/mp4" else "audio/$ext"
                else -> "video/${ext.ifBlank { "mp4" }}"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportToMediaStore(file, mime, item)
            } else {
                // Legacy: file is already on public storage; just scan it.
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
            }
        } catch (_: Exception) {}
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToMediaStore(file: File, mime: String, item: MediaItem) {
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
            return
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
        } catch (e: Exception) {
            android.util.Log.w("MediaExport", "Export copy failed for ${file.name}", e)
            resolver.delete(uri, null, null)
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
