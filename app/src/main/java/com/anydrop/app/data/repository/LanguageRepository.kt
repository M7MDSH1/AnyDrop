package com.anydrop.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val LANGUAGE_KEY = stringPreferencesKey("app_language")

/**
 * Single DataStore instance for the language preference, shared by the async
 * repository flow and the synchronous attachBaseContext read. Built manually
 * (not via the `preferencesDataStore` delegate) because Application
 * attachBaseContext runs before `applicationContext` exists — the standard
 * delegates would throw a NullPointerException there. The file path is built
 * from the raw base context's filesDir, which is valid at that point and
 * resolves to the same directory as the application context's.
 */
private object LanguageDataStoreHolder {
    @Volatile
    private var instance: DataStore<Preferences>? = null

    fun get(context: Context): DataStore<Preferences> =
        instance ?: synchronized(this) {
            instance ?: PreferenceDataStoreFactory.create(
                produceFile = {
                    File(context.filesDir, "datastore/language_prefs.preferences_pb")
                }
            ).also { instance = it }
        }
}

/**
 * Language choices offered in Settings. SYSTEM follows the device locale; the
 * explicit options force the app UI regardless of the device language.
 */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    ARABIC("ar");

    companion object {
        fun fromTag(tag: String): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * Persisted UI-language preference (DataStore). The tag is applied to the app
 * via [com.anydrop.app.util.LocaleHelper] — on API 33+ through the system's
 * per-app locale manager, on older Androids by wrapping each Context at
 * attachBaseContext.
 */
@Singleton
class LanguageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore: DataStore<Preferences> get() = LanguageDataStoreHolder.get(context)

    val language: Flow<AppLanguage> =
        dataStore.data.map { prefs ->
            AppLanguage.fromTag(prefs[LANGUAGE_KEY] ?: "")
        }

    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { prefs -> prefs[LANGUAGE_KEY] = language.tag }
    }
}

/**
 * Synchronous read for attachBaseContext — the locale must be known before the
 * Context's resources are first used, and the DataStore file is tiny.
 */
fun readAppLanguageSync(context: Context): AppLanguage =
    runBlocking {
        LanguageDataStoreHolder.get(context).data.first()[LANGUAGE_KEY]
            ?.let(AppLanguage::fromTag)
            ?: AppLanguage.SYSTEM
    }
