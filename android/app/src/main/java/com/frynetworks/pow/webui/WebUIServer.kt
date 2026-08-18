package com.frynetworks.pow.webui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.frynetworks.pow.data.ConfigRepository
import com.frynetworks.pow.data.ConfigValidator
import com.frynetworks.pow.data.MiningConfig
import com.frynetworks.pow.mining.MinerBinaries
import com.frynetworks.pow.mining.MiningController
import com.frynetworks.pow.mining.MiningService
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONException

/**
 * LAN dashboard on port 8080, hosted by AppContainer for process lifetime -
 * MiningService self-stops on Idle so it cannot own a dashboard that must outlive a
 * session. serve() runs on NanoHTTPD's per-request worker threads, never the main
 * thread, so the short runBlocking bridges into DataStore are safe.
 */
class WebUIServer(
    private val appContext: Context,
    private val configRepository: ConfigRepository,
    private val miningController: MiningController,
    private val maxThreads: Int,
) : NanoHTTPD(PORT) {

    override fun serve(session: IHTTPSession): Response {
        val method = session.method?.name ?: "GET"
        val path = session.uri ?: "/"
        val response = when {
            !WebUIApi.isKnownRoute(method, path) -> notFound()
            method == "OPTIONS" -> newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "")
            method == "GET" -> get(path)
            else -> post(session, path)
        }
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    private fun get(path: String): Response = when (path) {
        "/" -> asset("index.html", "text/html")
        "/config" -> asset("config.html", "text/html")
        "/style.css" -> asset("style.css", "text/css")
        "/app.js" -> asset("app.js", "application/javascript")
        "/api/status" -> json(WebUIApi.statusJson(miningController.state.value, currentConfig()))
        "/api/config" -> json(WebUIApi.configJson(currentConfig()))
        "/api/device" -> json(
            WebUIApi.deviceJson(
                model = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE,
                abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
                ip = LanIpProvider.lanIp(),
            ),
        )
        else -> notFound()
    }

    private fun post(session: IHTTPSession, path: String): Response {
        val length = session.headers["content-length"]?.toLongOrNull() ?: 0L
        if (length > MAX_BODY_BYTES) return json(WebUIApi.errorJson("bad_request", "Body too large"))
        val body = try {
            readBody(session)
        } catch (e: Exception) {
            return json(WebUIApi.errorJson("bad_request"))
        }
        return when (path) {
            "/api/config" -> saveConfig(body)
            "/api/mining/start" -> startMining()
            "/api/mining/stop" -> {
                // Direct call, never a service intent: delivering ACTION_STOP needs a
                // service start, which Android 12+ refuses from a background thread.
                miningController.stop()
                json(WebUIApi.okJson())
            }
            else -> notFound()
        }
    }

    private fun saveConfig(body: String): Response {
        val patched = try {
            WebUIApi.parseConfigPatch(body, currentConfig())
        } catch (e: JSONException) {
            return json(WebUIApi.errorJson("bad_json"))
        }
        val errors = ConfigValidator.validate(patched, maxThreads)
        if (errors.isNotEmpty()) return json(WebUIApi.validationErrorJson(errors))
        runBlocking { configRepository.save(patched) }
        return json("""{"ok":true,"config":${WebUIApi.configJson(patched)}}""")
    }

    private fun startMining(): Response {
        if (miningController.isBusy) return json(WebUIApi.errorJson("already_running"))
        val errors = ConfigValidator.validate(currentConfig(), maxThreads)
        if (errors.isNotEmpty()) {
            return json(WebUIApi.errorJson("invalid_config", errors.first().message))
        }
        val intent = Intent(appContext, MiningService::class.java).setAction(MiningService.ACTION_START)
        return try {
            ContextCompat.startForegroundService(appContext, intent)
            json(WebUIApi.okJson())
        } catch (e: Exception) {
            // Android 12+ refuses FGS starts while the app is backgrounded.
            miningController.reportForegroundBlocked()
            json(WebUIApi.errorJson("fgs_blocked", "Open FryPoW on the device and press Start."))
        }
    }

    /** DataStore is memory-cached after first load; each request has its own thread. */
    private fun currentConfig(): MiningConfig = runBlocking { configRepository.config.first() }

    private fun readBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"].orEmpty()
    }

    private fun asset(name: String, mime: String): Response = try {
        newChunkedResponse(Response.Status.OK, mime, appContext.assets.open("webui/$name"))
    } catch (e: IOException) {
        notFound()
    }

    private fun json(payload: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", payload)

    private fun notFound(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", """{"ok":false,"error":"not_found"}""")

    /** Idempotent; false when the port could not be bound. */
    fun startSafely(): Boolean {
        if (isAlive) return true
        return try {
            start()
            Log.i(MinerBinaries.TAG, "EVT=webui_started port=$PORT")
            true
        } catch (e: IOException) {
            Log.w(MinerBinaries.TAG, "EVT=webui_start_failed err=${e.message}")
            false
        }
    }

    fun stopSafely() {
        if (!isAlive) return
        stop()
        Log.i(MinerBinaries.TAG, "EVT=webui_stopped")
    }

    companion object {
        const val PORT = 8080
        private const val MAX_BODY_BYTES = 64 * 1024L
    }
}
