package com.anydrop.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anydrop.app.MainActivity
import com.anydrop.app.R
import com.anydrop.app.data.repository.AppUpdate
import com.anydrop.app.data.repository.UpdateRepository
import com.anydrop.app.util.Constants
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Once a day (when online) looks for a new release and, the first time a version shows
 * up, posts a notification that opens the update dialog. Free: WorkManager plus the
 * public GitHub API, no push service.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun updateRepository(): UpdateRepository
    }

    override suspend fun doWork(): Result {
        val updates = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).updateRepository()
        val update = updates.check(force = false) ?: return Result.success()
        if (!updates.isSkipped(update.version) && !updates.wasNotified(update.version)) {
            notify(applicationContext, update)
            updates.markNotified(update.version)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "anydrop-update-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun notify(context: Context, update: AppUpdate) {
            val manager = NotificationManagerCompat.from(context)
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(Constants.UPDATE_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.notification_channel_updates))
                    .build()
            )
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .putExtra(Constants.EXTRA_SHOW_UPDATE, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, Constants.UPDATE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(context.getString(R.string.update_notification_title, update.version))
                .setContentText(context.getString(R.string.update_notification_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                manager.notify(Constants.UPDATE_NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // Notifications not allowed: the dialog still appears the next time the app opens.
            }
        }
    }
}
