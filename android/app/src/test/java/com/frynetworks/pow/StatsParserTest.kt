package com.frynetworks.pow

import com.frynetworks.pow.mining.StatsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsParserTest {

    @Test
    fun `reads the 10 second hashrate from an xmrig speed line`() {
        val log = listOf(
            "[2026-08-12 10:00:00.000]  net      new job from pool.supportxmr.com:3333",
            "[2026-08-12 10:00:10.000]  miner    speed 10s/60s/15m 218.2 220.6 n/a H/s max 231.0 H/s",
        )

        val result = StatsParser.parseHashrate(log)

        assertEquals(218.2, result!!.first, 0.001)
        assertEquals("H/s", result.second)
    }

    @Test
    fun `keeps the unit prefix when the miner reports kilohashes`() {
        val log = listOf("[..]  miner    speed 10s/60s/15m 12.34 12.00 n/a kH/s max 15.0 kH/s")

        val (value, unit) = StatsParser.parseHashrate(log)!!

        assertEquals(12.34, value, 0.001)
        assertEquals("kH/s", unit)
    }

    @Test
    fun `falls back to a generic rate for non-xmrig miners`() {
        val log = listOf("[2026-08-12 10:00:00] thread 0: 4194304 hashes, 4.52 khash/s")

        val result = StatsParser.parseHashrate(log)

        assertEquals(4.52, result!!.first, 0.001)
    }

    @Test
    fun `returns null rather than zero when no rate has been printed`() {
        assertNull(StatsParser.parseHashrate(listOf("starting", "connected to pool")))
    }

    @Test
    fun `derives rejected shares from the accepted over total form`() {
        val log = listOf("[..] net accepted: 95/100 (diff 50001) gpu 0 ms")

        val (accepted, rejected) = StatsParser.parseShares(log)

        assertEquals(95L, accepted)
        assertEquals(5L, rejected)
    }

    @Test
    fun `never reports negative rejects when totals look inconsistent`() {
        val (accepted, rejected) = StatsParser.parseShares(listOf("accepted: 10/4"))

        assertEquals(10L, accepted)
        assertEquals(0L, rejected)
    }

    @Test
    fun `strips ansi colour codes before matching`() {
        val coloured = "[1;32m[..] miner speed 10s/60s/15m 50.0 51.0 n/a H/s[0m"

        assertEquals(50.0, StatsParser.parseHashrate(listOf(coloured))!!.first, 0.001)
    }

    @Test
    fun `identifies the algorithm from a label or from content`() {
        assertEquals("rx/0", StatsParser.parseAlgo(listOf("[..] Algorithm: rx/0")))
        assertEquals("panthera", StatsParser.parseAlgo(listOf("[..] XLArig starting panthera worker")))
        assertEquals("verushash", StatsParser.parseAlgo(listOf("CPU T0: Verus Hashing. 364.98 kH/s")))
    }

    @Test
    fun `distinguishes a crash from a clean shutdown`() {
        assertTrue(StatsParser.looksLikeCrash(listOf("worker 1", "Segmentation fault")))
        assertFalse(StatsParser.looksLikeCrash(listOf("worker 1", "signal received, exiting")))
    }
}
