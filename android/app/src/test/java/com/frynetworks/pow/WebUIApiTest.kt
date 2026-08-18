package com.frynetworks.pow

import com.frynetworks.pow.data.ConfigError
import com.frynetworks.pow.data.MiningConfig
import com.frynetworks.pow.mining.ErrorKind
import com.frynetworks.pow.mining.MiningState
import com.frynetworks.pow.mining.MiningStats
import com.frynetworks.pow.webui.WebUIApi
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUIApiTest {

    private val populated = MiningConfig(
        coinId = "xmr",
        wallet = "WALLET_A",
        dogeWallet = "DOGE_A",
        ltcWallet = "LTC_A",
        worker = "rig1",
        threads = 3,
        pool = "pool.example.com:3333",
        password = "x",
        startOnBoot = false,
    )

    private val stats = MiningStats(
        hashrate = 123.45,
        hashrateUnit = "H/s",
        accepted = 7,
        rejected = 1,
        algo = "rx/0",
        pool = "live.pool:3333",
        difficulty = "120K",
        uptimeSeconds = 300,
        coinId = "xmr",
    )

    @Test
    fun `status json for idle reports idle state and the configured worker`() {
        val o = JSONObject(WebUIApi.statusJson(MiningState.Idle, populated))
        assertEquals("idle", o.getString("state"))
        assertFalse(o.getBoolean("mining"))
        assertEquals("rig1", o.getString("worker"))
        assertTrue(o.isNull("hashrate"))
        assertEquals(0L, o.getLong("accepted"))
    }

    @Test
    fun `status json for mining carries hashrate accepted uptime algo and pool`() {
        val o = JSONObject(WebUIApi.statusJson(MiningState.Mining(stats), populated))
        assertEquals("mining", o.getString("state"))
        assertTrue(o.getBoolean("mining"))
        assertEquals(123.45, o.getDouble("hashrate"), 0.0001)
        assertEquals(7L, o.getLong("accepted"))
        assertEquals(300L, o.getLong("uptimeSeconds"))
        assertEquals("rx/0", o.getString("algo"))
        assertEquals("live.pool:3333", o.getString("pool"))
        assertEquals(87, o.getInt("efficiencyPercent"))
    }

    @Test
    fun `status json for error and unsupported carry the detail text`() {
        val err = JSONObject(WebUIApi.statusJson(MiningState.Error(ErrorKind.FGS_BLOCKED, "blocked"), populated))
        assertEquals("error", err.getString("state"))
        assertEquals("blocked", err.getString("detail"))
        val uns = JSONObject(WebUIApi.statusJson(MiningState.Unsupported("no binary"), populated))
        assertEquals("unsupported", uns.getString("state"))
        assertEquals("no binary", uns.getString("detail"))
    }

    @Test
    fun `every mining state variant maps to a distinct state word`() {
        val states = listOf(
            MiningState.Idle,
            MiningState.Starting,
            MiningState.Mining(stats),
            MiningState.Stopping,
            MiningState.Error(ErrorKind.CRASHED, "x"),
            MiningState.Unsupported("y"),
        )
        val words = states.map { JSONObject(WebUIApi.statusJson(it, populated)).getString("state") }.toSet()
        assertEquals(6, words.size)
    }

    @Test
    fun `config json round-trips every field through the patch parser`() {
        val parsed = WebUIApi.parseConfigPatch(WebUIApi.configJson(populated), MiningConfig())
        assertEquals(populated, parsed)
    }

    @Test
    fun `a partial patch changes only the provided key`() {
        val parsed = WebUIApi.parseConfigPatch("""{"worker":"webui-test-worker"}""", populated)
        assertEquals(populated.copy(worker = "webui-test-worker"), parsed)
    }

    @Test
    fun `a patch cannot name the web dashboard preference`() {
        val body = """{"web_server_enabled":false,"webServerEnabled":false}"""
        assertEquals(populated, WebUIApi.parseConfigPatch(body, populated))
    }

    @Test
    fun `malformed json raises for the transport to map to bad_json`() {
        assertThrows(JSONException::class.java) {
            WebUIApi.parseConfigPatch("not json", populated)
        }
    }

    @Test
    fun `validation errors serialise with field name and message`() {
        val errors = listOf(ConfigError(ConfigError.Field.WORKER, "Worker name is required"))
        val o = JSONObject(WebUIApi.validationErrorJson(errors))
        assertFalse(o.getBoolean("ok"))
        assertEquals("invalid_config", o.getString("error"))
        val arr = o.getJSONArray("errors")
        assertEquals(1, arr.length())
        assertEquals("WORKER", arr.getJSONObject(0).getString("field"))
        assertEquals("Worker name is required", arr.getJSONObject(0).getString("message"))
    }

    @Test
    fun `device json carries model version abi and ip including null ip`() {
        val o = JSONObject(WebUIApi.deviceJson("SM-S901U", "16", "arm64-v8a", "192.168.10.50"))
        assertEquals("SM-S901U", o.getString("model"))
        assertEquals("16", o.getString("androidVersion"))
        assertEquals("arm64-v8a", o.getString("abi"))
        assertEquals("192.168.10.50", o.getString("ip"))
        val noIp = JSONObject(WebUIApi.deviceJson("X96Q", "10", "armeabi-v7a", null))
        assertTrue(noIp.isNull("ip"))
    }

    @Test
    fun `all specified routes are known`() {
        val gets = listOf("/", "/config", "/style.css", "/app.js", "/api/status", "/api/config", "/api/device")
        val posts = listOf("/api/config", "/api/mining/start", "/api/mining/stop")
        gets.forEach { assertTrue("GET $it", WebUIApi.isKnownRoute("GET", it)) }
        posts.forEach { assertTrue("POST $it", WebUIApi.isKnownRoute("POST", it)) }
    }

    @Test
    fun `no server control routes exist in the route table`() {
        val paths = listOf(
            "/api/server", "/api/server/", "/api/server/stop",
            "/api/server/toggle", "/api/server/restart", "/api/server/off",
        )
        val methods = listOf("GET", "POST", "OPTIONS")
        for (p in paths) {
            for (m in methods) {
                assertFalse("$m $p must not be routed", WebUIApi.isKnownRoute(m, p))
            }
        }
    }

    @Test
    fun `unknown paths and methods are not routed`() {
        assertFalse(WebUIApi.isKnownRoute("GET", "/api/nope"))
        assertFalse(WebUIApi.isKnownRoute("GET", "/index.html"))
        assertFalse(WebUIApi.isKnownRoute("GET", "/../style.css"))
        assertFalse(WebUIApi.isKnownRoute("GET", "/API/STATUS"))
        assertFalse(WebUIApi.isKnownRoute("DELETE", "/api/config"))
        assertFalse(WebUIApi.isKnownRoute("PUT", "/api/config"))
    }
}
