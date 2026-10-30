package com.anydrop.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When a queued download may start: no more than "Downloads at once" run together, in the order
 * they were added, and with "Wi-Fi only" on, only while the phone is on Wi-Fi (or Ethernet) —
 * unless the user let that download use mobile data.
 */
@Singleton
class DownloadGate @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: SettingsRepository
) {
    /** Why a download hasn't started yet. */
    enum class Wait { TURN, WIFI }

    private val lock = Any()
    private val waiting = ArrayDeque<String>()
    private val running = mutableSetOf<String>()
    private val mobileDataAllowed = mutableSetOf<String>()

    /** Bumped whenever a waiting download might now be able to start. */
    private val changes = MutableStateFlow(0L)

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val _onWifi = MutableStateFlow(currentlyOnWifi())
    /** The phone is on Wi-Fi or Ethernet (not mobile data). */
    val onWifi: StateFlow<Boolean> = _onWifi.asStateFlow()

    init {
        runCatching {
            connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = networkChanged(caps.isWifi())
                override fun onLost(network: Network) = networkChanged(currentlyOnWifi())
            })
        }
    }

    private fun networkChanged(wifi: Boolean) {
        if (_onWifi.value == wifi) return
        _onWifi.value = wifi
        recheck()
    }

    private fun currentlyOnWifi(): Boolean = runCatching {
        connectivity?.getNetworkCapabilities(connectivity.activeNetwork)?.isWifi() ?: false
    }.getOrDefault(false)

    private fun NetworkCapabilities.isWifi() =
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

    /**
     * Suspends until [tag] may download, telling [onWait] each time the reason it's waiting
     * changes. The download holds its place until [release].
     */
    suspend fun awaitTurn(tag: String, onWait: (Wait) -> Unit) {
        synchronized(lock) { if (tag !in waiting && tag !in running) waiting.addLast(tag) }
        var told: Wait? = null
        try {
            changes.first {
                val reason = synchronized(lock) { claim(tag) }
                if (reason != null && reason != told) {
                    told = reason
                    onWait(reason)
                }
                reason == null
            }
        } catch (e: CancellationException) {
            release(tag)
            throw e
        }
    }

    /** Takes [tag]'s turn when it may start (null), else says why it can't yet. */
    private fun claim(tag: String): Wait? {
        if (tag in running) return null
        if (!mayUseNetwork(tag)) return Wait.WIFI
        // Downloads added earlier go first, unless they're the ones waiting for Wi-Fi.
        val ahead = waiting.takeWhile { it != tag }.any { mayUseNetwork(it) }
        if (ahead || running.size >= settings.maxParallelDownloads) return Wait.TURN
        waiting.remove(tag)
        running.add(tag)
        return null
    }

    private fun mayUseNetwork(tag: String) = !settings.wifiOnly || _onWifi.value || tag in mobileDataAllowed

    /** [tag] finished, failed or was paused: the next one may start. */
    fun release(tag: String) {
        synchronized(lock) {
            running.remove(tag)
            waiting.remove(tag)
            mobileDataAllowed.remove(tag)
        }
        recheck()
    }

    /** Lets one download waiting for Wi-Fi use mobile data. */
    fun allowMobileData(tag: String) {
        synchronized(lock) { mobileDataAllowed.add(tag) }
        recheck()
    }

    /** The rules changed (settings) or a slot freed up: waiting downloads look again. */
    fun recheck() = changes.update { it + 1 }
}
