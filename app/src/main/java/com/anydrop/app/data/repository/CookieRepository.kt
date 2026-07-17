package com.anydrop.app.data.repository

import android.content.Context
import android.net.Uri
import com.anydrop.app.data.model.CookieEntry
import com.anydrop.app.data.model.CookieSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CookieRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _importedSessions = MutableStateFlow<List<CookieSession>>(emptyList())
    val importedSessions: Flow<List<CookieSession>> = _importedSessions.asStateFlow()

    private val _isImported = MutableStateFlow(false)
    val isImported: Flow<Boolean> = _isImported.asStateFlow()

    fun getCookieFile(): File {
        return File(context.filesDir, "cookies.txt")
    }

    suspend fun importFromUri(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        try {
            val outputFile = getCookieFile()
            context.contentResolver.openInputStream(uri)?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Cannot open file"))

            val sessions = parseCookies(outputFile)
            _importedSessions.value = sessions
            _isImported.value = sessions.isNotEmpty()

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearCookies() {
        getCookieFile().delete()
        _importedSessions.value = emptyList()
        _isImported.value = false
    }

    /** True if the cookie file has at least one entry for [domain] (for ✓ marks). */
    fun hasCookiesForDomain(domain: String): Boolean {
        val file = getCookieFile()
        if (!file.exists()) return false
        val d = domain.removePrefix(".").lowercase()
        return try {
            file.readLines().any { line ->
                val t = line.trim()
                if (t.isBlank() || t.startsWith("#")) return@any false
                val entryDomain = t.split("\t").firstOrNull()?.removePrefix(".")?.lowercase() ?: ""
                entryDomain.endsWith(d)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Save cookies captured from an in-app WebView login (no PC needed).
     * [rawCookies] is the value of CookieManager.getCookie(url), i.e. a
     * "name=value; name2=value2" string. We convert it to Netscape cookies.txt
     * format (what yt-dlp reads) and MERGE it with any existing cookies, replacing
     * entries for the same domain so re-logins refresh cleanly.
     */
    suspend fun saveCookiesFromWebView(
        domain: String,
        rawCookies: String,
        requiredCookie: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Verify the user actually logged in: the session cookie must be present.
            // (A logged-OUT Instagram page still sets csrftoken/mid, so presence of
            // *some* cookies is not enough.)
            if (requiredCookie != null) {
                val hasSession = rawCookies.split(";").any { pair ->
                    val name = pair.substringBefore("=").trim()
                    name.equals(requiredCookie, ignoreCase = true) &&
                        pair.substringAfter("=", "").trim().isNotBlank()
                }
                if (!hasSession) {
                    return@withContext Result.failure(
                        Exception("Not logged in yet — finish signing in, then tap DONE.")
                    )
                }
            }

            val normalizedDomain = if (domain.startsWith(".")) domain else ".$domain"
            // Far-future expiry so yt-dlp treats them as session-persistent.
            val expiry = (System.currentTimeMillis() / 1000) + 60L * 60 * 24 * 365

            val newLines = rawCookies.split(";")
                .mapNotNull { pair ->
                    val idx = pair.indexOf('=')
                    if (idx <= 0) return@mapNotNull null
                    val name = pair.substring(0, idx).trim()
                    val value = pair.substring(idx + 1).trim()
                    if (name.isBlank()) return@mapNotNull null
                    // domain \t includeSubdomains \t path \t secure \t expiry \t name \t value
                    "$normalizedDomain\tTRUE\t/\tTRUE\t$expiry\t$name\t$value"
                }

            if (newLines.isEmpty()) {
                return@withContext Result.failure(Exception("No cookies found — are you logged in?"))
            }

            val file = getCookieFile()

            // Keep existing lines for OTHER domains; drop old ones for this domain.
            val existing = if (file.exists()) {
                file.readLines().filter { line ->
                    val t = line.trim()
                    if (t.isBlank() || t.startsWith("#")) true
                    else {
                        val d = t.split("\t").firstOrNull() ?: ""
                        !d.endsWith(domain, ignoreCase = true) && !d.endsWith(normalizedDomain, ignoreCase = true)
                    }
                }
            } else {
                listOf("# Netscape HTTP Cookie File", "# Generated by AnyDrop in-app login")
            }

            file.printWriter().use { pw ->
                existing.forEach { pw.println(it) }
                newLines.forEach { pw.println(it) }
            }

            val sessions = parseCookies(file)
            _importedSessions.value = sessions
            _isImported.value = sessions.isNotEmpty()
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseCookies(file: File): List<CookieSession> {
        val entries = mutableListOf<CookieEntry>()

        try {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach

                    val parts = trimmed.split("\t")
                    if (parts.size >= 7) {
                        entries.add(
                            CookieEntry(
                                domain = parts[0],
                                includeSubdomains = parts[1] == "TRUE",
                                path = parts[2],
                                secure = parts[3] == "TRUE",
                                expiry = parts[4].toLongOrNull() ?: 0L,
                                name = parts[5],
                                value = parts[6]
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        return entries.groupBy { it.domain }
            .map { (domain, cookieList) ->
                CookieSession(domain = domain, cookies = cookieList)
            }
    }
}
