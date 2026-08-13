package com.frynetworks.pow

import com.frynetworks.pow.catalog.CoinCatalog
import com.frynetworks.pow.data.MiningConfig
import com.frynetworks.pow.devfee.DevFee
import com.frynetworks.pow.mining.SessionPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SessionPlannerTest {

    private val binary = File("/data/app/lib/arm64/libxmrig.so")

    private fun config(coin: String, pool: String = "") = MiningConfig(
        coinId = coin,
        wallet = "WALLET123",
        worker = "rig1",
        threads = 4,
        pool = pool,
        password = "x",
    )

    @Test
    fun `xmrig gets the algorithm thread count and no built-in donation`() {
        val coin = CoinCatalog.byId("xmr")!!

        val plan = SessionPlanner.plan(binary, coin, config("xmr"), devSlice = false, lowMemory = false)

        assertTrue(plan.argv.containsAll(listOf("-a", "rx/0", "--threads=4", "--donate-level=0", "--no-color")))
        assertEquals("pool.supportxmr.com:3333", plan.pool)
        assertEquals("WALLET123.rig1", plan.user)
    }

    @Test
    fun `a protocol prefix on the pool is stripped`() {
        val coin = CoinCatalog.byId("xmr")!!

        val plan = SessionPlanner.plan(
            binary, coin, config("xmr", "stratum+tcp://my.pool.example:4444"),
            devSlice = false, lowMemory = false,
        )

        assertEquals("my.pool.example:4444", plan.pool)
    }

    @Test
    fun `randomx drops to light mode only on a low memory device`() {
        val coin = CoinCatalog.byId("xmr")!!

        val low = SessionPlanner.plan(binary, coin, config("xmr"), devSlice = false, lowMemory = true)
        val normal = SessionPlanner.plan(binary, coin, config("xmr"), devSlice = false, lowMemory = false)

        assertTrue(low.argv.contains("--randomx-mode=light"))
        assertFalse(normal.argv.contains("--randomx-mode=light"))
    }

    @Test
    fun `the dev fee slice swaps in the project wallet and the frydev worker`() {
        val coin = CoinCatalog.byId("xmr")!!

        val plan = SessionPlanner.plan(binary, coin, config("xmr"), devSlice = true, lowMemory = false)

        assertTrue(plan.devSlice)
        assertTrue(plan.user.endsWith(".${DevFee.DEV_WORKER}"))
        assertFalse(plan.user.contains("WALLET123"))
    }

    @Test
    fun `an unmineable dev slice keeps the referral code`() {
        val coin = CoinCatalog.byId("shib")!!

        val plan = SessionPlanner.plan(binary, coin, config("shib"), devSlice = true, lowMemory = false)

        assertTrue(plan.user.contains("#efz3-b4fb"))
        assertTrue(plan.user.contains(".${DevFee.DEV_WORKER}"))
    }

    @Test
    fun `cpuminer uses the unambiguous retries flag`() {
        val coin = CoinCatalog.byId("ltc")!!

        val plan = SessionPlanner.plan(binary, coin, config("ltc"), devSlice = false, lowMemory = false)

        // "--retry" alone is an ambiguous prefix of "--retry-pause" and makes the
        // miner exit immediately.
        assertTrue(plan.argv.contains("--retries"))
        assertFalse(plan.argv.contains("--retry"))
        assertTrue(plan.argv.contains("--algo=scrypt"))
        assertTrue(plan.argv.any { it.startsWith("stratum+tcp://") })
    }

    @Test
    fun `a coin with no android miner produces no command`() {
        val coin = CoinCatalog.byId("rvn-lotto")!!

        val plan = SessionPlanner.plan(binary, coin, config("rvn-lotto"), devSlice = false, lowMemory = false)

        assertTrue(plan.argv.isEmpty())
    }
}
