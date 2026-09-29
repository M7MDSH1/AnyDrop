package com.anydrop.app.data.repository

import android.content.Context
import android.net.Uri
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.model.MediaSource
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Disk cache of the media-library index so reopening the app shows the grid
 * instantly instead of waiting for full MediaStore + folder scans. The cache is
 * rebuilt by [LibraryRepository] whenever a refresh completes and is dropped on
 * deletes / re-exports so stale entries never resurrect.
 */
class LibraryIndexCache(private val context: Context) {

    data class Index(
        val media: List<MediaItem>,
        val audio: List<MediaItem>
    )

    suspend fun load(): Index? = withContext(Dispatchers.IO) {
        val file = cacheFile()
        if (!file.exists() || file.length() == 0L) return@withContext null
        runCatching {
            val root = JSONObject(file.readText())
            Index(
                media = root.optJSONArray("media").orEmpty().toMediaItems(),
                audio = root.optJSONArray("audio").orEmpty().toMediaItems()
            )
        }.getOrNull()
    }

    suspend fun save(media: List<MediaItem>, audio: List<MediaItem>) = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject().apply {
                put("media", media.toJsonArray())
                put("audio", audio.toJsonArray())
            }
            val target = cacheFile()
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(root.toString())
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }
    }

    /** Cheap fingerprint so callers can skip rewriting an unchanged index. */
    fun signature(media: List<MediaItem>, audio: List<MediaItem>): Long {
        var sig = media.size.toLong() * 31L + audio.size
        media.forEach { sig += it.downloadedAt + it.fileSize }
        audio.forEach { sig += it.downloadedAt + it.fileSize }
        return sig
    }

    /** Drop the cache (after deletes / re-exports) so the next launch rescans. */
    fun invalidate() {
        runCatching { cacheFile().delete() }
    }

    private fun cacheFile(): File = File(context.filesDir, "library_index.json")

    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

    private fun MediaItem.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("url", url)
        put("thumbnailUrl", thumbnailUrl)
        put("localFilePath", localFilePath)
        put("localUri", localUri?.toString().orEmpty())
        put("source", source.name)
        put("quality", quality)
        put("durationMs", durationMs)
        put("fileSize", fileSize)
        put("downloadedAt", downloadedAt)
        put("isAudio", isAudio)
        put("isImage", isImage)
        put("creator", creator)
        put("creatorUrl", creatorUrl)
    }

    private fun JSONArray.toMediaItems(): List<MediaItem> = buildList {
        for (i in 0 until length()) {
            val o = getJSONObject(i)
            add(
                MediaItem(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    url = o.optString("url"),
                    thumbnailUrl = o.optString("thumbnailUrl"),
                    localFilePath = o.optString("localFilePath"),
                    localUri = o.optString("localUri")
                        .takeIf { it.isNotBlank() }
                        ?.let { Uri.parse(it) },
                    source = runCatching {
                        MediaSource.valueOf(o.optString("source", MediaSource.UNKNOWN.name))
                    }.getOrDefault(MediaSource.UNKNOWN),
                    quality = o.optString("quality"),
                    durationMs = o.optLong("durationMs"),
                    fileSize = o.optLong("fileSize"),
                    downloadedAt = o.optLong("downloadedAt"),
                    isAudio = o.optBoolean("isAudio"),
                    isImage = o.optBoolean("isImage"),
                    creator = o.optString("creator"),
                    creatorUrl = o.optString("creatorUrl")
                )
            )
        }
    }

    private fun List<MediaItem>.toJsonArray(): JSONArray = JSONArray().apply {
        forEach { put(it.toJson()) }
    }
}
