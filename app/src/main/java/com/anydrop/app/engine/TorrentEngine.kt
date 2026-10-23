package com.anydrop.app.engine

import android.content.Context
import android.util.Log
import com.anydrop.app.R
import com.anydrop.app.data.model.DownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** A torrent download's progress, then its finished files. */
sealed interface TorrentEvent {
    data class Progress(val state: DownloadState.Progress) : TorrentEvent
    data class Done(val files: List<File>, val workDir: File) : TorrentEvent
}

/** A torrent problem worth showing as is (bad link, no one sharing it…). */
class TorrentException(message: String) : Exception(message)

/**
 * Peer-to-peer downloads (magnet links and .torrent files) with libtorrent. The session only
 * runs while something uses it: reading a torrent's file list or downloading it. Like any
 * torrent app, a download also uploads the parts it already has to the other peers; when it
 * finishes the torrent is removed, so AnyDrop does not keep sharing afterwards.
 */
@Singleton
class TorrentEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Created on first use: loading the native library has no business slowing app start.
    private val session: SessionManager by lazy { SessionManager(false) }
    private val lock = Any()
    private var users = 0
    private val idleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var idleStop: Job? = null

    /** Where torrents download to before their files move into the library. */
    private val workRoot: File get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "Torrents")
    private val metaDir: File get() = File(context.filesDir, "torrents").apply { mkdirs() }

    /** The file list of a magnet link: kept from before, or asked of the peers sharing it. */
    suspend fun metadata(magnet: String): TorrentMeta = withContext(Dispatchers.IO) {
        val hash = Magnet.infoHash(magnet) ?: throw TorrentException(context.getString(R.string.torrent_bad_link))
        val bytes = cachedInfo(hash) ?: fetchInfo(magnet, hash)
        parse(bytes).toMeta(hash)
    }

    /** Opens a .torrent file's contents; returns its file list and a magnet link for it. */
    fun open(bytes: ByteArray): Pair<TorrentMeta, String> {
        val info = parse(bytes)
        val hash = info.infoHash().toHex().lowercase(Locale.ROOT)
        File(metaDir, "$hash.torrent").writeBytes(bytes)
        return info.toMeta(hash) to Magnet.of(hash, info.name())
    }

    /** Fetches and opens a .torrent file from the web. */
    suspend fun openUrl(url: String): Pair<TorrentMeta, String> = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) throw TorrentException(context.getString(R.string.torrent_bad_file))
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_TORRENT_FILE) throw TorrentException(context.getString(R.string.torrent_bad_file))
                }
                out.toByteArray()
            }
            open(bytes)
        } finally {
            conn.disconnect()
        }
    }

    /** The file list of any torrent link: a magnet link or the web address of a .torrent file. */
    suspend fun open(link: String): Pair<TorrentMeta, String> =
        if (Magnet.isMagnet(link)) metadata(link) to link else openUrl(link)

    /**
     * Downloads the files at [selected] (indexes into the torrent's file list) of [link], a magnet
     * link or a .torrent file's address. Emits progress about once a second, then
     * [TorrentEvent.Done] with the finished files. Cancelling stops the torrent and keeps what
     * was downloaded, so starting it again carries on.
     */
    fun download(link: String, selected: Set<Int>): Flow<TorrentEvent> = flow {
        emit(phase(R.string.torrent_finding_peers))
        val magnet = if (Magnet.isMagnet(link)) link else openUrl(link).second
        val hash = Magnet.infoHash(magnet) ?: throw TorrentException(context.getString(R.string.torrent_bad_link))
        val info = parse(cachedInfo(hash) ?: fetchInfo(magnet, hash))
        val saveDir = File(workRoot, hash).apply { mkdirs() }
        val priorities = Array(info.numFiles()) { if (it in selected) Priority.DEFAULT else Priority.IGNORE }
        acquire()
        var handle: TorrentHandle? = null
        try {
            session.download(info, saveDir, null, priorities, null, torrent_flags_t())
            // Torrents are added in the background; wait for it to show up.
            val sha = Sha1Hash.parseHex(hash)
            var waited = 0
            while (handle == null && waited < ADD_TIMEOUT_MS) {
                handle = session.find(sha)?.takeIf { it.isValid }
                if (handle == null) {
                    delay(100)
                    waited += 100
                }
            }
            val h = handle ?: throw TorrentException(context.getString(R.string.torrent_failed))
            // A file list learnt from peers carries no trackers or web seeds: take the magnet's.
            Magnet.trackers(magnet).forEach { runCatching { h.addTracker(AnnounceEntry(it)) } }
            Magnet.webSeeds(magnet).forEach { runCatching { h.addUrlSeed(it) } }
            h.resume()
            var ticks = 0
            while (true) {
                val status = h.status()
                if (ticks++ % 30 == 0) {
                    Log.i(TAG, "$hash ${status.state()} peers=${status.numPeers()} seeds=${status.numSeeds()} " +
                        "rate=${status.downloadPayloadRate()} done=${status.totalWantedDone()}/${status.totalWanted()}")
                }
                status.errorCode()?.takeIf { it.isError }?.let { throw TorrentException(it.message) }
                if (status.isFinished || status.state() == TorrentStatus.State.SEEDING) break
                emit(progress(status))
                delay(1000)
            }
            val files = selected.sorted()
                .map { File(saveDir, info.files().filePath(it)) }
                .filter { it.isFile && it.length() > 0 }
            emit(TorrentEvent.Done(files, saveDir))
        } finally {
            // Stop sharing; the downloaded data stays on disk.
            handle?.let { h -> runCatching { session.remove(h) } }
            release()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Stopped downloads keep their data so they can carry on; a week later it's assumed
     * abandoned and removed. Runs before the session starts, so nothing is in use.
     */
    private fun pruneOldDownloads() {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        workRoot.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") && it.lastModified() < cutoff }
            ?.forEach { it.deleteRecursively() }
    }

    /** Deletes what's left of a torrent's download folder once its files have moved on. */
    fun cleanUp(workDir: File) {
        if (workDir.parentFile == workRoot) workDir.deleteRecursively()
    }

    private suspend fun fetchInfo(magnet: String, hash: String): ByteArray {
        acquire()
        try {
            val tmp = File(workRoot, ".meta").apply { mkdirs() }
            val bytes = runInterruptible(Dispatchers.IO) { session.fetchMagnet(magnet, METADATA_TIMEOUT_S, tmp) }
                ?: throw TorrentException(context.getString(R.string.torrent_no_peers))
            File(metaDir, "$hash.torrent").writeBytes(bytes)
            return bytes
        } finally {
            release()
        }
    }

    private fun cachedInfo(hash: String): ByteArray? =
        File(metaDir, "$hash.torrent").takeIf { it.isFile && it.length() > 0 }?.readBytes()

    private fun parse(bytes: ByteArray): TorrentInfo =
        try {
            TorrentInfo.bdecode(bytes)
        } catch (e: Exception) {
            throw TorrentException(context.getString(R.string.torrent_bad_file))
        }

    private fun TorrentInfo.toMeta(hash: String): TorrentMeta {
        val fs = files()
        return TorrentMeta(
            infoHash = hash,
            name = name(),
            totalSize = totalSize(),
            files = (0 until fs.numFiles()).map { TorrentFile(it, fs.filePath(it), fs.fileSize(it)) }
        )
    }

    /** Starts the session for one more user (a lookup or a download). */
    private fun acquire() {
        synchronized(lock) {
            idleStop?.cancel()
            idleStop = null
            try {
                // Plain file writes: libtorrent's default memory-mapped files can take the whole
                // app down on Android's shared storage.
                if (!session.isRunning) {
                    pruneOldDownloads()
                    session.start(SessionParams().apply { setPosixDiskIO() })
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Torrent engine unavailable", e)
                throw TorrentException(context.getString(R.string.torrent_unsupported))
            }
            users++
        }
    }

    /** One user is done; the session stops when nobody has used it for a while. */
    private fun release() {
        synchronized(lock) {
            users = (users - 1).coerceAtLeast(0)
            if (users > 0) return
            idleStop?.cancel()
            idleStop = idleScope.launch {
                delay(IDLE_STOP_MS)
                synchronized(lock) {
                    if (users == 0 && session.isRunning) runCatching { session.stop() }
                }
            }
        }
    }

    private fun phase(res: Int) = TorrentEvent.Progress(DownloadState.Progress(0, phase = context.getString(res)))

    private fun progress(status: TorrentStatus): TorrentEvent.Progress {
        val wanted = status.totalWanted()
        val done = status.totalWantedDone().coerceAtMost(wanted)
        val rate = status.downloadPayloadRate().toLong()
        val phase = when (status.state()) {
            TorrentStatus.State.CHECKING_FILES, TorrentStatus.State.CHECKING_RESUME_DATA -> context.getString(R.string.torrent_checking)
            TorrentStatus.State.DOWNLOADING_METADATA -> context.getString(R.string.torrent_finding_peers)
            else -> if (status.numPeers() == 0) context.getString(R.string.torrent_finding_peers)
            else context.getString(R.string.torrent_peers, status.numPeers())
        }
        return TorrentEvent.Progress(
            DownloadState.Progress(
                percentage = if (wanted > 0) (done * 100 / wanted).toInt().coerceIn(0, 99) else 0,
                speed = if (rate > 0) "${bytesText(rate)}/s" else "",
                eta = if (rate > 0 && wanted > done) clockText(((wanted - done) / rate).toInt()) else "",
                phase = phase,
                sizeText = if (wanted > 0) "${bytesText(done)} / ${bytesText(wanted)}" else ""
            )
        )
    }

    private fun bytesText(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1e9)
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1e6)
        bytes >= 1_000 -> String.format(Locale.US, "%.0f KB", bytes / 1e3)
        else -> "$bytes B"
    }

    private companion object {
        const val TAG = "TorrentEngine"
        /** How long to look for peers that can send a magnet link's file list. */
        const val METADATA_TIMEOUT_S = 90
        const val ADD_TIMEOUT_MS = 15_000
        const val IDLE_STOP_MS = 60_000L
        const val MAX_TORRENT_FILE = 10 * 1024 * 1024
    }
}
