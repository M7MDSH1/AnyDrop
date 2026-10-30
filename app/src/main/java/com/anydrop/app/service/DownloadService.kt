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

    /** Tags of downloads queued or running. */
    private val activeTags = mutableSetOf<String>()

    /** Queued downloads held back by "Wi-Fi only" (the summary says so when that's all there is). */
    private val waitingForWifi = mutableSetOf<String>()

    /** A result notification was posted, so the finished group gets its summary. */
    private var postedResult = false

    /**
     * Downloads with a notification of their own showing. The pin heads them as the group's
     * summary; without any (downloads still only queued) it's a plain notification, since
     * Android doesn't show a group summary that has nothing under it.
     */
    private val children = mutableSetOf<String>()

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
                    // Queued downloads: reserved up front so the notification stays while they
                    // wait their turn (no tracker mark — each one registers when it starts).
                    val batchTitle = intent.getStringExtra("batch_title").orEmpty()
                    batchTags.forEach { tag ->
                        activeTags.add(tag)
                        titles[tag] = batchTitle
                    }
                    pin()
                    return START_NOT_STICKY
                }

                val tag = intent.getStringExtra("tag") ?: return START_NOT_STICKY
                val title = intent.getStringExtra("title").orEmpty()
                activeTags.add(tag)
                waitingForWifi.remove(tag)
                titles[tag] = title
                lastPercentages[tag] = 0
                pin()
                appDownloadScope.launch { downloadTracker.markActive(tag, title) }
            }
            Constants.ACTION_DOWNLOAD_WAITING -> {
                val tag = intent.getStringExtra("tag") ?: return START_NOT_STICKY
                if (tag !in activeTags) return START_NOT_STICKY
                if (intent.getBooleanExtra("wifi", false)) waitingForWifi.add(tag) else waitingForWifi.remove(tag)
                refreshSummary()
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
                    .setGroup(GROUP_DOWNLOADS)
                    .setContentIntent(openDownloadsIntent(this))
                    .build()
                NotificationManagerCompat.from(this)
                    .notify(tag, Constants.DOWNLOAD_PROGRESS_NOTIFICATION_ID, notification)
                if (children.add(tag)) refreshSummary()
            }
            Constants.ACTION_DOWNLOAD_COMPLETE ->
                finishWith(intent, getString(R.string.notification_complete), android.R.drawable.stat_sys_download_done)
            Constants.ACTION_DOWNLOAD_FAILED ->
                finishWith(intent, getString(R.string.notification_failed), android.R.drawable.stat_notify_error)
            Constants.ACTION_DOWNLOAD_CANCEL -> {
                // Paused in the app: its progress notification just goes away.
                val tag = intent.getStringExtra("tag") ?: return START_NOT_STICKY
                if (tag !in activeTags) return START_NOT_STICKY
                NotificationManagerCompat.from(this).cancel(tag, Constants.DOWNLOAD_PROGRESS_NOTIFICATION_ID)
                children.remove(tag)
                finishTag(tag)
            }
        }
        return START_NOT_STICKY
    }

    /** The foreground pin, put up once and kept up to date. */
    private fun pin() {
        if (foregroundPinned) {
            refreshSummary()
        } else {
            startForeground(Constants.FOREGROUND_SERVICE_NOTIFICATION_ID, buildSummary(summaryText()))
            foregroundPinned = true
        }
    }

    /** A download ended: its result notification replaces its progress, then the bookkeeping. */
    private fun finishWith(intent: Intent, verb: String, icon: Int) {
        val tag = intent.getStringExtra("tag") ?: return
        if (tag !in activeTags) return
        postFinalNotification(tag, verb, icon)
        finishTag(tag)
    }

    /** Shared terminal-path bookkeeping: drop the tag and clear tracker state. */
    private fun finishTag(tag: String) {
        activeTags.remove(tag)
        waitingForWifi.remove(tag)
        lastPercentages.remove(tag)
        titles.remove(tag)
        appDownloadScope.launch { downloadTracker.markDone(tag) }
        refreshSummary()
        if (activeTags.isEmpty()) {
            foregroundPinned = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            // The results stay grouped under a summary that still opens the downloads list
            // (a group Android makes up itself would just open the app).
            if (postedResult) {
                NotificationManagerCompat.from(this).notify(
                    Constants.FOREGROUND_SERVICE_NOTIFICATION_ID,
                    summaryBuilder(getString(R.string.notification_downloads_finished))
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setAutoCancel(true)
                        .build()
                )
            }
            postedResult = false
            children.clear()
            stopSelf()
        }
    }

    private fun postFinalNotification(tag: String, verb: String, icon: Int) {
        val builder = NotificationCompat.Builder(this, Constants.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(titles[tag] ?: getString(R.string.app_name))
            .setContentText(verb)
            .setSmallIcon(icon)
            .setProgress(0, 0, false)
            .setAutoCancel(true)
            .setGroup(GROUP_DOWNLOADS)
            .setContentIntent(openDownloadsIntent(this))
        NotificationManagerCompat.from(this)
            .notify(tag, Constants.DOWNLOAD_PROGRESS_NOTIFICATION_ID, builder.build())
        postedResult = true
        children.add(tag)
    }

    private fun summaryText(): String = when {
        allWaitingForWifi() ->
            getString(R.string.notification_waiting_wifi, activeTags.size)
        activeTags.size == 1 -> titles[activeTags.first()] ?: getString(R.string.notification_download_active)
        else -> getString(R.string.notification_downloading_files, activeTags.size)
    }

    /** Keep the single foreground pin up to date; never per-file, never a new pin. */
    private fun refreshSummary() {
        if (!foregroundPinned || activeTags.isEmpty()) return
        startForeground(Constants.FOREGROUND_SERVICE_NOTIFICATION_ID, buildSummary(summaryText()))
    }

    private fun buildSummary(text: String) =
        summaryBuilder(text, asSummary = children.isNotEmpty())
            .setSmallIcon(android.R.drawable.stat_sys_download)
            // Indeterminate while downloading (per-file % lives in tagged notifications); none
            // while everything waits for Wi-Fi, so the text saying so shows.
            .setProgress(0, 0, !allWaitingForWifi())
            .setOngoing(true)
            .build()

    private fun allWaitingForWifi() = waitingForWifi.isNotEmpty() && waitingForWifi.containsAll(activeTags)

    /** The pin heads the group of per-file notifications, so tapping the group opens the downloads too. */
    private fun summaryBuilder(text: String, asSummary: Boolean = true) =
        NotificationCompat.Builder(this, Constants.DOWNLOAD_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSilent(true)
            .setContentIntent(openDownloadsIntent(this))
            .apply { if (asSummary) setGroup(GROUP_DOWNLOADS).setGroupSummary(true) }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {

        private const val GROUP_DOWNLOADS = "com.anydrop.app.DOWNLOADS"

        // Own request code: the update notification's intent to MainActivity uses 0, and two
        // intents that differ only in extras would share (and overwrite) one PendingIntent.
        private const val REQUEST_OPEN_DOWNLOADS = 1

        /** A tap on any download notification opens AnyDrop on the deck's downloads list. */
        fun openDownloadsIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_DOWNLOADS,
            Intent(context, MainActivity::class.java)
                .putExtra(Constants.EXTRA_SHOW_DOWNLOADS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        /** A download starts now (it was [register]ed when queued). */
        fun startDownload(context: Context, tag: String, title: String) {
            start(context, Intent(context, DownloadService::class.java).apply {
                action = Constants.ACTION_DOWNLOAD_START
                putExtra("tag", tag)
                putExtra("title", title)
            })
        }

        /** Downloads were queued: the notification goes up and stays while they wait their turn. */
        fun register(context: Context, tags: List<String>, title: String) {
            start(context, Intent(context, DownloadService::class.java).apply {
                action = Constants.ACTION_DOWNLOAD_START
                putStringArrayListExtra("batch_tags", ArrayList(tags))
                putExtra("batch_title", title)
            })
        }

        /** A queued download is (or no longer is) waiting for Wi-Fi. */
        fun setWaitingForWifi(context: Context, tag: String, waiting: Boolean) {
            start(context, Intent(context, DownloadService::class.java).apply {
                action = Constants.ACTION_DOWNLOAD_WAITING
                putExtra("tag", tag)
                putExtra("wifi", waiting)
            })
        }

        /**
         * A queued download can get its turn while the app is in the background, where Android
         * refuses to start a foreground service. The service is already up then (it was started
         * when the download was queued), so a plain start reaches it.
         */
        private fun start(context: Context, intent: Intent) {
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                runCatching { context.startService(intent) }
            }
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
