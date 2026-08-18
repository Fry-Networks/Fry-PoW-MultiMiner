package com.frynetworks.pow.webui

import com.frynetworks.pow.data.ConfigError
import com.frynetworks.pow.data.MiningConfig
import com.frynetworks.pow.mining.MiningState
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure JSON layer for the LAN dashboard. No Android imports, so the route table and
 * every payload shape are testable on the JVM.
 */
object WebUIApi {

    private val getRoutes = setOf(
        "/", "/config", "/style.css", "/app.js",
        "/api/status", "/api/config", "/api/device",
    )
    private val postRoutes = setOf("/api/config", "/api/mining/start", "/api/mining/stop")

    /**
     * Complete route surface; anything absent is a 404. Deliberately contains no
     * route under /api/server - the web UI cannot control its own availability.
     */
    fun isKnownRoute(method: String, path: String): Boolean = when (method) {
        "GET" -> path in getRoutes
        "POST" -> path in postRoutes
        "OPTIONS" -> path in getRoutes || path in postRoutes
        else -> false
    }

    fun statusJson(state: MiningState, config: MiningConfig): String {
        val stats = (state as? MiningState.Mining)?.stats
        val word = when (state) {
            is MiningState.Idle -> "idle"
            is MiningState.Starting -> "starting"
            is MiningState.Mining -> "mining"
            is MiningState.Stopping -> "stopping"
            is MiningState.Error -> "error"
            is MiningState.Unsupported -> "unsupported"
        }
        val detail = when (state) {
            is MiningState.Error -> state.detail
            is MiningState.Unsupported -> state.reason
            else -> null
        }
        return JSONObject()
            .put("state", word)
            .put("mining", state is MiningState.Mining)
            .put("hashrate", stats?.hashrate ?: JSONObject.NULL)
            .put("hashrateDisplay", stats?.hashrateDisplay ?: "--")
            .put("accepted", stats?.accepted ?: 0L)
            .put("rejected", stats?.rejected ?: 0L)
            .put("uptimeSeconds", stats?.uptimeSeconds ?: 0L)
            .put("efficiencyPercent", stats?.efficiencyPercent ?: 100)
            .put("algo", stats?.algo ?: JSONObject.NULL)
            .put("pool", stats?.pool ?: config.pool.ifBlank { null } ?: JSONObject.NULL)
            .put("difficulty", stats?.difficulty ?: JSONObject.NULL)
            .put("worker", config.worker)
            .put("coinId", stats?.coinId?.ifBlank { null } ?: config.coinId)
            .put("detail", detail ?: JSONObject.NULL)
            .toString()
    }

    /** The web dashboard preference is deliberately absent - not readable over HTTP. */
    fun configJson(config: MiningConfig): String = JSONObject()
        .put("coinId", config.coinId)
        .put("wallet", config.wallet)
        .put("dogeWallet", config.dogeWallet)
        .put("ltcWallet", config.ltcWallet)
        .put("worker", config.worker)
        .put("threads", config.threads)
        .put("pool", config.pool)
        .put("password", config.password)
        .put("startOnBoot", config.startOnBoot)
        .toString()

    fun deviceJson(model: String, androidVersion: String, abi: String, ip: String?): String =
        JSONObject()
            .put("model", model)
            .put("androidVersion", androidVersion)
            .put("abi", abi)
            .put("ip", ip ?: JSONObject.NULL)
            .toString()

    fun okJson(): String = """{"ok":true}"""

    fun errorJson(error: String, detail: String? = null): String = JSONObject()
        .put("ok", false)
        .put("error", error)
        .put("detail", detail ?: JSONObject.NULL)
        .toString()

    fun validationErrorJson(errors: List<ConfigError>): String {
        val arr = JSONArray()
        errors.forEach { arr.put(JSONObject().put("field", it.field.name).put("message", it.message)) }
        return JSONObject()
            .put("ok", false)
            .put("error", "invalid_config")
            .put("detail", errors.first().message)
            .put("errors", arr)
            .toString()
    }

    /**
     * Partial patch: only keys present in the body override the current values, and
     * unknown keys are ignored, so a body naming web_server_enabled cannot reach the
     * preference. Throws JSONException on malformed JSON for the transport to map.
     */
    fun parseConfigPatch(body: String, current: MiningConfig): MiningConfig {
        val o = JSONObject(body)
        return current.copy(
            coinId = o.optString("coinId", current.coinId),
            wallet = o.optString("wallet", current.wallet),
            dogeWallet = o.optString("dogeWallet", current.dogeWallet),
            ltcWallet = o.optString("ltcWallet", current.ltcWallet),
            worker = o.optString("worker", current.worker),
            threads = o.optInt("threads", current.threads),
            pool = o.optString("pool", current.pool),
            password = o.optString("password", current.password),
            startOnBoot = o.optBoolean("startOnBoot", current.startOnBoot),
        )
    }
}
