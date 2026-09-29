package com.anydrop.app.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.anydrop.app.data.repository.AppLanguage
import java.util.Locale

/**
 * Locale application without AppCompat:
 *
 * - API 33+: the system's per-app locale manager persists the choice and
 *   recreates activities automatically — no manual work needed.
 * - API 26-32: each Context (Application, Activities, Services) wraps its base
 *   context with the stored locale via [wrapAppLocale]; on a language change the
 *   activity is recreated so the new configuration takes effect immediately.
 */
object LocaleHelper {

    /** Wraps a base context with the app-selected locale (API 26-32 only). */
    fun wrapAppLocale(base: Context, language: AppLanguage): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        if (language == AppLanguage.SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        val locale = Locale(language.tag)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Applies a newly selected language immediately. */
    fun applyAppLocaleNow(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = activity.getSystemService(LocaleManager::class.java)
            localeManager.applicationLocales = LocaleList.forLanguageTags(language.tag)
        } else {
            activity.recreate()
        }
    }
}
