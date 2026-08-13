package com.frynetworks.pow

import com.frynetworks.pow.data.ConfigError
import com.frynetworks.pow.data.ConfigValidator
import com.frynetworks.pow.data.MiningConfig
import com.frynetworks.pow.update.VersionComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigAndVersionTest {

    private val valid = MiningConfig(
        coinId = "xmr", wallet = "4addr", worker = "rig1", threads = 2, pool = "", password = "x",
    )

    @Test
    fun `a complete configuration passes`() {
        assertTrue(ConfigValidator.validate(valid, maxThreads = 8).isEmpty())
    }

    @Test
    fun `a missing wallet is reported against the wallet field`() {
        val errors = ConfigValidator.validate(valid.copy(wallet = ""), maxThreads = 8)

        assertTrue(errors.any { it.field == ConfigError.Field.WALLET })
    }

    @Test
    fun `an unknown coin fails before anything else is checked`() {
        val errors = ConfigValidator.validate(valid.copy(coinId = "nope"), maxThreads = 8)

        assertEquals(1, errors.size)
        assertEquals(ConfigError.Field.COIN, errors.first().field)
    }

    @Test
    fun `a pool without a port is rejected`() {
        val errors = ConfigValidator.validate(valid.copy(pool = "pool.example.com"), maxThreads = 8)

        assertTrue(errors.any { it.field == ConfigError.Field.POOL })
    }

    @Test
    fun `a tls pool is rejected with a specific reason because this build has no TLS`() {
        val errors = ConfigValidator.validate(
            valid.copy(pool = "stratum+ssl://pool.example.com:443"), maxThreads = 8,
        )

        assertTrue(errors.any { it.field == ConfigError.Field.POOL && it.message.contains("TLS") })
    }

    @Test
    fun `thread count must fit the device`() {
        assertTrue(ConfigValidator.validate(valid.copy(threads = 0), maxThreads = 8).isNotEmpty())
        assertTrue(ConfigValidator.validate(valid.copy(threads = 99), maxThreads = 8).isNotEmpty())
    }

    @Test
    fun `an informational coin cannot be mined`() {
        val errors = ConfigValidator.validate(valid.copy(coinId = "tera"), maxThreads = 8)

        assertTrue(errors.any { it.field == ConfigError.Field.COIN })
    }

    @Test
    fun `version comparison understands prefixed release tags`() {
        assertTrue(VersionComparator.isNewer("v1.1.0", "1.0.0"))
        assertTrue(VersionComparator.isNewer("android-v2.0.0", "1.9.9"))
        assertFalse(VersionComparator.isNewer("v1.0.0", "1.0.0"))
        assertFalse(VersionComparator.isNewer("v0.9.0", "1.0.0"))
    }

    @Test
    fun `an unparseable tag never claims an update is available`() {
        assertFalse(VersionComparator.isNewer("latest", "1.0.0"))
    }
}
