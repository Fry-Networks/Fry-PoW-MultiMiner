package com.frynetworks.pow.data

import com.frynetworks.pow.catalog.Coin
import com.frynetworks.pow.catalog.CoinCatalog
import com.frynetworks.pow.catalog.MinerFamily

/** A validation failure tied to the field that caused it, so the UI can mark it. */
data class ConfigError(val field: Field, val message: String) {
    enum class Field { COIN, WALLET, POOL, THREADS, WORKER }
}

object ConfigValidator {

    private const val MAX_THREADS = 64

    fun validate(config: MiningConfig, maxThreads: Int = MAX_THREADS): List<ConfigError> {
        val errors = mutableListOf<ConfigError>()
        val coin: Coin? = CoinCatalog.byId(config.coinId)

        if (coin == null) {
            errors += ConfigError(ConfigError.Field.COIN, "Choose a coin to mine")
            return errors
        }

        if (coin.family == MinerFamily.INFO_ONLY) {
            errors += ConfigError(ConfigError.Field.COIN, "${coin.name} is informational only and cannot be mined here")
            return errors
        }

        if (config.wallet.isBlank()) {
            errors += ConfigError(ConfigError.Field.WALLET, "Wallet address is required")
        }

        val pool = config.pool.ifBlank { coin.defaultPool.orEmpty() }
        when {
            pool.isBlank() ->
                errors += ConfigError(ConfigError.Field.POOL, "Pool address is required")
            !pool.contains(":") ->
                errors += ConfigError(ConfigError.Field.POOL, "Pool must include a port, e.g. pool.example.com:3333")
            pool.startsWith("stratum+ssl://") || pool.startsWith("ssl://") ->
                errors += ConfigError(
                    ConfigError.Field.POOL,
                    "TLS pools are not supported in this build - use a plain stratum+tcp:// pool",
                )
        }

        if (config.threads < 1 || config.threads > maxThreads) {
            errors += ConfigError(ConfigError.Field.THREADS, "Threads must be between 1 and $maxThreads")
        }

        if (config.worker.isBlank()) {
            errors += ConfigError(ConfigError.Field.WORKER, "Worker name is required")
        }

        return errors
    }

    /** Strip the protocol prefix, matching the Linux panel's sed pipeline. */
    fun normalisePool(raw: String): String =
        raw.trim()
            .removePrefix("stratum+tcp://")
            .removePrefix("stratum+ssl://")
            .removePrefix("stratum://")
            .removePrefix("https://")
            .removePrefix("http://")
}
