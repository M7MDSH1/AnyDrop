package com.anydrop.app.engine

import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** Torrent links: magnet URIs, and what a torrent holds. Pure Kotlin, so it is unit-tested. */
object Magnet {

    private val MAGNET = Regex("""magnet:\?[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    fun isMagnet(text: String): Boolean = text.trim().startsWith("magnet:?", ignoreCase = true)

    /** A web address of a .torrent file. */
    fun isTorrentFileUrl(text: String): Boolean {
        val t = text.trim()
        return (t.startsWith("https://", true) || t.startsWith("http://", true)) &&
            t.substringBefore('#').substringBefore('?').endsWith(".torrent", ignoreCase = true)
    }

    /** Any kind of torrent link AnyDrop can open. */
    fun isTorrentLink(text: String): Boolean = isMagnet(text) || isTorrentFileUrl(text)

    /** The first magnet link in a piece of shared text. */
    fun find(text: String): String? = MAGNET.find(text)?.value

    /** The torrent's info hash as 40 lowercase hex characters (from a hex or base32 `btih`). */
    fun infoHash(magnet: String): String? {
        val xt = params(magnet)["xt"].orEmpty().firstOrNull { it.startsWith("urn:btih:", ignoreCase = true) } ?: return null
        val raw = xt.substring("urn:btih:".length)
        return when {
            raw.length == 40 && raw.all { it.isHex() } -> raw.lowercase(Locale.ROOT)
            raw.length == 32 -> base32ToHex(raw)
            else -> null
        }
    }

    /** The name a magnet link gives its torrent (`dn`), if any. */
    fun displayName(magnet: String): String? = params(magnet)["dn"]?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    /** The trackers a magnet link names (`tr`): where to ask for peers. */
    fun trackers(magnet: String): List<String> =
        params(magnet)["tr"].orEmpty().map { it.trim() }.filter { it.contains("://") }.distinct()

    /** Web servers that also hold the files (`ws`), used like peers. */
    fun webSeeds(magnet: String): List<String> =
        params(magnet)["ws"].orEmpty().map { it.trim() }.filter { it.startsWith("http://") || it.startsWith("https://") }.distinct()

    /** A magnet link for a torrent known by its hash (as when it was opened from a .torrent file). */
    fun of(infoHash: String, name: String): String =
        "magnet:?xt=urn:btih:$infoHash" + if (name.isNotBlank()) "&dn=" + URLEncoder.encode(name, "UTF-8") else ""

    private fun params(magnet: String): Map<String, List<String>> {
        val query = magnet.trim().substringAfter('?', "")
        if (query.isEmpty()) return emptyMap()
        return query.split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=').lowercase(Locale.ROOT)
                val value = part.substringAfter('=', "")
                if (key.isEmpty()) null else key to runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun base32ToHex(text: String): String? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = 0L
        var count = 0
        val out = StringBuilder()
        for (c in text.uppercase(Locale.ROOT)) {
            val v = alphabet.indexOf(c)
            if (v < 0) return null
            bits = (bits shl 5) or v.toLong()
            count += 5
            while (count >= 8) {
                count -= 8
                out.append("%02x".format(Locale.ROOT, (bits shr count) and 0xff))
            }
        }
        return out.toString().takeIf { it.length == 40 }
    }
}

/** What kind of file this is, by its extension. */
enum class TorrentFileKind { VIDEO, AUDIO, IMAGE, OTHER }

/** One file inside a torrent. */
data class TorrentFile(val index: Int, val path: String, val size: Long) {
    val name: String get() = path.substringAfterLast('/').substringAfterLast('\\')
    val extension: String get() = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val kind: TorrentFileKind
        get() = when (extension) {
            in VIDEO_EXTS -> TorrentFileKind.VIDEO
            in AUDIO_EXTS -> TorrentFileKind.AUDIO
            in IMAGE_EXTS -> TorrentFileKind.IMAGE
            else -> TorrentFileKind.OTHER
        }

    companion object {
        val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "mov", "m4v", "avi", "ts", "wmv", "flv", "mpg", "mpeg", "3gp", "ogv")
        val AUDIO_EXTS = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma")
        val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif")
    }
}

/** A torrent's name, size and files, read from its metadata. */
data class TorrentMeta(val infoHash: String, val name: String, val totalSize: Long, val files: List<TorrentFile>) {

    /** The files AnyDrop can put in the library (videos, music, pictures). */
    val mediaFiles: List<TorrentFile> get() = files.filter { it.kind != TorrentFileKind.OTHER }

    /**
     * What to tick to start with: the videos, minus small extras like a "sample" clip; with no
     * video, the music; with neither, everything shown.
     */
    fun defaultSelection(): Set<Int> {
        val videos = files.filter { it.kind == TorrentFileKind.VIDEO }
        if (videos.isNotEmpty()) {
            val largest = videos.maxOf { it.size }
            return videos.filter { it.size >= largest / 5 }.map { it.index }.toSet()
        }
        val audio = files.filter { it.kind == TorrentFileKind.AUDIO }
        if (audio.isNotEmpty()) return audio.map { it.index }.toSet()
        return mediaFiles.map { it.index }.toSet()
    }
}

/** "1.9 GB", "640 MB", "12 KB". */
fun sizeLabel(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(Locale.ROOT, bytes / 1e9)
    bytes >= 1_000_000 -> "%.0f MB".format(Locale.ROOT, bytes / 1e6)
    bytes > 0 -> "%.0f KB".format(Locale.ROOT, bytes / 1e3)
    else -> ""
}

/** A torrent's file as an entry of the deck's list (kept with the session like any format). */
fun TorrentFile.toFormat(torrentName: String) = FormatInfo(
    id = FormatInfo.TORRENT_ID + index,
    extension = extension,
    resolution = path,
    filesize = size,
    vcodec = FormatInfo.TORRENT_CODEC,
    acodec = FormatInfo.TORRENT_CODEC,
    isAudioOnly = kind == TorrentFileKind.AUDIO,
    isImage = kind == TorrentFileKind.IMAGE,
    title = torrentName
)

/** The torrent files listed in [this] (the reverse of [toFormat]). */
fun List<FormatInfo>.torrentFileList(): List<TorrentFile> =
    filter { it.isTorrent }.mapNotNull { f -> f.torrentFiles.singleOrNull()?.let { TorrentFile(it, f.resolution, f.filesize) } }

/** One download entry for the chosen files of a torrent. */
fun List<TorrentFile>.asDownload(torrentName: String): FormatInfo {
    val main = maxBy { it.size }
    return FormatInfo(
        id = FormatInfo.TORRENT_ID + map { it.index }.sorted().joinToString(","),
        extension = main.extension.ifBlank { "mp4" },
        // The tracker shows this as the quality: the size is what says most about a torrent.
        resolution = sizeLabel(sumOf { it.size }),
        filesize = sumOf { it.size },
        vcodec = FormatInfo.TORRENT_CODEC,
        acodec = FormatInfo.TORRENT_CODEC,
        isAudioOnly = main.kind == TorrentFileKind.AUDIO,
        isImage = main.kind == TorrentFileKind.IMAGE,
        title = torrentName
    )
}
