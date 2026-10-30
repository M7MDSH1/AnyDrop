package com.anydrop.app.service

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The audio sleep timer: set from the player, carried out by [PlaybackService] (same process),
 * so it works with the app closed and the screen off.
 */
object SleepTimer {

    private val _endsAt = MutableStateFlow<Long?>(null)
    /** When playback stops ([SystemClock.elapsedRealtime] millis), or null. */
    val endsAt: StateFlow<Long?> = _endsAt.asStateFlow()

    private val _endOfTrack = MutableStateFlow(false)
    /** Playback stops when the song playing now ends. */
    val endOfTrack: StateFlow<Boolean> = _endOfTrack.asStateFlow()

    fun stopIn(minutes: Int) {
        _endOfTrack.value = false
        _endsAt.value = SystemClock.elapsedRealtime() + minutes * 60_000L
    }

    fun stopAtEndOfTrack() {
        _endsAt.value = null
        _endOfTrack.value = true
    }

    fun cancel() {
        _endsAt.value = null
        _endOfTrack.value = false
    }

    val isSet: Boolean get() = _endsAt.value != null || _endOfTrack.value
}
