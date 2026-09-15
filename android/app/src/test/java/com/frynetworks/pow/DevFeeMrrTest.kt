package com.frynetworks.pow

import com.frynetworks.pow.catalog.CoinCatalog
import com.frynetworks.pow.data.MiningConfig
import com.frynetworks.pow.devfee.DevFee
import com.frynetworks.pow.mining.SessionPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression: the dev-fee slice must not mine a raw coin address at a
 * MiningRigRentals assigned port.
 *
 * MRR authenticates only `username.rigid`. SessionPlanner computed ONE pool and
 * used it for both slices, so on an MRR pool the dev slice presented the raw dev
 * address (for verus: RRhFqT2bfXQmsnqtyrVxikhy94KqnVf5nt) and failed auth, turning
 * the whole 60-second dev minute into a client.reconnect storm. The shell
 * installers had the identical bug; measured there at 40-70 reconnects per dev
 * slice, with M2 at 597 reconnects / 65% efficiency until it was fixed.
 *
 * On an MRR pool the dev slice now goes to the coin's own public pool
 * (coin.defaultPool). On any other pool it keeps using the user's pool, so
 * existing configurations are unaffected.
 */
class DevFeeMrrTest {

    private val binary = File("/data/app/lib/arm64/libverus.so")

    private val mrrPool = "us-central01.miningrigrentals.com:50912"
    private val publicPool = "pool.verus.io:9999"
    private val userWallet = "RUserWalletExampleAddr0000000000000"

    private fun config(wallet: String, pool: String) = MiningConfig(
        coinId = "verus",
        wallet = wallet,
        worker = "rig1",
        threads = 2,
        pool = pool,
        password = "x",
    )

    private fun plan(wallet: String, pool: String, devSlice: Boolean) =
        SessionPlanner.plan(
            binary,
            CoinCatalog.byId("verus")!!,
            config(wallet, pool),
            devSlice = devSlice,
            lowMemory = false,
        )

    @Test
    fun `on an MRR pool the dev slice does not use the user's MRR port`() {
        val dev = plan(userWallet, mrrPool, devSlice = true)

        // The raw dev address cannot authenticate against an MRR assigned port.
        assertNotEquals(mrrPool, dev.pool)
    }

    @Test
    fun `on an MRR pool the dev slice uses the coin's public pool`() {
        val dev = plan(userWallet, mrrPool, devSlice = true)

        assertEquals(publicPool, dev.pool)
    }

    @Test
    fun `the user slice still mines the user's own pool on MRR`() {
        val user = plan(userWallet, mrrPool, devSlice = false)

        // Only the dev slice is redirected; the user must keep mining their rig.
        assertEquals(mrrPool, user.pool)
    }

    @Test
    fun `on a non-MRR pool the dev slice is unchanged`() {
        val dev = plan(userWallet, publicPool, devSlice = true)
        val user = plan(userWallet, publicPool, devSlice = false)

        assertEquals(publicPool, dev.pool)
        assertEquals(user.pool, dev.pool)
    }

    @Test
    fun `the dev slice still presents the dev wallet and frydev worker`() {
        val dev = plan(userWallet, mrrPool, devSlice = true)

        // Redirecting the pool must not disturb who gets paid.
        assertEquals("RRhFqT2bfXQmsnqtyrVxikhy94KqnVf5nt.frydev", dev.user)
    }

    @Test
    fun `MRR pools are recognised and other pools are not`() {
        assertTrue(DevFee.isMrrPool(mrrPool))
        assertTrue(DevFee.isMrrPool("eu-01.MiningRigRentals.com:3333"))
        assertFalse(DevFee.isMrrPool(publicPool))
        assertFalse(DevFee.isMrrPool("na.luckpool.net:3956"))
    }

    @Test
    fun `a user already mining the coin's dev wallet pays no dev fee`() {
        val verus = CoinCatalog.byId("verus")!!

        assertTrue(DevFee.shouldSkipCycle(verus, "RRhFqT2bfXQmsnqtyrVxikhy94KqnVf5nt"))
    }

    @Test
    fun `a user mining the operator's nominated wallet pays no dev fee`() {
        val verus = CoinCatalog.byId("verus")!!

        // The SBC fleet mines to a pool-side rig account, which is already the
        // project's; charging it a dev fee would be a transfer to itself that costs a
        // miner teardown every 50 minutes. The real value is an account identifier and
        // so is supplied at build time, not hardcoded - hence the injected placeholder.
        assertTrue(DevFee.shouldSkipCycle(verus, SKIP_WALLET, skipWallet = SKIP_WALLET))
    }

    @Test
    fun `a blank nominated wallet skips nothing extra`() {
        val verus = CoinCatalog.byId("verus")!!

        // Public builds ship with no nominated wallet. Nothing may then match it - in
        // particular a blank config must not turn every wallet into a skip.
        assertFalse(DevFee.shouldSkipCycle(verus, userWallet, skipWallet = ""))
        assertFalse(DevFee.shouldSkipCycle(verus, SKIP_WALLET, skipWallet = ""))
        assertFalse(DevFee.shouldSkipCycle(verus, "   ", skipWallet = "   "))

        // The coin's own dev wallet still skips without any config.
        assertTrue(DevFee.shouldSkipCycle(verus, DevFee.walletFor(verus), skipWallet = ""))
    }

    @Test
    fun `an ordinary user still pays the dev fee`() {
        val verus = CoinCatalog.byId("verus")!!

        assertFalse(DevFee.shouldSkipCycle(verus, userWallet, skipWallet = SKIP_WALLET))
        assertFalse(DevFee.shouldSkipCycle(verus, "", skipWallet = SKIP_WALLET))
    }

    private companion object {
        /** Stands in for the operator's nominated wallet; never a real account id. */
        const val SKIP_WALLET = "testrig.000000"
    }
}
