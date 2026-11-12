package com.anydrop.app.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import com.anydrop.app.data.repository.LibraryRepository
import com.anydrop.app.data.repository.DownloadRepository
import androidx.lifecycle.viewModelScope
import com.anydrop.app.data.repository.AppLanguage
import com.anydrop.app.data.repository.DownloadGate
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
    private val languageRepository: LanguageRepository,
    private val downloadGate: DownloadGate,
    private val downloadRepository: DownloadRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    /** Bytes AnyDrop's downloads take, and bytes free (null until measured). */
    private val _storage = MutableStateFlow<Pair<Long, Long>?>(null)
    val storage: StateFlow<Pair<Long, Long>?> = _storage.asStateFlow()

    fun refreshStorage() {
        viewModelScope.launch { _storage.value = withContext(Dispatchers.IO) { libraryRepository.storage() } }
    }

    enum class EngineState { IDLE, CHECKING, UPDATED, CURRENT, FAILED }

    private val _engineVersion = MutableStateFlow(downloadRepository.engineVersion().orEmpty())
    val engineVersion: StateFlow<String> = _engineVersion.asStateFlow()

    private val _engineState = MutableStateFlow(EngineState.IDLE)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    /** Looks for a newer download engine (yt-dlp) now, instead of at the daily check. */
    fun updateEngine() {
        if (_engineState.value == EngineState.CHECKING) return
        _engineState.value = EngineState.CHECKING
        viewModelScope.launch {
            _engineState.value = try {
                if (downloadRepository.updateEngine()) EngineState.UPDATED else EngineState.CURRENT
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EngineState.FAILED
            }
            _engineVersion.value = downloadRepository.engineVersion().orEmpty()
        }
    }

    private val _wifiOnly = MutableStateFlow(settingsRepository.wifiOnly)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _maxParallel = MutableStateFlow(settingsRepository.maxParallelDownloads)
    val maxParallel: StateFlow<Int> = _maxParallel.asStateFlow()

    /** Queued downloads follow the new rule at once (they wait, or start). */
    fun toggleWifiOnly() {
        val next = !settingsRepository.wifiOnly
        settingsRepository.wifiOnly = next
        _wifiOnly.value = next
        downloadGate.recheck()
    }

    fun setMaxParallel(count: Int) {
        settingsRepository.maxParallelDownloads = count
        _maxParallel.value = settingsRepository.maxParallelDownloads
        downloadGate.recheck()
    }

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

    private val _audioLanguages = MutableStateFlow(settingsRepository.audioLanguages)
    val audioLanguages: StateFlow<Boolean> = _audioLanguages.asStateFlow()

    private val _subtitles = MutableStateFlow(settingsRepository.subtitles)
    val subtitles: StateFlow<Boolean> = _subtitles.asStateFlow()

    private val _blockAds = MutableStateFlow(settingsRepository.blockAds)
    val blockAds: StateFlow<Boolean> = _blockAds.asStateFlow()

    fun toggleBlockAds() {
        settingsRepository.blockAds = !settingsRepository.blockAds
        _blockAds.value = settingsRepository.blockAds
    }

    private val _quickShare = MutableStateFlow(settingsRepository.quickShare)
    val quickShare: StateFlow<Boolean> = _quickShare.asStateFlow()

    fun toggleQuickShare() {
        settingsRepository.quickShare = !settingsRepository.quickShare
        _quickShare.value = settingsRepository.quickShare
    }

    fun toggleAudioLanguages() {
        settingsRepository.audioLanguages = !settingsRepository.audioLanguages
        _audioLanguages.value = settingsRepository.audioLanguages
    }

    fun toggleSubtitles() {
        settingsRepository.subtitles = !settingsRepository.subtitles
        _subtitles.value = settingsRepository.subtitles
    }

    fun toggleAutoDetectLinks() {
        val next = !settingsRepository.autoDetectLinks
        settingsRepository.autoDetectLinks = next
        _autoDetectLinks.value = next
    }

    fun toggleDarkMode() = settingsRepository.toggleDarkMode()

    /** Persists the choice; the caller applies it to the running activity. */
    suspend fun setLanguage(language: AppLanguage) {
        languageRepository.setLanguage(language)
    }
}
