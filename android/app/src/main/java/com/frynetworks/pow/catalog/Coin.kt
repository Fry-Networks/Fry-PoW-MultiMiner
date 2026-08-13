package com.frynetworks.pow.catalog

/** Which native miner binary a coin needs. */
enum class MinerFamily {
    XMRIG,
    XLARIG,
    CPUMINER,
    VERUS,

    /** Needs a GPU miner — no Android build exists. */
    GPU_ONLY,

    /** Needs the Solana ore-cli toolchain. */
    ORE,

    /** Needs a reachable Algorand node and goal. */
    ORA,

    /** Listed for information only; not mineable through this app. */
    INFO_ONLY,
}

enum class CoinGroup(val label: String) {
    POPULAR("Popular"),
    CPU_MINEABLE("CPU Mineable"),
    OTHER("Other Mineable"),
    LOTTO("Solo Lottery"),
    UNMINEABLE("Unmineable"),
    BLOCKCHAIN_POW("Blockchain PoW"),
    SPECIAL("Special"),
}

data class Coin(
    val id: String,
    val name: String,
    val ticker: String,
    val group: CoinGroup,
    val algo: String,
    val family: MinerFamily,
    val defaultPool: String?,
    val poolLocked: Boolean = false,
    val note: String? = null,
)
