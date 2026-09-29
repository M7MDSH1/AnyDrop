package com.anydrop.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydrop.app.data.repository.AppLanguage
import com.anydrop.app.data.repository.LanguageRepository
import com.anydrop.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val languageRepository: LanguageRepository
) : ViewModel() {

    private val _maxQualityHeight = MutableStateFlow(settingsRepository.maxQualityHeight)
    val maxQualityHeight: StateFlow<Int> = _maxQualityHeight.asStateFlow()

    private val _autoDetectLinks = MutableStateFlow(settingsRepository.autoDetectLinks)
    val autoDetectLinks: StateFlow<Boolean> = _autoDetectLinks.asStateFlow()

    /** Backed by the repository's prefs listener, so the theme updates instantly. */
    val darkMode: StateFlow<Boolean> = settingsRepository.darkModeFlow

    val language: StateFlow<AppLanguage> = languageRepository.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLanguage.SYSTEM)

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

    /** Persists the choice; the caller applies it to the running activity. */
    suspend fun setLanguage(language: AppLanguage) {
        languageRepository.setLanguage(language)
    }
}
