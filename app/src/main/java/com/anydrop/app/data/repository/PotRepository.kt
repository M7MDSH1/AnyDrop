package com.anydrop.app.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PotRepository @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend fun fetchPotToken(): String? = withContext(Dispatchers.IO) {
        val url = settingsRepository.potServerUrl
        val apiKey = settingsRepository.apiKey
        if (url.isBlank() || apiKey.isBlank()) return@withContext null

        try {
            val conn = URL("$url/get_pot").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-API-Key", apiKey)
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val body = JSONObject().apply {
                put("content_binding", JSONObject.NULL)
                put("bypass_cache", false)
            }.toString()

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val code = conn.responseCode
            if (code != 200) {
                Log.w("PotRepository", "Server returned $code")
                return@withContext null
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val token = JSONObject(response).optString("pot", null)
            if (token.isNullOrBlank()) return@withContext null
            Log.i("PotRepository", "Fetched PO token: ${token.take(20)}...")
            token
        } catch (e: Exception) {
            Log.w("PotRepository", "Failed to fetch PO token", e)
            null
        }
    }
}
