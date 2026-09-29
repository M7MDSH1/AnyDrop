package com.anydrop.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.anydrop.app.MainActivity
import com.anydrop.app.R
import com.anydrop.app.appDownloadScope
import com.anydrop.app.data.repository.DownloadTracker
import com.anydrop.app.data.repository.readAppLanguageSync
import com.anydrop.app.util.Constants
import com.anydrop.app.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Foreground notification shell for downloads.
 *
 * One constant foreground pin (FOREGROUND_SERVICE_NOTIFICATION_ID) shows a static
 * summary; every per-file notification is posted with notify(tag, ...) using the
 * download's output path as a stable, unique tag. Tracker state (DownloadTracker)
 * is persisted at start and cleared on every terminal path, so an interrupted
 * download is never mistaken for a completed one after a process kill.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    /** Tags of downloads currently running (or pre-registered as a batch). */
    private val activeTags = mutableSetOf<String>()

    /** Display title per active tag, pre-populated during batch registration. */
    private val titles = mutableMapOf<String, String>()

    /** Last posted percentage per tag — progress events are deduplicated on it. */
    private val lastPercentages = mutableMapOf<String, Int>()

    private var foregroundPinned = false

    @Inject
    lateinit var downloadTracker: DownloadTracker

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(
            LocaleHelper.wrapAppLocale(newBase, readAppLanguageSync(newBase))
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_DOWNLOAD_START -> {
                val batchTags = intent.getStringArrayListExtra("batch_tags")
                if (batchTags != null) {
                    // Batch pre-registration: reserve all tags up front so teardown
                    // only fires once the whole batch is done (no pin, no tracker
                    // mark — each item registers itself when it actually starts).
                    val batchTitle = intent.getStringExtra("batch_title").orEmpty()
                    batchTags.forEach { tag ->
                        activeTags.add(tag)
                        titles[tag] = batchTitle
                    }
                    return START_NOT_STICKY
                }

                val tag = intent.getStringExtra("tag") ?: return START_NOT_STICKY
                val title = intent.getStringExtra("title").orEmpty()
                activeTags.add(tag)
                titles[tag] = title
                lastPercentages[tag] = 0
                if (foregroundPinned) {
                    refreshSummary()
                } else {
                    startForeground(Constants.FOREGROUND_SERVICE_NOTIFICATION_ID, buildSummary(summaryText()))
                    foregroundPinned = true
                }
                appDownloadScope.launch { downloadTracker.markActive(tag, title) }
            }
            Constants.ACTION_DOWNLOAD_PROGRESS -> {
                val tag = intent.getStringExtra("tag") ?: return START_NOT_STICKY
                // Ignore progress that arrives after completion/cancel (e.g. a late
                // frame from a video+audio merge).
                if (!activeTags.contains(tag)) return START_NOT_STICKY
                val pct = intent.getIntExtra("percentage", 0).coerceIn(0, 100)
                if (lastPercentages[tag] == pct) return START_NOT_STICKY
                lastPercentages[tag] = pct
                val notification = NotificationCompat.Builder(this, Constants.DOWNLOAD_CHANNEL_ID)
                    .setContentTitle(titles[tag] ?: getString(R.string.app_name))
                    .setContentText(getString(R.string.download_progress, pct))
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOngoing(true)
                    .setProgress(100, pct, false)
                    .setSilent(true)
                    .build()
                NotificationManagerCompat.from(this)
                    .notify(tag, Constants.DOWNLOAD_PROGRESS_NOTIFICATION_ID, notification)
            }
            Constants.ACTION_DOWNLOAD_COMPLETE -> {
                if (!finishTag(intent)) return START_NOT_STICKY
                val tag = intent.getStringExtra("tag") ?: return START_NOT_STICKY
                postFinalNotification(tag, getString(R.string.notification_complete), android.R.drawable.stat_sys_download_done, withMainIntent = true)
            }
            Constants.ACTION_DOWNLOAD_FAILED -> {
                if (!finishTag(intent)) return START_NOT_STICKY
                val tag = intent.getStringExtra("tag") ?: return START_NOT_STICKY
                postFinalNotification(tag, getString(R.string.notification_failed), android.R.drawable.stat_notify_error)
            }
            Constants.ACTION_DOWNLOAD_CANCEL -> {
                if (!finishTag(intent)) return START_NOT_STICKY
                val tag = intent.getStringExtra("tag") ?: return START_NOT_STICKY
                postFinalNotification(tag, getString(R.string.notification_cancelled), android.R.drawable.stat_notify_error)
            }
        }
        return START_NOT_STICKY
    }

    /** Shared terminal-path bookkeeping: drop the tag and clear tracker state. */
    private fun finishTag(intent: Intent?): Boolean {
        val tag = intent?.getStringExtra("tag") ?: return false
        if (!activeTags.remove(tag)) return false
        lastPercentages.remove(tag)
        titles.remove(tag)
        appDownloadScope.launch { downloadTracker.markDone(tag) }
        refreshSummary()
        if (activeTags.isEmpty()) {
            foregroundPinned = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return true
    }

    private fun postFinalNotification(tag: String, verb: String, icon: Int, withMainIntent: Boolean = false) {
        val builder = NotificationCompat.Builder(this, Constants.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(titles[tag] ?: getString(R.string.app_name))
            .setContentText(verb)
            .setSmallIcon(icon)
            .setProgress(0, 0, false)
            .setAutoCancel(true)
        if (withMainIntent) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.setContentIntent(pendingIntent)
        }
        NotificationManagerCompat.from(this)
            .notify(tag, Constants.DOWNLOAD_PROGRESS_NOTIFICATION_ID, builder.build())
    }

    private fun summaryText(): String = when (activeTags.size) {
        1 -> titles[activeTags.first()] ?: getString(R.string.notification_download_active)
        else -> getString(R.string.notification_downloading_files, activeTags.size)
    }

    /** Keep the single foreground pin up to date; never per-file, never a new pin. */
    private fun refreshSummary() {
        if (!foregroundPinned || activeTags.isEmpty()) return
        startForeground(Constants.FOREGROUND_SERVICE_NOTIFICATION_ID, buildSummary(summaryText()))
    }

    private fun buildSummary(text: String) =
        NotificationCompat.Builder(this, Constants.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(0, 0, true) // indeterminate: per-file % lives in tagged notifications
            .setOngoing(true)
            .setSilent(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {

        /** Start a download (or pre-register a sequential batch via [batchTags]). */
        fun startDownload(context: Context, tag: String, title: String, batchTags: List<String>? = null) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = Constants.ACTION_DOWNLOAD_START
                putExtra("tag", tag)
                putExtra("title", title)
                if (batchTags != null) {
                    putStringArrayListExtra("batch_tags", ArrayList(batchTags))
                    putExtra("batch_title", title)
                }
            }
            context.startForegroundService(intent)
        }

        fun updateProgress(context: Context, tag: String, percentage: Int) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = Constants.ACTION_DOWNLOAD_PROGRESS
                putExtra("tag", tag)
                putExtra("percentage", percentage)
            }
            context.startService(intent)
        }

        /** Mark done → show a "[title] Complete" notification; teardown when idle. */
        fun finishDownload(context: Context, tag: String, title: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = Constants.ACTION_DOWNLOAD_COMPLETE
                putExtra("tag", tag)
                putExtra("title", title)
            }
            context.startService(intent)
        }

        fun notifyFailed(context: Context, tag: String, title: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = Constants.ACTION_DOWNLOAD_FAILED
                putExtra("tag", tag)
                putExtra("title", title)
            }
            context.startService(intent)
        }

        fun cancelDownload(context: Context, tag: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = Constants.ACTION_DOWNLOAD_CANCEL
                putExtra("tag", tag)
            }
            context.startService(intent)
        }
    }
}
