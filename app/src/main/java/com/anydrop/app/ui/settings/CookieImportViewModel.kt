package com.anydrop.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydrop.app.R
import com.anydrop.app.data.repository.CookieRepository
import com.anydrop.app.data.repository.LoginPlatform
import com.anydrop.app.data.repository.LoginPlatformRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Status line shown under the platform list, with its error styling decided here. */
data class CookieStatusMessage(val text: String, val isError: Boolean)

@HiltViewModel
class CookieImportViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
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

    private val _statusMessage = MutableStateFlow<CookieStatusMessage?>(null)
    val statusMessage: StateFlow<CookieStatusMessage?> = _statusMessage.asStateFlow()

    /** Sign out of [platform] but keep it (and its login URL) in the list for later. */
    fun disconnect(platform: LoginPlatform) {
        viewModelScope.launch {
            cookieRepository.disconnect(platform.domain)
            refreshLoginStatus()
            _statusMessage.value = CookieStatusMessage(
                appContext.getString(R.string.session_disconnected, platform.name),
                isError = false
            )
        }
    }

    /** Sign out of [platform] AND remove it from the list (added platforms only). */
    fun deletePlatform(platform: LoginPlatform) {
        if (platform.builtIn) return
        viewModelScope.launch {
            // Sign out first: removing the row alone used to leave the account's cookies saved.
            cookieRepository.disconnect(platform.domain)
            platformRepository.removePlatform(platform.name)
            refreshLoginStatus()
            _statusMessage.value = CookieStatusMessage(
                appContext.getString(R.string.session_deleted, platform.name),
                isError = false
            )
        }
    }

    private fun errorText(fallbackRes: Int, exception: Throwable?): String =
        appContext.getString(
            R.string.error_with_message,
            exception?.message ?: appContext.getString(fallbackRes)
        )

    fun importCookies(uri: Uri) {
        viewModelScope.launch {
            val result = cookieRepository.importFromUri(uri)
            _statusMessage.value = if (result.isSuccess) {
                CookieStatusMessage(appContext.getString(R.string.cookie_success), isError = false)
            } else {
                CookieStatusMessage(
                    errorText(R.string.cookie_import_failed, result.exceptionOrNull()),
                    isError = true
                )
            }
        }
    }

    fun clearCookies() {
        cookieRepository.clearCookies()
        _statusMessage.value =
            CookieStatusMessage(appContext.getString(R.string.cookies_cleared), isError = false)
    }

    /** Save cookies captured from the in-app WebView login. */
    fun saveWebViewCookies(domain: String, rawCookies: String, requiredCookie: String?) {
        viewModelScope.launch {
            val result = cookieRepository.saveCookiesFromWebView(domain, rawCookies, requiredCookie)
            _statusMessage.value = if (result.isSuccess) {
                CookieStatusMessage(
                    appContext.getString(R.string.web_login_saved, domain),
                    isError = false
                )
            } else {
                CookieStatusMessage(
                    errorText(R.string.web_login_capture_failed, result.exceptionOrNull()),
                    isError = true
                )
            }
            refreshLoginStatus()
        }
    }
}
