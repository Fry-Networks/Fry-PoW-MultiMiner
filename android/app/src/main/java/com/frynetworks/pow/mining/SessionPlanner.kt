package com.frynetworks.pow.mining

import com.frynetworks.pow.catalog.Coin
import com.frynetworks.pow.catalog.MinerFamily
import com.frynetworks.pow.catalog.WalletRules
import com.frynetworks.pow.data.ConfigValidator
import com.frynetworks.pow.data.MiningConfig
import com.frynetworks.pow.devfee.DevFee
import java.io.File

data class SessionPlan(
    val argv: List<String>,
    val pool: String,
    val user: String,
    val devSlice: Boolean,
)

/** Pure command-line assembly - the unit-tested heart of launching a miner. */
object SessionPlanner {

    /** RandomX needs ~2 GB for its full dataset; below this it must run in light mode. */
    const val LOW_MEMORY_THRESHOLD_BYTES = 3L * 1024 * 1024 * 1024

    fun plan(
        binary: File,
        coin: Coin,
        config: MiningConfig,
        devSlice: Boolean,
        lowMemory: Boolean,
    ): SessionPlan {
        val pool = ConfigValidator.normalisePool(config.pool.ifBlank { coin.defaultPool.orEmpty() })
        val threads = config.threads.coerceAtLeast(1)

        val user = if (devSlice) {
            val devWallet = DevFee.walletFor(coin)
            if (coin.group == com.frynetworks.pow.catalog.CoinGroup.UNMINEABLE) {
                "$devWallet.${DevFee.DEV_WORKER}#${WalletRules.UNMINEABLE_REFERRAL}"
            } else {
                "$devWallet.${DevFee.DEV_WORKER}"
            }
        } else {
            WalletRules.minerUser(coin, config.wallet, config.worker)
        }
        val password = if (devSlice) "x" else config.password.ifBlank { "x" }

        val argv = when (coin.family) {
            MinerFamily.XMRIG, MinerFamily.XLARIG -> buildList {
                add(binary.absolutePath)
                add("-o"); add(pool)
                add("-u"); add(user)
                add("-p"); add(password)
                add("-a"); add(coin.algo)
                add("--threads=$threads")
                add("--no-color")
                add("--print-time=10")
                // The app takes its own fee slice; the miner's built-in donation stays off.
                add("--donate-level=0")
                if (coin.algo.startsWith("rx/") && lowMemory) {
                    add("--randomx-mode=light")
                    add("--huge-pages=false")
                }
            }

            MinerFamily.CPUMINER -> buildList {
                add(binary.absolutePath)
                add("--algo=${coin.algo}")
                add("-o"); add("stratum+tcp://$pool")
                add("-u"); add(user)
                add("-p"); add(password)
                add("--threads=$threads")
                // "--retries" in full: getopt_long cannot disambiguate "--retry" from
                // "--retry-pause", and the miner exits immediately when it is ambiguous.
                add("--retries"); add("10")
                add("--retry-pause"); add("30")
            }

            MinerFamily.VERUS -> buildList {
                add(binary.absolutePath)
                add("-a"); add("verus")
                add("-o"); add("stratum+tcp://$pool")
                add("-u"); add(user)
                add("-p"); add(password)
                add("-t"); add(threads.toString())
            }

            else -> emptyList()
        }

        return SessionPlan(argv = argv, pool = pool, user = user, devSlice = devSlice)
    }
}
