package com.cuscus.wifiaudiostreaming

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val REPO = "marcomorosi06/WiFiAudioStreaming-Android"
    const val RELEASES_URL = "https://github.com/marcomorosi06/WiFiAudioStreaming-Android/releases/latest"

    sealed class Result {
        data class UpToDate(val current: String) : Result()
        data class Available(val current: String, val latest: String, val url: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    private val TAG_RX = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")

    fun currentVersion(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0"

    suspend fun check(context: Context, timeoutMs: Int = 5000): Result = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("https://api.github.com/repos/$REPO/releases/latest")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "WFAS-UpdateChecker")
            }
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return@withContext Result.Failed("HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val tag = TAG_RX.find(body)?.groupValues?.get(1)
                ?: return@withContext Result.Failed("no release tag found")
            val latest = normalize(tag)
            val current = normalize(currentVersion(context))
            if (compareVersions(latest, current) > 0)
                Result.Available(current, latest, RELEASES_URL)
            else
                Result.UpToDate(current)
        } catch (e: Exception) {
            Result.Failed(e.message ?: "network error")
        }
    }

    fun normalize(tag: String): String =
        tag.trim().removePrefix("v").removePrefix("V").trim()

    fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.', '-', '+').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val pb = b.split('.', '-', '+').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}
