package com.anydrop.app.service

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.anydrop.app.data.repository.readAppLanguageSync
import com.anydrop.app.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground media service that hosts the ExoPlayer used for AUDIO playback so it
 * keeps playing after the user leaves the app, with a media-style notification
 * (play/pause/next/previous) provided automatically by Media3. It plays the Audio tab's
 * songs as one queue (one after another, shuffle, repeat) and carries out [SleepTimer].
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(
            LocaleHelper.wrapAppLocale(newBase, readAppLanguageSync(newBase))
        )
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
        runSleepTimer(player)
    }

    private fun runSleepTimer(player: ExoPlayer) {
        // "End of this song": the player stops itself there; the timer is then done.
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    SleepTimer.cancel()
                }
            }
        })
        scope.launch { SleepTimer.endOfTrack.collect { player.pauseAtEndOfMediaItems = it } }
        // A set time: the sound fades out over the last seconds, then playback pauses.
        scope.launch {
            SleepTimer.endsAt.collectLatest { endsAt ->
                player.volume = 1f
                if (endsAt == null) return@collectLatest
                delay((endsAt - SystemClock.elapsedRealtime() - FADE_MS).coerceAtLeast(0))
                val steps = 20
                for (i in steps downTo 1) {
                    player.volume = i / steps.toFloat()
                    delay(FADE_MS / steps)
                }
                player.pause()
                player.volume = 1f
                SleepTimer.cancel()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Keep playing when the task is swiped away only if actively playing;
        // otherwise stop the service so we don't linger.
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        SleepTimer.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        const val FADE_MS = 10_000L
    }
}
