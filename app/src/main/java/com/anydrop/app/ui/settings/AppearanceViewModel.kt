package com.anydrop.app.ui.settings

import androidx.lifecycle.ViewModel
import com.anydrop.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Light/dark mode state, read at the app roots (MainActivity, ShareActivity) so
 * the theme reactively follows the persisted setting. The flow is backed by the
 * repository's SharedPreferences listener, so the theme updates immediately no
 * matter which screen/ViewModel flips the setting.
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val darkMode: StateFlow<Boolean> = settingsRepository.darkModeFlow

    fun setDarkMode(dark: Boolean) {
        settingsRepository.darkMode = dark
    }

    fun toggleDarkMode() {
        settingsRepository.darkMode = !settingsRepository.darkMode
    }
}