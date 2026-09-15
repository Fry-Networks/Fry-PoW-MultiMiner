package com.frynetworks.pow

import com.frynetworks.pow.catalog.CoinCatalog
import com.frynetworks.pow.catalog.WalletRules
import com.frynetworks.pow.data.DeviceWorkerName
import com.frynetworks.pow.data.MiningConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the Tempest report: every FryPoW install shipped the same
 * worker name, so a pool collapsed all of a user's devices into one worker row and
 * its dashboard showed only one device at a time, rotating between them.
 *
 * The shared literal was the default of [MiningConfig.worker]. A device-derived name
 * from [DeviceWorkerName] is supplied by ConfigRepository instead, so no two installs
 * authorize with the same worker unless the user deliberately types the same name.
 */
class TempestWorkerNameTest {

    private val wallet = "RRhFqT2bfXQmsnqtyrVxikhy94KqnVf5nt"

    @Test
    fun `the default worker is not a shared constant across installs`() {
        // "worker1" was hardcoded, so six devices all sent <wallet>.worker1.
        assertNotEquals("worker1", MiningConfig().worker)
    }

    @Test
    fun `two different devices derive different worker names`() {
        val phone = DeviceWorkerName.derive("SM-S901U", "a1b2c3d4e5f60718")
        val tvBox = DeviceWorkerName.derive("X96Q", "99887766554433aa")

        assertNotEquals(phone, tvBox)
        assertTrue(phone.isNotBlank())
        assertTrue(tvBox.isNotBlank())
    }

    @Test
    fun `the same device derives the same worker name every time`() {
        val first = DeviceWorkerName.derive("SM-S901U", "a1b2c3d4e5f60718")
        val second = DeviceWorkerName.derive("SM-S901U", "a1b2c3d4e5f60718")

        assertEquals(first, second)
    }

    @Test
    fun `two devices of the same model still differ by device id`() {
        val one = DeviceWorkerName.derive("X96Q", "1111111111111111")
        val two = DeviceWorkerName.derive("X96Q", "2222222222222222")

        assertNotEquals(one, two)
    }

    @Test
    fun `a derived name carries no dot because stratum splits the login on it`() {
        val name = DeviceWorkerName.derive("Pixel 7.Pro", "aa.bb.cc.dd.ee.ff")

        assertFalse(name.contains("."))
    }

    @Test
    fun `a derived name stays inside the pool-safe character set`() {
        val name = DeviceWorkerName.derive("Xiaomi Redmi Note 12 Pro+ 5G", "ff:ee!dd@cc#bb")

        assertTrue(name, name.all { it.isLetterOrDigit() || it == '_' || it == '-' })
    }

    @Test
    fun `a device with no readable id still yields a usable name`() {
        val name = DeviceWorkerName.derive("SM-S901U", "")

        assertTrue(name.isNotBlank())
        assertFalse(name.endsWith("-"))
    }

    @Test
    fun `a device with neither model nor id falls back rather than going blank`() {
        val name = DeviceWorkerName.derive("", "")

        assertTrue(name.isNotBlank())
    }

    @Test
    fun `the stored legacy default is migrated to the device name`() {
        val device = DeviceWorkerName.derive("SM-S901U", "a1b2c3d4e5f60718")

        assertEquals(device, DeviceWorkerName.resolve("worker1", device))
    }

    @Test
    fun `a worker the user typed is never overwritten`() {
        val device = DeviceWorkerName.derive("SM-S901U", "a1b2c3d4e5f60718")

        assertEquals("tempest-rig-3", DeviceWorkerName.resolve("tempest-rig-3", device))
    }

    @Test
    fun `an unset or blank stored worker resolves to the device name`() {
        val device = DeviceWorkerName.derive("X96Q", "99887766554433aa")

        assertEquals(device, DeviceWorkerName.resolve(null, device))
        assertEquals(device, DeviceWorkerName.resolve("   ", device))
    }

    @Test
    fun `a wallet already carrying the rig suffix is not double-appended`() {
        val xmr = CoinCatalog.byId("xmr")!!

        // MiningRigRentals' username.rigid form, which users paste in whole.
        assertEquals("testuser.000000", WalletRules.minerUser(xmr, "testuser.000000", "000000"))
    }

    @Test
    fun `a deliberately different worker is still appended`() {
        val xmr = CoinCatalog.byId("xmr")!!

        assertEquals("4address.rig1.rig9", WalletRules.minerUser(xmr, "4address.rig1", "rig9"))
    }

    @Test
    fun `the double-append guard keeps the unmineable referral intact`() {
        val shib = CoinCatalog.byId("shib")!!

        assertEquals(
            "SHIB:0xabc123.rig1#efz3-b4fb",
            WalletRules.minerUser(shib, "SHIB:0xabc123.rig1", "rig1"),
        )
    }

    @Test
    fun `the full stratum login differs between two devices sharing one wallet`() {
        val verus = CoinCatalog.byId("verus")!!
        val phone = DeviceWorkerName.derive("SM-S901U", "a1b2c3d4e5f60718")
        val tvBox = DeviceWorkerName.derive("X96Q", "99887766554433aa")

        val phoneLogin = WalletRules.minerUser(verus, wallet, phone)
        val tvLogin = WalletRules.minerUser(verus, wallet, tvBox)

        // This is precisely what the pool keys its worker rows on.
        assertNotEquals(phoneLogin, tvLogin)
        assertEquals("$wallet.$phone", phoneLogin)
        assertEquals("$wallet.$tvBox", tvLogin)
    }
}
