package com.frynetworks.pow

import com.frynetworks.pow.catalog.CoinCatalog
import com.frynetworks.pow.catalog.CoinGroup
import com.frynetworks.pow.catalog.MinerFamily
import com.frynetworks.pow.catalog.WalletRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogAndWalletTest {

    @Test
    fun `coin ids are unique`() {
        val ids = CoinCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every mineable coin has a default pool`() {
        val mineable = CoinCatalog.all.filter {
            it.family in setOf(MinerFamily.XMRIG, MinerFamily.XLARIG, MinerFamily.CPUMINER, MinerFamily.VERUS)
        }
        assertTrue(mineable.isNotEmpty())
        mineable.forEach { assertNotNull("${it.id} has no pool", it.defaultPool) }
    }

    @Test
    fun `all unmineable coins are locked to the unmineable pool`() {
        val unmineable = CoinCatalog.all.filter { it.group == CoinGroup.UNMINEABLE }
        assertEquals(19, unmineable.size)
        unmineable.forEach {
            assertTrue(it.poolLocked)
            assertEquals("rx.unmineable.com:3333", it.defaultPool)
            assertEquals("rx/0", it.algo)
        }
    }

    @Test
    fun `unmineable wallets get the ticker prefix and referral code`() {
        val shib = CoinCatalog.byId("shib")!!

        val user = WalletRules.minerUser(shib, "0xabc123", "rig1")

        assertEquals("SHIB:0xabc123.rig1#efz3-b4fb", user)
    }

    @Test
    fun `an address that already carries the prefix is not double prefixed`() {
        val shib = CoinCatalog.byId("shib")!!

        assertEquals("SHIB:0xabc123", WalletRules.effectiveWallet(shib, "SHIB:0xabc123"))
    }

    @Test
    fun `direct coins get no prefix and no referral`() {
        val xmr = CoinCatalog.byId("xmr")!!

        assertEquals("4address.worker1", WalletRules.minerUser(xmr, "4address", "worker1"))
    }

    @Test
    fun `a blank worker falls back to the default name`() {
        val xmr = CoinCatalog.byId("xmr")!!

        assertEquals("4address.worker1", WalletRules.minerUser(xmr, "4address", "  "))
    }

    @Test
    fun `gpu only coins report why they cannot run regardless of installed miners`() {
        val rvn = CoinCatalog.byId("rvn-lotto")!!
        val everything = setOf(MinerFamily.XMRIG, MinerFamily.XLARIG, MinerFamily.CPUMINER, MinerFamily.VERUS)

        val reason = CoinCatalog.unavailabilityReason(rvn, everything)

        assertNotNull(reason)
        assertFalse(CoinCatalog.isRunnable(rvn, everything))
    }

    @Test
    fun `a coin becomes runnable only when its miner family is present`() {
        val xmr = CoinCatalog.byId("xmr")!!

        assertNull(CoinCatalog.unavailabilityReason(xmr, setOf(MinerFamily.XMRIG)))
        assertNotNull(CoinCatalog.unavailabilityReason(xmr, setOf(MinerFamily.CPUMINER)))
    }
}
