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

    private val _potServerUrl = MutableStateFlow(settingsRepository.potServerUrl)
    val potServerUrl: StateFlow<String> = _potServerUrl.asStateFlow()

    private val _apiKey = MutableStateFlow(settingsRepository.apiKey)
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun updatePotServerUrl(url: String) {
        settingsRepository.potServerUrl = url
        _potServerUrl.value = url
    }

    fun updateApiKey(key: String) {
        settingsRepository.apiKey = key
        _apiKey.value = key
    }
}
