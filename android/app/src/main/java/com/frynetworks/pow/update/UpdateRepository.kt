package com.frynetworks.pow.update

import com.frynetworks.pow.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val current: String) : UpdateState
    data class Available(val current: String, val latest: String, val url: String) : UpdateState
    data class NoReleases(val current: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Deliberately specific about failures: the shell version's habit of showing a bare
 * "Network error" is exactly what makes an update check useless to diagnose.
 */
class UpdateRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        val current = BuildConfig.VERSION_NAME
        val url = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "FryPoW-UpdateCheck")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val body = response.body?.string().orEmpty()
                        val tag = runCatching { JSONObject(body).optString("tag_name") }.getOrDefault("")
                        val html = runCatching { JSONObject(body).optString("html_url") }.getOrDefault("")
                        if (tag.isBlank()) return@withContext UpdateState.NoReleases(current)
                        if (VersionComparator.isNewer(tag, current)) {
                            UpdateState.Available(current, tag, html)
                        } else {
                            UpdateState.UpToDate(current)
                        }
                    }
                    404 -> UpdateState.NoReleases(current)
                    403, 429 -> UpdateState.Failed("GitHub rate limit reached - try again in about 15 minutes")
                    in 500..599 -> UpdateState.Failed("GitHub is having problems (HTTP ${response.code})")
                    else -> UpdateState.Failed("GitHub returned HTTP ${response.code}")
                }
            }
        } catch (e: UnknownHostException) {
            UpdateState.Failed("No internet connection")
        } catch (e: IOException) {
            UpdateState.Failed("Could not reach GitHub (${e.javaClass.simpleName})")
        }
    }
}

/** Compares release tags like "v1.2.0" or "android-v1.2.0" against BuildConfig. */
object VersionComparator {

    fun normalise(tag: String): List<Int> =
        Regex("""(\d+)""").findAll(tag).map { it.value.toInt() }.toList()

    fun isNewer(candidate: String, current: String): Boolean {
        val a = normalise(candidate)
        val b = normalise(current)
        if (a.isEmpty() || b.isEmpty()) return false
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
