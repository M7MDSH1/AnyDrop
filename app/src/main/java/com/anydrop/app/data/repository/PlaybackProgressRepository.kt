package com.anydrop.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.progressDataStore by preferencesDataStore(name = "playback_progress")

/**
 * Persists per-video watch progress keyed by stable video id.
 *
 * DataStore Preferences rewrites the whole preferences blob on each write, which
 * is fine while only a handful of positions are stored at a time; revisit with
 * Room if the watch-history scope grows to many simultaneous keys.
 */
@Singleton
class PlaybackProgressRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun getPosition(mediaId: String): Long? {
        val key = longPreferencesKey(mediaId)
        return context.progressDataStore.data.first()[key]
    }

    suspend fun savePosition(mediaId: String, positionMs: Long) {
        val key = longPreferencesKey(mediaId)
        context.progressDataStore.edit { prefs ->
            prefs[key] = positionMs
        }
    }

    suspend fun clearPosition(mediaId: String) {
        val key = longPreferencesKey(mediaId)
        context.progressDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }
}