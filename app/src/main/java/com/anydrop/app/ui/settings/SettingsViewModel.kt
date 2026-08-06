package com.anydrop.app.ui.settings

import androidx.lifecycle.ViewModel
import com.anydrop.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _maxQualityHeight = MutableStateFlow(settingsRepository.maxQualityHeight)
    val maxQualityHeight: StateFlow<Int> = _maxQualityHeight.asStateFlow()

    private val _autoDetectLinks = MutableStateFlow(settingsRepository.autoDetectLinks)
    val autoDetectLinks: StateFlow<Boolean> = _autoDetectLinks.asStateFlow()

    /** Backed by the repository's prefs listener, so the theme updates instantly. */
    val darkMode: StateFlow<Boolean> = settingsRepository.darkModeFlow

    fun updateMaxQualityHeight(height: Int) {
        settingsRepository.maxQualityHeight = height
        _maxQualityHeight.value = height
    }

    fun toggleAutoDetectLinks() {
        val next = !settingsRepository.autoDetectLinks
        settingsRepository.autoDetectLinks = next
        _autoDetectLinks.value = next
    }

    fun toggleDarkMode() {
        settingsRepository.darkMode = !settingsRepository.darkMode
    }
}
