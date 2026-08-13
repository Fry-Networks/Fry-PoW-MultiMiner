package com.frynetworks.pow.devfee

import com.frynetworks.pow.catalog.Coin
import com.frynetworks.pow.catalog.CoinGroup

/**
 * Full parity with the Linux control panel's 2% dev fee: mine to the user's wallet
 * for 49 minutes of every 50, then to the project wallet for 1 minute.
 *
 * Everything about the fee lives in this file and in one branch of the session loop,
 * so it stays auditable and the disclosure text can never drift from the behaviour.
 */
object DevFee {

    const val PERCENT = 2
    const val USER_MINUTES = 49
    const val DEV_MINUTES = 1
    const val DEV_WORKER = "frydev"

    val enabled: Boolean = true

    private const val XMR = "482R7WT5xYVKa2SYHaDtSGWQPv82sgwfSVBGfjV5wez2hbnVTiDRGHb7AEsP5NLGDrBNfFgacPkNSEToGYissp2GRRiSUyo"
    private const val LTC = "ltc1qrdc0wqzs3cwuhxxzkq2khepec2l3c6uhd8l9jy"
    private const val BTC = "bc1qr6ldduupwn4dtqq4dwthv4vp3cg2dx7u3mcgva"
    private const val DOGE = "D5nsUsiivbNv2nmuNE9x2ybkkCTEL4ceHj"
    private const val DASH = "Xff5VZsVpFxpJYazyQ8hbabzjWAmq1TqPG"
    private const val DCR = "DsTSHaQRwE9bibKtq5gCtaYZXSp7UhzMiWw"
    private const val KDA = "k:05178b77e1141ca2319e66cab744e8149349b3f140a676624f231314d483f7a3"
    private const val BCH = "qrsvjp5987h57x8e6tnv430gq4hnq4jy5vf8u5x4d9"
    private const val DERO = "dero1qysrv5fp2xethzatpdf80umh8yu2nk404tc3cw2lwypgynj3qvhtgqq294092"
    private const val ZEPH = "ZEPHsD5WFqKYHXEAqQLj9Nds4ZAS3KbK1Ht98SRy5u9d7Pp2gs6hPpw8UfA1iPgLdUgKpjXx72AjFN1QizwKY2SbXgMzEiQohBn"
    private const val SCALA = "Ssy2BnsAcJUVZZ2kTiywf61bvYjvPosXzaBcaft9RSvaNNKsFRkcKbaWjMotjATkSbSmeSdX2DAxc1XxpcdxUBGd41oCwwfetG"
    private const val VRSC = "RRhFqT2bfXQmsnqtyrVxikhy94KqnVf5nt"
    private const val SAL = "SC1siGvtk7BQ7mkwsjXo57XF4y6SKsX547rfhzHJXGojeRSYoDWknqrJKeYHuMbqhbjSWYvxLppoMdCFjHHhVnrmZUxEc5QdYFj"
    private const val YDA = "1NLFnpcykRcoAMKX35wyzZm2d8ChbQvXB3"

    /** Unmineable dev fee routes to Scala, matching the shell version. */
    fun walletFor(coin: Coin): String {
        if (coin.group == CoinGroup.UNMINEABLE) return SCALA
        return when (coin.id) {
            "xmr", "xmr-lotto", "aeon", "xel-lotto" -> XMR
            "ltc", "ltc-lotto" -> LTC
            "btc", "btc-lotto", "dgb-lotto", "xec-lotto", "fb-lotto", "bc2-lotto" -> BTC
            "doge", "doge-lotto" -> DOGE
            "dash" -> DASH
            "dcr" -> DCR
            "kda" -> KDA
            "bch-lotto" -> BCH
            "dero" -> DERO
            "zephyr", "zeph-lotto" -> ZEPH
            "scala" -> SCALA
            "verus" -> VRSC
            "salvium" -> SAL
            "yadacoin" -> YDA
            else -> XMR
        }
    }

    val userSliceMillis: Long = USER_MINUTES * 60_000L
    val devSliceMillis: Long = DEV_MINUTES * 60_000L
}
