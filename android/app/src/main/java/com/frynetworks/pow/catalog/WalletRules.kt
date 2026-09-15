package com.frynetworks.pow.catalog

/**
 * The Unmineable and worker-name conventions from the Linux control panel, in one
 * place. The shell version spreads these across several `case` blocks; duplicating
 * that here is how the two implementations would drift apart.
 */
object WalletRules {

    const val UNMINEABLE_REFERRAL = "efz3-b4fb"

    /** Unmineable expects `TICKER:address`; typing the prefix by hand must not double it. */
    fun effectiveWallet(coin: Coin, wallet: String): String {
        val trimmed = wallet.trim()
        if (coin.group != CoinGroup.UNMINEABLE) return trimmed
        val prefix = "${coin.ticker.uppercase()}:"
        return if (trimmed.uppercase().startsWith(prefix)) trimmed else prefix + trimmed
    }

    /** The full `-u` / `--user` string handed to the miner. */
    fun minerUser(coin: Coin, wallet: String, worker: String): String {
        val base = effectiveWallet(coin, wallet)
        val rig = worker.trim().ifEmpty { "worker1" }
        // The wallet often already carries the rig as a dotted suffix - MiningRigRentals'
        // `username.rigid` form, and anything pasted from a pool's own instructions.
        // Appending again would build ADDRESS.RIG.RIG, which the pool reads as a different
        // worker. Same guard the Linux panel applies in save.cgi. A deliberately different
        // worker (ADDRESS.rig1 + worker=rig9) is still appended normally.
        val suffixed = if (base.endsWith(".$rig")) base else "$base.$rig"
        return if (coin.group == CoinGroup.UNMINEABLE) {
            "$suffixed#$UNMINEABLE_REFERRAL"
        } else {
            suffixed
        }
    }
}
