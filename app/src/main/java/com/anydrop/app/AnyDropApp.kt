package com.anydrop.app

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import com.anydrop.app.R
import com.anydrop.app.data.repository.DownloadRepository
import com.anydrop.app.data.repository.DownloadTracker
import com.anydrop.app.data.repository.LibraryRepository
import com.anydrop.app.data.repository.SettingsRepository
import com.anydrop.app.data.repository.readAppLanguageSync
import com.anydrop.app.ui.theme.TapeDeckPaletteState
import com.anydrop.app.util.Constants
import com.anydrop.app.util.LocaleHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

// Legacy one-shot (still read on cold start).
var pendingSharedUrl: String? = null

/**
 * Reactive stream of shared URLs. MainActivity.onNewIntent emits here; the deck
 * ViewModel collects it, so a share into an ALREADY-RUNNING app (singleTask) still
 * pastes the URL — the ViewModel's init{} only runs once and can't catch later shares.
 */
val sharedUrlFlow = MutableStateFlow<String?>(null)

/**
 * App-lifetime coroutine scope for downloads. Downloads must NOT run in a
 * viewModelScope — from the share bottom sheet the ViewModel is cleared as soon as
 * ShareActivity finishes, which would cancel the download mid-flight (before the
 * MediaStore export), leaving files invisible to other apps. This scope survives
 * the Activity/ViewModel so the download + public export always completes.
 */
val appDownloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@HiltAndroidApp
class AnyDropApp : Application() {

    @Inject
    lateinit var downloadTracker: DownloadTracker

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var libraryRepository: LibraryRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(
            LocaleHelper.wrapAppLocale(base, readAppLanguageSync(base))
        )
    }

    override fun onCreate() {
        super.onCreate()
        followDarkMode()
        createNotificationChannel()
        reconcileInterruptedDownloads()
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
        Coil.setImageLoader(imageLoader)
    }

    /**
     * Keeps the palette behind the global color getters in step with the saved
     * setting for the life of the process. UNDISPATCHED applies the saved mode
     * before any activity composes (no flash of the wrong palette), and later
     * switches land on the main thread before the next frame.
     */
    private fun followDarkMode() {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            .launch(start = CoroutineStart.UNDISPATCHED) {
                settingsRepository.darkModeFlow.collect(TapeDeckPaletteState::select)
            }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            Constants.DOWNLOAD_CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW
        ).setName(getString(R.string.notification_channel_downloads)).build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    /**
     * Startup recovery for downloads that died with the process:
     *  (1) A tracker entry whose output file is COMPLETE was likely killed right
     *      after downloading but before the MediaStore export — re-export it so it
     *      becomes visible to other apps, then drop it from the tracker.
     *  (2) Incomplete/interrupted entries are surfaced as "Failed — download
     *      interrupted" (never as stuck "Downloading…") and their orphaned partial
     *      / temp files are swept off disk.
     *  (3) A folder-wide sweep removes any leftover *.part / .temp_* junk.
     */
    private fun reconcileInterruptedDownloads() {
        appDownloadScope.launch {
            val stale = downloadTracker.staleDownloads()
            var restored = false
            stale.forEach { (path, title) ->
                val output = File(path)
                val complete = output.exists() && output.length() > 0L &&
                    !File(path + ".part").exists()
                if (complete) {
                    val ok = runCatching { downloadRepository.reexportToMediaStore(path) }.getOrDefault(false)
                    downloadTracker.markDone(path)
                    restored = restored || ok
                } else {
                    cleanOrphans(output)
                    postInterruptedNotification(title)
                }
            }
            sweepOrphanFiles(libraryRepository.getVideoDir())
            sweepOrphanFiles(libraryRepository.getImageDir())
            sweepOrphanFiles(libraryRepository.getAudioDir())
            if (restored) {
                libraryRepository.invalidateIndexCache()
            }
            downloadTracker.clearAll()
        }
    }

    private fun postInterruptedNotification(title: String) {
        try {
            val notification = NotificationCompat.Builder(this, Constants.DOWNLOAD_CHANNEL_ID)
                .setContentTitle(title.ifBlank { getString(R.string.app_name) })
                .setContentText(getString(R.string.notification_download_interrupted))
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(this)
                .notify(title, Constants.DOWNLOAD_PROGRESS_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — the cleanup still ran, just no toast.
        }
    }

    /** Remove the partial download and any mux temp siblings for a dead download. */
    private fun cleanOrphans(output: File) {
        val dir = output.parentFile ?: return
        val base = output.name
        dir.listFiles()?.forEach { f ->
            if (f.isFile && (f.name == "$base.part" || f.name.startsWith(".temp_"))) {
                f.delete()
            }
        }
    }

    private fun sweepOrphanFiles(dir: File) {
        dir.listFiles()?.forEach { f ->
            if (f.isFile && (f.name.endsWith(".part") || f.name.startsWith(".temp_"))) {
                f.delete()
            }
        }
    }
}
