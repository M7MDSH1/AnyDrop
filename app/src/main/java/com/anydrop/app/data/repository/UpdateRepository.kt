package com.anydrop.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.anydrop.app.BuildConfig
import com.anydrop.app.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** A newer release published on GitHub. [apkUrl] is the APK built for this phone, if any. */
data class AppUpdate(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkSize: Long
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val update: AppUpdate) : UpdateStatus
    data object Failed : UpdateStatus
}

sealed interface UpdateDownload {
    data object Idle : UpdateDownload
    data class Downloading(val percent: Int) : UpdateDownload
    data class Ready(val apk: File) : UpdateDownload
    data class Failed(val reason: Reason) : UpdateDownload

    enum class Reason { NETWORK, NOT_ANYDROP, DIFFERENT_KEY }
}

/**
 * In-app updates from the project's GitHub releases. Free and account-less: the public
 * "latest release" API is read (GitHub allows 60 such calls an hour per device), the
 * APK for this phone's CPU is downloaded, checked, and handed to Android's installer.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("anydrop_updates", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private val _download = MutableStateFlow<UpdateDownload>(UpdateDownload.Idle)
    val download: StateFlow<UpdateDownload> = _download.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    private val updatesDir: File get() = File(context.cacheDir, "updates")

    /**
     * Looks for a newer release. Unless [force]d, GitHub is asked at most every few hours
     * and the last answer is reused in between. Returns the update, or null when this is
     * the latest version (or the check failed; see [status]).
     */
    suspend fun check(force: Boolean): AppUpdate? {
        val now = System.currentTimeMillis()
        val cached = prefs.getString(KEY_LATEST_JSON, null)
        val fresh = now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS
        _status.value = UpdateStatus.Checking
        return try {
            val json = if (!force && fresh && cached != null) cached else withContext(Dispatchers.IO) {
                fetchLatestRelease()
            }.also {
                prefs.edit().putString(KEY_LATEST_JSON, it).putLong(KEY_LAST_CHECK, now).apply()
            }
            val update = parseRelease(json, Build.SUPPORTED_ABIS)
                ?.takeIf { isNewer(it.version, currentVersion) }
            _status.value = if (update != null) UpdateStatus.Available(update) else UpdateStatus.UpToDate
            update
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _status.value = UpdateStatus.Failed
            null
        }
    }

    fun isSkipped(version: String) = prefs.getString(KEY_SKIPPED, null) == version
    fun skip(version: String) = prefs.edit().putString(KEY_SKIPPED, version).apply()

    /** "Later": don't offer this version again on app start for a day. */
    fun snooze(version: String) = prefs.edit()
        .putString(KEY_SNOOZED, version)
        .putLong(KEY_SNOOZED_UNTIL, System.currentTimeMillis() + SNOOZE_MS)
        .apply()

    fun isSnoozed(version: String) = prefs.getString(KEY_SNOOZED, null) == version &&
        System.currentTimeMillis() < prefs.getLong(KEY_SNOOZED_UNTIL, 0L)

    /** The background check notifies once per version. */
    fun wasNotified(version: String) = prefs.getString(KEY_NOTIFIED, null) == version
    fun markNotified(version: String) = prefs.edit().putString(KEY_NOTIFIED, version).apply()

    /** Downloads the update's APK (progress in [download]) and makes sure it can be installed. */
    suspend fun downloadApk(update: AppUpdate) {
        val url = update.apkUrl ?: return
        _download.value = UpdateDownload.Downloading(0)
        try {
            // Network and APK parsing stay off the main thread.
            _download.value = withContext(Dispatchers.IO) {
                val apk = fetchApk(url, update.version)
                val refused = when {
                    !isAnyDropApk(apk) -> UpdateDownload.Reason.NOT_ANYDROP
                    !hasSameSigner(apk) -> UpdateDownload.Reason.DIFFERENT_KEY
                    else -> null
                }
                if (refused == null) {
                    UpdateDownload.Ready(apk)
                } else {
                    apk.delete() // can't be installed; don't keep 60+ MB around
                    UpdateDownload.Failed(refused)
                }
            }
        } catch (e: CancellationException) {
            _download.value = UpdateDownload.Idle
            throw e
        } catch (_: Exception) {
            _download.value = UpdateDownload.Failed(UpdateDownload.Reason.NETWORK)
        }
    }

    fun resetDownload() {
        _download.value = UpdateDownload.Idle
    }

    /** Android 8+ asks the user once to let AnyDrop install apps. */
    fun canInstallApps(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun allowInstallsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Old update APKs are useless once installed; drop them at startup. */
    fun cleanUp() {
        updatesDir.listFiles()?.forEach { file ->
            val version = file.name.removePrefix("AnyDrop-").substringBefore(".apk")
            if (!isNewer(version, currentVersion)) file.delete()
        }
    }

    private fun fetchLatestRelease(): String {
        val conn = open("https://api.github.com/repos/${Constants.GITHUB_REPO}/releases/latest")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) error("GitHub answered ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchApk(url: String, version: String): File {
        updatesDir.mkdirs()
        val target = File(updatesDir, "AnyDrop-$version.apk")
        val part = File(updatesDir, target.name + ".part")
        val conn = open(url)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) error("Download answered ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _download.value = UpdateDownload.Downloading(percent)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        target.delete()
        if (!part.renameTo(target)) error("Couldn't save the update")
        return target
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AnyDrop/${BuildConfig.VERSION_NAME} (Android)")
        }

    private fun isAnyDropApk(apk: File): Boolean =
        context.packageManager.getPackageArchiveInfo(apk.path, 0)?.packageName == context.packageName

    /** Android refuses an update signed with another key; say so up front instead. */
    @Suppress("DEPRECATION")
    private fun hasSameSigner(apk: File): Boolean {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val installed = pm.getPackageInfo(context.packageName, flags).signerSet()
            val update = pm.getPackageArchiveInfo(apk.path, flags)?.signerSet() ?: return false
            installed.isNotEmpty() && installed == update
        } else {
            val flags = PackageManager.GET_SIGNATURES
            val installed = pm.getPackageInfo(context.packageName, flags).signatures?.map { it.toCharsString() }?.toSet()
            val update = pm.getPackageArchiveInfo(apk.path, flags)?.signatures?.map { it.toCharsString() }?.toSet()
            !installed.isNullOrEmpty() && installed == update
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun PackageInfo.signerSet(): Set<String> =
        signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()

    companion object {
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_LATEST_JSON = "latest_json"
        private const val KEY_SKIPPED = "skipped_version"
        private const val KEY_NOTIFIED = "notified_version"
        private const val KEY_SNOOZED = "snoozed_version"
        private const val KEY_SNOOZED_UNTIL = "snoozed_until"
        private const val SNOOZE_MS = 24 * 60 * 60 * 1000L
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

        /**
         * Reads GitHub's release JSON. Picks the APK built for the phone's preferred CPU
         * ([abis] in order), then a universal one, then any APK.
         */
        fun parseRelease(json: String, abis: Array<String>): AppUpdate? {
            val release = JSONObject(json)
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
            val version = release.optString("tag_name").trim().removePrefix("v").removePrefix("V")
            if (version.isEmpty()) return null
            val assets = release.optJSONArray("assets")
            val apks = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
            val apk = abis.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.optString("name").contains(abi) } }
                ?: apks.firstOrNull { it.optString("name").contains("universal") }
                ?: apks.firstOrNull()
            return AppUpdate(
                version = version,
                notes = release.optString("body"),
                pageUrl = release.optString("html_url"),
                apkUrl = apk?.optString("browser_download_url")?.takeIf { it.isNotBlank() },
                apkSize = apk?.optLong("size") ?: 0L
            )
        }

        /** "1.10.0" is newer than "1.9.2"; a leading "v" and suffixes like "-beta" are ignored. */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
                .substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        /**
         * The part of the release notes worth showing: in the Arabic UI the Arabic section
         * ("## ما الجديد") when the notes have one, otherwise everything except it.
         */
        fun notesFor(notes: String, arabic: Boolean): String {
            val lines = notes.replace("\r", "").lines()
            val start = lines.indexOfFirst { it.startsWith("## ") && it.any { c -> c in '؀'..'ۿ' } }
            if (start < 0) return notes.trim()
            val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
            val picked = if (arabic) lines.subList(start + 1, end) else lines.subList(0, start) + lines.subList(end, lines.size)
            return picked.joinToString("\n").trim()
        }
    }
}
