package com.anydrop.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The deck's downloads list on disk, so it's still there after Android closes the app (RETRY and
 * OPEN keep working). Live progress isn't kept, and neither are session cookies.
 */
@Singleton
class DownloadListStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("anydrop_downloads", Context.MODE_PRIVATE)

    fun load(): List<DownloadTask> = decode(prefs.getString(KEY_TASKS, null))

    fun save(tasks: List<DownloadTask>) {
        prefs.edit().putString(KEY_TASKS, encode(tasks.take(MAX_KEPT))).apply()
    }

    /**
     * Downloads in the list that didn't finish: their partial files are kept so RETRY carries on
     * where they stopped (yt-dlp resumes a .part file).
     */
    fun unfinishedOutputs(): List<File> =
        load().filter { it.status != TaskStatus.DONE }.map { File(it.tag) }

    companion object {
        private const val KEY_TASKS = "tasks"
        private const val MAX_KEPT = 100

        internal fun encode(tasks: List<DownloadTask>): String = JSONArray().apply {
            tasks.forEach { t ->
                put(JSONObject().apply {
                    put("tag", t.tag)
                    put("title", t.title)
                    put("quality", t.quality)
                    put("kind", t.kind.name)
                    put("status", t.status.name)
                    put("percent", t.percent)
                    put("sizeText", t.sizeText)
                    put("error", t.error)
                    put("errorDetail", t.errorDetail)
                    put("filePath", t.filePath)
                    put("startedAt", t.startedAt)
                    put("url", t.request.url)
                    put("requestTitle", t.request.title)
                    put("creator", t.request.creator)
                    put("webpageUrl", t.request.webpageUrl)
                    put("audioLanguage", t.request.audioLanguage)
                    put("subtitleLanguage", t.request.subtitleLanguage)
                    put("format", FormatJson.toJson(t.request.format))
                })
            }
        }.toString()

        internal fun decode(raw: String?): List<DownloadTask> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    DownloadTask(
                        tag = o.getString("tag"),
                        title = o.optString("title"),
                        quality = o.optString("quality"),
                        kind = runCatching { TaskKind.valueOf(o.optString("kind")) }.getOrDefault(TaskKind.VIDEO),
                        request = DownloadRequest(
                            url = o.optString("url"),
                            format = FormatJson.fromJson(o.getJSONObject("format")),
                            title = o.optString("requestTitle"),
                            creator = o.optString("creator"),
                            webpageUrl = o.optString("webpageUrl"),
                            audioLanguage = o.optString("audioLanguage"),
                            subtitleLanguage = o.optString("subtitleLanguage")
                        ),
                        status = runCatching { TaskStatus.valueOf(o.optString("status")) }.getOrDefault(TaskStatus.FAILED),
                        percent = o.optInt("percent"),
                        sizeText = o.optString("sizeText"),
                        error = o.optString("error"),
                        errorDetail = o.optString("errorDetail"),
                        filePath = o.optString("filePath"),
                        startedAt = o.optLong("startedAt")
                    )
                }
            }.getOrDefault(emptyList())
        }
    }
}
