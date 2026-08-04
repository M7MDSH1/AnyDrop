package com.anydrop.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.model.MediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

enum class SortMode { DATE, DURATION }

class LibraryRepository(private val context: Context) {

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()

    private val _audioItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val audioItems: StateFlow<List<MediaItem>> = _audioItems.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.DATE)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val audioExtensions = listOf("m4a", "mp3", "opus", "aac", "ogg", "flac", "wav")
    private val imageExtensions = listOf("jpg", "jpeg", "png", "webp", "heic", "gif")

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun getDownloadDir(): File {
        // Use the app-specific external files dir. This is writable on ALL Android
        // versions with ZERO storage permissions (scoped storage). On Android 11+,
        // writing to a public /storage/emulated/0/AnyDrop folder would require the
        // special "All files access" (MANAGE_EXTERNAL_STORAGE) permission, which was
        // causing "Permission denied: /storage/emulated/0/AnyDrop" on Android 12.
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val anyDropDir = File(base, "AnyDrop")
        if (!anyDropDir.exists()) anyDropDir.mkdirs()
        // Eagerly create the type subfolders so they always exist / are visible.
        File(anyDropDir, "Videos").mkdirs()
        File(anyDropDir, "Images").mkdirs()
        File(anyDropDir, "Audio").mkdirs()
        return anyDropDir
    }

    // Organized subfolders inside AnyDrop/ for each media type.
    fun getVideoDir(): File = File(getDownloadDir(), "Videos").apply { if (!exists()) mkdirs() }
    fun getImageDir(): File = File(getDownloadDir(), "Images").apply { if (!exists()) mkdirs() }
    fun getAudioDir(): File = File(getDownloadDir(), "Audio").apply { if (!exists()) mkdirs() }

    /**
     * The OLD public download folder (/storage/emulated/0/AnyDrop) used before the
     * move to app-specific storage. We still SCAN it (read-only) so downloads made
     * before the storage change keep showing up in the Library. It may be
     * inaccessible on Android 11+ without permission — guarded with try/catch.
     */
    private fun getLegacyPublicDir(): File? = try {
        val d = File(Environment.getExternalStorageDirectory(), "AnyDrop")
        if (d.exists()) d else null
    } catch (_: Exception) {
        null
    }

    /**
     * Files of the given extensions across ALL known AnyDrop locations:
     *  - the type subfolder (new)               e.g. AnyDrop/Videos
     *  - the app-specific root (recent)          e.g. AnyDrop/
     *  - the legacy public folder + its subs     e.g. /storage/emulated/0/AnyDrop[/Videos]
     */
    private fun collectFiles(preferredDir: File, exts: List<String>): List<File> {
        fun scan(dir: File?): List<File> =
            dir?.listFiles()?.filter { it.isFile && it.extension.lowercase() in exts } ?: emptyList()

        val results = mutableListOf<File>()
        results += scan(preferredDir)                    // new subfolder
        results += scan(getDownloadDir())                // app root (pre-subfolder)

        val legacy = getLegacyPublicDir()
        if (legacy != null) {
            results += scan(legacy)                      // old public root
            results += scan(File(legacy, preferredDir.name)) // old public subfolder if any
        }
        return results.distinctBy { it.absolutePath }
    }

    suspend fun refreshLibrary() = withContext(Dispatchers.IO) {
        val mode = _sortMode.value
        val items = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED
        )

        val orderBy = when (mode) {
            SortMode.DATE -> "${MediaStore.Video.Media.DATE_ADDED} DESC"
            SortMode.DURATION -> "${MediaStore.Video.Media.DURATION} DESC"
        }

        // Show ALL videos on the device (like a gallery). No folder filter.
        val cursor = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            orderBy
        )

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: "Unknown"
                val duration = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
                val fileSize = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))

                val localPath: String
                val localUri: Uri
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    localUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                    )
                    localPath = localUri.toString()
                } else {
                    @Suppress("DEPRECATION")
                    localPath = it.getString(it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)) ?: ""
                    localUri = Uri.fromFile(File(localPath))
                }

                val dateAdded = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED))
                items.add(MediaItem(
                    id = "vid_$id",
                    title = name,
                    localFilePath = localPath,
                    localUri = localUri,
                    durationMs = duration,
                    fileSize = fileSize,
                    downloadedAt = dateAdded * 1000, // DATE_ADDED is in seconds
                    source = MediaSource.UNKNOWN
                ))
            }
        }

        // Enrich the MediaStore items with metadata (creator/source/title) from the
        // sidecar that lives next to the PRIVATE copy of the same file name.
        val privateVideos = collectFiles(getVideoDir(), listOf("mp4", "mkv", "webm", "mov"))
        for (i in items.indices) {
            val storeItem = items[i]
            val match = privateVideos.firstOrNull { it.name == storeItem.title || it.nameWithoutExtension == storeItem.title }
                ?: privateVideos.firstOrNull { it.nameWithoutExtension == File(storeItem.title).nameWithoutExtension }
            if (match != null) {
                items[i] = storeItem.copy(localFilePath = storeItem.localFilePath).let { base ->
                    // read sidecar keyed by the private file path
                    val meta = readMetadata(match.absolutePath)
                    if (meta != null) base.copy(
                        url = meta.optString("url", base.url),
                        creator = meta.optString("creator", base.creator),
                        creatorUrl = meta.optString("creatorUrl", base.creatorUrl)
                    ) else base
                }
            }
        }

        // Add any private-folder videos that are NOT already in MediaStore (dedup by
        // file NAME, since MediaStore paths are content:// URIs and won't match paths).
        val storeNames = items.map { File(it.title).nameWithoutExtension }.toSet()
        val anyDropFiles = privateVideos
            .filter { file -> !storeNames.contains(file.nameWithoutExtension) }
            .sortedByDescending { it.lastModified() }
            .map { file ->
                MediaItem(
                    id = file.nameWithoutExtension,
                    title = file.nameWithoutExtension,
                    localFilePath = file.absolutePath,
                    localUri = Uri.fromFile(file),
                    fileSize = file.length(),
                    durationMs = readVideoDuration(file),
                    downloadedAt = file.lastModified(),
                    source = MediaSource.UNKNOWN
                ).withMetadata()
            }
        items.addAll(anyDropFiles)

        // ALL images on the device (gallery), via MediaStore.
        val imageItems = mutableListOf<MediaItem>()
        try {
            val imgProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED
            )
            val imgCursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imgProjection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            imgCursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "Image"
                    val size = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                    val dateAdded = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    imageItems.add(
                        MediaItem(
                            id = "img_$id",
                            title = name,
                            localFilePath = uri.toString(),
                            localUri = uri,
                            fileSize = size,
                            downloadedAt = dateAdded * 1000,
                            source = MediaSource.UNKNOWN,
                            isImage = true
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // Enrich AnyDrop images with metadata + add any private ones not yet indexed.
        val privateImages = collectFiles(getImageDir(), imageExtensions)
        for (i in imageItems.indices) {
            val storeItem = imageItems[i]
            val match = privateImages.firstOrNull { it.nameWithoutExtension == File(storeItem.title).nameWithoutExtension }
            if (match != null) {
                val meta = readMetadata(match.absolutePath)
                if (meta != null) {
                    imageItems[i] = storeItem.copy(
                        url = meta.optString("url", storeItem.url),
                        creator = meta.optString("creator", storeItem.creator),
                        creatorUrl = meta.optString("creatorUrl", storeItem.creatorUrl)
                    )
                }
            }
        }
        val imgStoreNames = imageItems.map { File(it.title).nameWithoutExtension }.toSet()
        privateImages
            .filter { !imgStoreNames.contains(it.nameWithoutExtension) }
            .sortedByDescending { it.lastModified() }
            .forEach { file ->
                imageItems.add(
                    MediaItem(
                        id = "img_" + file.nameWithoutExtension,
                        title = file.nameWithoutExtension,
                        localFilePath = file.absolutePath,
                        localUri = Uri.fromFile(file),
                        fileSize = file.length(),
                        downloadedAt = file.lastModified(),
                        source = MediaSource.UNKNOWN,
                        isImage = true
                    ).withMetadata()
                )
            }
        items.addAll(imageItems)

        _mediaItems.value = items
        refreshAudioLibrary()
    }

    private fun refreshAudioLibrary() {
        // Scan ALL audio on the device via MediaStore (music, ringtones excluded).
        val audio = mutableListOf<MediaItem>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED
            )
            // Only actual music/audio tracks, skip system ringtones/notifications.
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR " +
                "${MediaStore.Audio.Media.IS_PODCAST} != 0 OR " +
                "${MediaStore.Audio.Media.IS_AUDIOBOOK} != 0"
            val orderBy = when (_sortMode.value) {
                SortMode.DURATION -> "${MediaStore.Audio.Media.DURATION} DESC"
                else -> "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            }
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                orderBy
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    val title = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                        ?: it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME))
                        ?: "Unknown"
                    val duration = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                    val size = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE))
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val dataPath = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)) ?: ""
                    val dateAdded = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED))
                    audio.add(
                        MediaItem(
                            id = "aud_$id",
                            title = title,
                            localFilePath = dataPath.ifBlank { contentUri.toString() },
                            localUri = contentUri,
                            durationMs = duration,
                            fileSize = size,
                            downloadedAt = dateAdded * 1000, // DATE_ADDED is in seconds
                            source = MediaSource.UNKNOWN,
                            isAudio = true
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Permission missing or query failed — fall back to AnyDrop folder scan.
        }

        if (audio.isEmpty()) {
            // Fallback: audio ripped by the app into the AnyDrop/Audio folder.
            collectFiles(getAudioDir(), audioExtensions)
                .sortedByDescending { it.lastModified() }
                .forEach { file ->
                    audio.add(
                        MediaItem(
                            id = "aud_" + file.nameWithoutExtension,
                            title = file.nameWithoutExtension,
                            localFilePath = file.absolutePath,
                            localUri = Uri.fromFile(file),
                            fileSize = file.length(),
                            downloadedAt = file.lastModified(),
                            source = MediaSource.UNKNOWN,
                            isAudio = true
                        )
                    )
                }
        }
        // Guarantee newest-first regardless of source (MediaStore or folder).
        _audioItems.value = audio.sortedByDescending { it.downloadedAt }
    }

    fun deleteItem(item: MediaItem): Boolean {
        var deletedSomething = false

        // 1) Delete via MediaStore content:// URI (public copy). A plain File.delete
        //    can't remove a content URI, which is why deletes were "not working".
        try {
            val uriStr = item.localUri?.toString() ?: item.localFilePath
            if (uriStr.startsWith("content://")) {
                val rows = context.contentResolver.delete(android.net.Uri.parse(uriStr), null, null)
                if (rows > 0) deletedSomething = true
            }
        } catch (_: Exception) {}

        // 2) Delete the real file if localFilePath is a filesystem path.
        try {
            if (!item.localFilePath.startsWith("content://")) {
                val f = File(item.localFilePath)
                if (f.exists() && f.delete()) deletedSomething = true
                File("${item.localFilePath}.meta.json").takeIf { it.exists() }?.delete()
            }
        } catch (_: Exception) {}

        // 3) Delete the PRIVATE-folder twin (+ its sidecar) matched by file name,
        //    across all type subfolders. Ensures no copy lingers to reappear on scan.
        try {
            val baseName = File(item.title).nameWithoutExtension.ifBlank {
                File(item.localFilePath).nameWithoutExtension
            }
            listOf(getVideoDir(), getAudioDir(), getImageDir(), getDownloadDir()).forEach { dir ->
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && f.nameWithoutExtension == baseName) {
                        f.delete()
                        File("${f.absolutePath}.meta.json").takeIf { it.exists() }?.delete()
                        deletedSomething = true
                    }
                }
            }
        } catch (_: Exception) {}

        return deletedSomething
    }

    /** Read a video/audio file's duration in ms (0 on failure). */
    private fun readVideoDuration(file: File): Long {
        return try {
            val mmr = android.media.MediaMetadataRetriever()
            mmr.setDataSource(file.absolutePath)
            val dur = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            mmr.release()
            dur?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    // ---- Metadata sidecar (url / creator / duration) ----------------------
    // URL and creator can't be recovered from a file on disk, so we persist them
    // next to the media as "<file>.meta.json" at download time and read them back.

    fun writeMetadata(
        mediaFilePath: String,
        url: String,
        creator: String,
        creatorUrl: String,
        title: String
    ) {
        try {
            val meta = org.json.JSONObject()
                .put("url", url)
                .put("creator", creator)
                .put("creatorUrl", creatorUrl)
                .put("title", title)
            File("$mediaFilePath.meta.json").writeText(meta.toString())
        } catch (_: Exception) {}
    }

    private fun readMetadata(mediaFilePath: String): org.json.JSONObject? {
        return try {
            val f = File("$mediaFilePath.meta.json")
            if (!f.exists()) null else org.json.JSONObject(f.readText())
        } catch (_: Exception) {
            null
        }
    }

    private fun MediaItem.withMetadata(): MediaItem {
        val meta = readMetadata(localFilePath) ?: return this
        return copy(
            url = meta.optString("url", url),
            creator = meta.optString("creator", creator),
            creatorUrl = meta.optString("creatorUrl", creatorUrl),
            title = meta.optString("title", title).ifBlank { title }
        )
    }

    // ---- Smart file naming ------------------------------------------------

    /** Make a title safe for a filename: strip illegal chars, collapse spaces. */
    private fun sanitizeTitle(title: String): String {
        return title
            .replace(Regex("[\\\\/:*?\"<>|]"), "")   // illegal filename chars
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(100)
    }

    /** Short unique id (base36 timestamp + small random) for untitled media. */
    private fun uniqueId(): String {
        val ts = System.currentTimeMillis().toString(36)
        val rnd = (100..999).random()
        return "${ts}$rnd".uppercase()
    }

    /** Derive a platform label from the URL (e.g. "YouTube", "Instagram"). */
    private fun platformOf(url: String): String {
        val host = url.substringAfter("//").substringBefore("/").removePrefix("www.").lowercase()
        return when {
            host.contains("youtube") || host.contains("youtu.be") -> "YouTube"
            host.contains("instagram") -> "Instagram"
            host.contains("tiktok") -> "TikTok"
            host.contains("facebook") || host.contains("fb.watch") -> "Facebook"
            host.contains("twitter") || host.contains("x.com") -> "X"
            host.contains("reddit") -> "Reddit"
            else -> host.substringBefore(".").replaceFirstChar { it.uppercase() }.ifBlank { "Video" }
        }
    }

    /**
     * Build a filename. With a title: "Title [quality].ext" (so the same video at
     * different qualities coexists). Without a title: "Platform_quality_ID.ext"
     * with an app-generated unique id.
     */
    private fun buildFileName(
        title: String,
        url: String,
        quality: String,
        ext: String
    ): String {
        val q = quality.replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "media" }
        val clean = sanitizeTitle(title)
        return if (clean.isNotBlank()) {
            "$clean [$q].$ext"
        } else {
            "${platformOf(url)}_${q}_${uniqueId()}.$ext"
        }
    }

    /**
     * All PRIVATE copies of a video with this title+quality: files whose base
     * name is "Title [quality]" or "Title [quality]_N". Public MediaStore twins
     * mirror the private names, so they don't need a separate scan. Untitled
     * media has no stable base name and never collides.
     */
    fun findVideoCopies(url: String, title: String, quality: String): List<File> {
        if (sanitizeTitle(title).isBlank()) return emptyList()
        val base = File(buildFileName(title, url, quality, "mp4")).nameWithoutExtension
        return listOf(getVideoDir(), getDownloadDir())
            .flatMap { dir -> dir.listFiles()?.filter { it.isFile } ?: emptyList() }
            .filter { FileNameHelper.matchesCopyBase(it.nameWithoutExtension, base) }
            .sortedBy { it.lastModified() } // oldest first
    }

    /**
     * True when any of these copies is the SAME media as `url`, decided by the
     * `<file>.meta.json` sidecar (written at download time). Files without a
     * sidecar (legacy / still downloading) are treated as different media, so we
     * never wrongly prompt to replace them.
     */
    fun isSameVideo(copies: List<File>, url: String): Boolean =
        copies.any { readMetadata(it.absolutePath)?.optString("url") == url }

    /**
     * Output path for a "download anyway" copy: the next free
     * "Title [quality]_N.mp4" so repeated downloads of the same video coexist.
     */
    fun getSuffixedVideoOutputPath(url: String, title: String, quality: String, copies: List<File>): String {
        val base = File(buildFileName(title, url, quality, "mp4")).nameWithoutExtension
        val next = FileNameHelper.nextFreeBase(base, copies.map { it.nameWithoutExtension })
        return File(getVideoDir(), "$next.mp4").absolutePath
    }

    /** Delete the OLDEST copy of this video (private + sidecar + public twin). */
    fun deleteOldestVideoCopy(url: String, title: String, quality: String) {
        val oldest = findVideoCopies(url, title, quality).minByOrNull { it.lastModified() } ?: return
        val baseName = oldest.nameWithoutExtension
        oldest.delete()
        File("${oldest.absolutePath}.meta.json").takeIf { it.exists() }?.delete()
        // Public MediaStore twin (same display name).
        _mediaItems.value.firstOrNull {
            !it.isImage && !it.isAudio && File(it.title).nameWithoutExtension == baseName
        }?.let { existing ->
            val u = existing.localUri?.toString() ?: existing.localFilePath
            if (u.startsWith("content://")) {
                runCatching { context.contentResolver.delete(android.net.Uri.parse(u), null, null) }
            }
        }
    }

    fun getOutputPath(url: String, title: String = "", quality: String = ""): String {
        return File(getVideoDir(), buildFileName(title, url, quality, "mp4")).absolutePath
    }

    fun getAudioOutputPath(url: String, title: String = "", quality: String = ""): String {
        return File(getAudioDir(), buildFileName(title, url, quality, "m4a")).absolutePath
    }

    fun getImageOutputPath(url: String, title: String = "", quality: String = ""): String {
        return File(getImageDir(), buildFileName(title, url, quality, "jpg")).absolutePath
    }
}
