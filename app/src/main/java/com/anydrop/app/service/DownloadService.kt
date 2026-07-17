package com.anydrop.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.anydrop.app.MainActivity
import com.anydrop.app.util.Constants
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DownloadService : Service() {

    // True while a download is actively running. Prevents trailing/out-of-order
    // progress events (e.g. a late frame from a video+audio merge) from restarting
    // the service and re-showing "Downloading… 5%" after it already completed.
    private var active = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_DOWNLOAD_START -> {
                active = true
                val url = intent.getStringExtra("url") ?: return START_NOT_STICKY
                showDownloadingNotification(url)
            }
            ACTION_DOWNLOAD_PROGRESS -> {
                // Ignore progress that arrives after completion/cancel.
                if (active) {
                    val pct = intent.getIntExtra("percentage", 0)
                    updateProgress(pct)
                }
            }
            ACTION_DOWNLOAD_COMPLETE -> {
                if (active) {
                    active = false
                    showComplete(intent.getStringExtra("title") ?: "Download complete")
                }
            }
            Constants.ACTION_DOWNLOAD_CANCEL -> {
                active = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun showDownloadingNotification(url: String) {
        val notification = NotificationCompat.Builder(this, Constants.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("AnyDrop")
            .setContentText("Downloading…")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, 0, true) // indeterminate until first % arrives
            .setSilent(true)
            .build()
        startForeground(Constants.DOWNLOAD_NOTIFICATION_ID, notification)
    }

    private fun updateProgress(percentage: Int) {
        val notification = NotificationCompat.Builder(this, Constants.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("AnyDrop")
            .setContentText("Downloading… $percentage%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, percentage.coerceIn(0, 100), percentage <= 0)
            .setSilent(true)
            .build()
        // Keep it a foreground notification so it can't be swiped away mid-download.
        startForeground(Constants.DOWNLOAD_NOTIFICATION_ID, notification)
    }

    private fun showComplete(title: String) {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, Constants.DOWNLOAD_CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this)
            .notify(Constants.DOWNLOAD_NOTIFICATION_ID, notification)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            Constants.DOWNLOAD_CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW
        ).setName("Downloads").build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_DOWNLOAD_PROGRESS = "com.anydrop.app.DOWNLOAD_PROGRESS"
        const val ACTION_DOWNLOAD_COMPLETE = "com.anydrop.app.DOWNLOAD_COMPLETE"

        fun startDownload(context: Context, url: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = Constants.ACTION_DOWNLOAD_START
                putExtra("url", url)
            }
            context.startForegroundService(intent)
        }

        fun updateProgress(context: Context, percentage: Int) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_DOWNLOAD_PROGRESS
                putExtra("percentage", percentage)
            }
            context.startService(intent)
        }

        /** Mark done → show a "Download Complete" notification and stop the service. */
        fun finishDownload(context: Context, title: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_DOWNLOAD_COMPLETE
                putExtra("title", title)
            }
            context.startService(intent)
        }

        fun cancelDownload(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = Constants.ACTION_DOWNLOAD_CANCEL
            }
            context.startService(intent)
        }
    }
}
