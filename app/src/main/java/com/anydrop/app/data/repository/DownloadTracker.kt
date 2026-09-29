package com.anydrop.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.downloadTrackerDataStore by preferencesDataStore(name = "download_tracker")

/**
 * Tracks downloads that are currently running (or died with the process) so that
 * on next app start the app can tell a user a download never finished.
 *
 * Entries are encoded "outputPath\u0000title"; the path is the stable per-download
 * tag shared with DownloadService notifications.
 */
@Singleton
class DownloadTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val key = stringSetPreferencesKey("active_downloads")

    suspend fun markActive(outputPath: String, title: String) {
        context.downloadTrackerDataStore.edit { prefs ->
            val entries = prefs[key].orEmpty().toMutableSet()
            entries.add("$outputPath\u0000$title")
            prefs[key] = entries
        }
    }

    suspend fun markDone(outputPath: String) {
        context.downloadTrackerDataStore.edit { prefs ->
            val entries = prefs[key].orEmpty().toMutableSet()
            entries.removeAll { it.startsWith("$outputPath\u0000") }
            prefs[key] = entries
        }
    }

    /** Leftover downloads (interrupted by a process kill), path -> title. */
    suspend fun staleDownloads(): Map<String, String> {
        val entries = context.downloadTrackerDataStore.data.first()[key].orEmpty()
        return entries.mapNotNull { entry ->
            val sep = entry.indexOf('\u0000')
            if (sep > 0) entry.substring(0, sep) to entry.substring(sep + 1) else null
        }.toMap()
    }

    suspend fun clearAll() {
        context.downloadTrackerDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }
}
