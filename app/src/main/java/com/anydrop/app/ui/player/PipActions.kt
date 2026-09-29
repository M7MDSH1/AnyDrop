package com.anydrop.app.ui.player

import android.app.PendingIntent
import android.app.RemoteAction
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.anydrop.app.R

/**
 * Constants + helpers for Picture-in-Picture control actions (play/pause, next,
 * previous). PiP controls are delivered as broadcasts, caught by a receiver
 * registered in the player, which then drives the ExoPlayer instance.
 */
object PipActions {
    const val ACTION_CONTROL = "com.anydrop.app.PIP_CONTROL"
    const val EXTRA_CONTROL_TYPE = "control_type"

    const val CONTROL_PLAY = 1
    const val CONTROL_PAUSE = 2
    const val CONTROL_NEXT = 3
    const val CONTROL_PREVIOUS = 4

    private fun pendingIntent(context: android.content.Context, controlType: Int): PendingIntent {
        val intent = Intent(ACTION_CONTROL)
            .setPackage(context.packageName)
            .putExtra(EXTRA_CONTROL_TYPE, controlType)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, controlType, intent, flags)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun action(
        context: android.content.Context,
        iconRes: Int,
        title: String,
        controlType: Int,
        enabled: Boolean = true
    ): RemoteAction {
        val ra = RemoteAction(
            Icon.createWithResource(context, iconRes),
            title,
            title,
            pendingIntent(context, controlType)
        )
        ra.isEnabled = enabled
        return ra
    }

    /**
     * Build the [previous] [play/pause] [next] action row for the PiP window.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun buildActions(
        context: android.content.Context,
        isPlaying: Boolean,
        hasPrevious: Boolean,
        hasNext: Boolean
    ): List<RemoteAction> {
        val playPause = if (isPlaying) {
            action(context, R.drawable.ic_pip_pause, context.getString(R.string.pip_pause), CONTROL_PAUSE)
        } else {
            action(context, R.drawable.ic_pip_play, context.getString(R.string.pip_play), CONTROL_PLAY)
        }
        return listOf(
            action(context, R.drawable.ic_pip_previous, context.getString(R.string.pip_previous), CONTROL_PREVIOUS, hasPrevious),
            playPause,
            action(context, R.drawable.ic_pip_next, context.getString(R.string.pip_next), CONTROL_NEXT, hasNext)
        )
    }
}
