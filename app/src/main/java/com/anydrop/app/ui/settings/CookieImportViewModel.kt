package com.anydrop.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydrop.app.data.repository.CookieRepository
import com.anydrop.app.data.repository.LoginPlatform
import com.anydrop.app.data.repository.LoginPlatformRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CookieImportViewModel @Inject constructor(
    private val cookieRepository: CookieRepository,
    private val platformRepository: LoginPlatformRepository
) : ViewModel() {

    val isImported = cookieRepository.isImported
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val sessions = cookieRepository.importedSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val platforms = platformRepository.platforms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Set of platform domains that currently have saved cookies (drives the ✓).
    private val _loggedInDomains = MutableStateFlow<Set<String>>(emptySet())
    val loggedInDomains: StateFlow<Set<String>> = _loggedInDomains.asStateFlow()

    init {
        refreshLoginStatus()
    }

    fun refreshLoginStatus() {
        val domains = platformRepository.platforms.value
            .filter { cookieRepository.hasCookiesForDomain(it.domain) }
            .map { it.domain }
            .toSet()
        _loggedInDomains.value = domains
    }

    fun addPlatform(name: String, loginUrl: String) {
        platformRepository.addPlatform(name, loginUrl)
        refreshLoginStatus()
    }

    fun removePlatform(name: String) {
        platformRepository.removePlatform(name)
        refreshLoginStatus()
    }

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun importCookies(uri: Uri) {
        viewModelScope.launch {
            val result = cookieRepository.importFromUri(uri)
            _statusMessage.value = if (result.isSuccess) {
                "Cookies imported successfully"
            } else {
                "Error: ${result.exceptionOrNull()?.message ?: "Import failed"}"
            }
        }
    }

    fun clearCookies() {
        cookieRepository.clearCookies()
        _statusMessage.value = "Cookies cleared"
    }

    /** Save cookies captured from the in-app WebView login. */
    fun saveWebViewCookies(domain: String, rawCookies: String, requiredCookie: String?) {
        viewModelScope.launch {
            val result = cookieRepository.saveCookiesFromWebView(domain, rawCookies, requiredCookie)
            _statusMessage.value = if (result.isSuccess) {
                "Logged in — cookies saved for $domain"
            } else {
                "Error: ${result.exceptionOrNull()?.message ?: "Login capture failed"}"
            }
            refreshLoginStatus()
        }
    }
}
