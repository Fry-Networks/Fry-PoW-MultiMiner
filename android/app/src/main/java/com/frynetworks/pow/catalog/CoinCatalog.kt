package com.frynetworks.pow.catalog

/**
 * The single coin table. The Linux script encodes this same information six times
 * (HTML optgroups, two JavaScript objects, and three shell `case` blocks); keeping
 * one copy here is the main structural improvement of the port.
 */
object CoinCatalog {

    private const val UNMINEABLE_POOL = "rx.unmineable.com:3333"

    private fun unmineable(id: String, name: String, ticker: String) = Coin(
        id = id, name = name, ticker = ticker, group = CoinGroup.UNMINEABLE,
        algo = "rx/0", family = MinerFamily.XMRIG,
        defaultPool = UNMINEABLE_POOL, poolLocked = true,
        note = "Mined as RandomX and paid out in $ticker by Unmineable.",
    )

    val all: List<Coin> = listOf(
        // --- Popular ---
        Coin("btc", "Bitcoin", "BTC", CoinGroup.POPULAR, "sha256d", MinerFamily.CPUMINER, "pool.btc.com:3333",
            note = "CPU sha256d mining is educational only - ASICs dominate this algorithm."),
        Coin("ltc", "Litecoin", "LTC", CoinGroup.POPULAR, "scrypt", MinerFamily.CPUMINER, "stratum.aikapool.com:7900"),
        Coin("doge", "Dogecoin", "DOGE", CoinGroup.POPULAR, "scrypt", MinerFamily.CPUMINER, "prohashing.com:3332"),
        Coin("xmr", "Monero", "XMR", CoinGroup.POPULAR, "rx/0", MinerFamily.XMRIG, "pool.supportxmr.com:3333"),

        // --- CPU mineable ---
        Coin("scala", "Scala", "XLA", CoinGroup.CPU_MINEABLE, "panthera", MinerFamily.XLARIG, "pool.scalaproject.io:3333"),
        Coin("verus", "VerusCoin", "VRSC", CoinGroup.CPU_MINEABLE, "verushash", MinerFamily.VERUS, "pool.verus.io:9999"),
        Coin("aeon", "Aeon", "AEON", CoinGroup.CPU_MINEABLE, "rx/0", MinerFamily.XMRIG, "aeon.herominers.com:10650"),
        Coin("dero", "Dero", "DERO", CoinGroup.CPU_MINEABLE, "astrobwt", MinerFamily.XMRIG, "dero-node-sk.mysrv.cloud:10300"),
        Coin("zephyr", "Zephyr", "ZEPH", CoinGroup.CPU_MINEABLE, "rx/0", MinerFamily.XMRIG, "de.zephyr.herominers.com:1123"),
        Coin("salvium", "Salvium", "SAL", CoinGroup.CPU_MINEABLE, "rx/0", MinerFamily.XMRIG, "de.salvium.herominers.com:1228"),
        Coin("yadacoin", "YadaCoin", "YDA", CoinGroup.CPU_MINEABLE, "rx/yada", MinerFamily.XMRIG, "pool.yadacoin.io:3333"),
        Coin("arionum", "Arionum", "ARO", CoinGroup.CPU_MINEABLE, "argon2d4096", MinerFamily.CPUMINER, "aropool.com:80"),

        // --- Other mineable ---
        Coin("dash", "Dash", "DASH", CoinGroup.OTHER, "x11", MinerFamily.CPUMINER, "dash.suprnova.cc:9989"),
        Coin("dcr", "Decred", "DCR", CoinGroup.OTHER, "decred", MinerFamily.CPUMINER, "dcr.suprnova.cc:3252"),
        Coin("kda", "Kadena", "KDA", CoinGroup.OTHER, "blake2s", MinerFamily.CPUMINER, "pool.woolypooly.com:3112"),

        // --- Solo lottery ---
        Coin("btc-lotto", "Bitcoin Solo", "BTC", CoinGroup.LOTTO, "sha256d", MinerFamily.CPUMINER, "eu1.solopool.org:8004"),
        Coin("bch-lotto", "Bitcoin Cash Solo", "BCH", CoinGroup.LOTTO, "sha256d", MinerFamily.CPUMINER, "eu1.solopool.org:8006"),
        Coin("ltc-lotto", "Litecoin Solo", "LTC", CoinGroup.LOTTO, "scrypt", MinerFamily.CPUMINER, "eu1.solopool.org:8001",
            note = "Merged mining - a DOGE address can be supplied as the second wallet."),
        Coin("doge-lotto", "Dogecoin Solo", "DOGE", CoinGroup.LOTTO, "scrypt", MinerFamily.CPUMINER, "eu1.solopool.org:8002",
            note = "Merged mining - an LTC address can be supplied as the second wallet."),
        Coin("dgb-lotto", "DigiByte Solo", "DGB", CoinGroup.LOTTO, "sha256d", MinerFamily.CPUMINER, "eu1.solopool.org:8010"),
        Coin("xec-lotto", "eCash Solo", "XEC", CoinGroup.LOTTO, "sha256d", MinerFamily.CPUMINER, "eu1.solopool.org:8012"),
        Coin("fb-lotto", "Fractal Solo", "FB", CoinGroup.LOTTO, "sha256d", MinerFamily.CPUMINER, "eu2.solopool.org:8014"),
        Coin("bc2-lotto", "Bitcoin II Solo", "BC2", CoinGroup.LOTTO, "sha256d", MinerFamily.CPUMINER, "eu2.solopool.org:8016"),
        Coin("xmr-lotto", "Monero Solo", "XMR", CoinGroup.LOTTO, "rx/0", MinerFamily.XMRIG, "eu1.solopool.org:8020"),
        Coin("zeph-lotto", "Zephyr Solo", "ZEPH", CoinGroup.LOTTO, "rx/0", MinerFamily.XMRIG, "eu2.solopool.org:8022"),
        Coin("xel-lotto", "Xelis Solo", "XEL", CoinGroup.LOTTO, "xelishash", MinerFamily.XMRIG, "eu3.solopool.org:8024"),
        Coin("etc-lotto", "Ethereum Classic Solo", "ETC", CoinGroup.LOTTO, "etchash", MinerFamily.GPU_ONLY, "eu1.solopool.org:8008"),
        Coin("ethw-lotto", "EthereumPoW Solo", "ETHW", CoinGroup.LOTTO, "etchash", MinerFamily.GPU_ONLY, "eu2.solopool.org:8018"),
        Coin("octa-lotto", "OctaSpace Solo", "OCTA", CoinGroup.LOTTO, "etchash", MinerFamily.GPU_ONLY, "eu3.solopool.org:8026"),
        Coin("kas-lotto", "Kaspa Solo", "KAS", CoinGroup.LOTTO, "kheavyhash", MinerFamily.GPU_ONLY, "eu1.solopool.org:8028"),
        Coin("erg-lotto", "Ergo Solo", "ERG", CoinGroup.LOTTO, "autolykos2", MinerFamily.GPU_ONLY, "eu2.solopool.org:8030"),
        Coin("rvn-lotto", "Ravencoin Solo", "RVN", CoinGroup.LOTTO, "kawpow", MinerFamily.GPU_ONLY, "eu1.solopool.org:8032"),

        // --- Unmineable (all RandomX, paid out in the chosen token) ---
        unmineable("shib", "Shiba Inu", "SHIB"),
        unmineable("ada", "Cardano", "ADA"),
        unmineable("sol", "Solana", "SOL"),
        unmineable("zec", "Zcash", "ZEC"),
        unmineable("etc", "Ethereum Classic", "ETC"),
        unmineable("rvn", "Ravencoin", "RVN"),
        unmineable("trx", "Tron", "TRX"),
        unmineable("vet", "VeChain", "VET"),
        unmineable("xrp", "XRP", "XRP"),
        unmineable("dot", "Polkadot", "DOT"),
        unmineable("matic", "Polygon", "MATIC"),
        unmineable("atom", "Cosmos", "ATOM"),
        unmineable("link", "Chainlink", "LINK"),
        unmineable("xlm", "Stellar", "XLM"),
        unmineable("algo", "Algorand", "ALGO"),
        unmineable("avax", "Avalanche", "AVAX"),
        unmineable("near", "NEAR Protocol", "NEAR"),
        unmineable("ftm", "Fantom", "FTM"),
        unmineable("one", "Harmony", "ONE"),

        // --- Blockchain PoW (toolchains that do not exist on Android) ---
        Coin("ore", "ORE", "ORE", CoinGroup.BLOCKCHAIN_POW, "drillx", MinerFamily.ORE, null,
            note = "Requires the Solana ore-cli toolchain and a funded keypair - Linux only."),
        Coin("ora", "Oranges", "ORA", CoinGroup.BLOCKCHAIN_POW, "algorand-tx", MinerFamily.ORA, null,
            note = "Requires a reachable Algorand node and the goal CLI - Linux only."),

        // --- Special ---
        Coin("tera", "TERA", "TERA", CoinGroup.SPECIAL, "-", MinerFamily.INFO_ONLY, null,
            note = "Informational entry - not mined through this app."),
        Coin("minima", "Minima", "MINIMA", CoinGroup.SPECIAL, "-", MinerFamily.INFO_ONLY, null,
            note = "Informational entry - not mined through this app."),
    )

    private val byId: Map<String, Coin> = all.associateBy { it.id }

    fun byId(id: String): Coin? = byId[id]

    fun grouped(): List<Pair<CoinGroup, List<Coin>>> =
        CoinGroup.entries.map { g -> g to all.filter { it.group == g } }.filter { it.second.isNotEmpty() }

    /** Why a coin cannot be mined here, for the picker badge. Null means it can. */
    fun unavailabilityReason(coin: Coin, availableFamilies: Set<MinerFamily>): String? = when (coin.family) {
        MinerFamily.GPU_ONLY -> "GPU-only algorithm - no Android miner exists"
        MinerFamily.ORE -> "Needs the Solana ore-cli toolchain - Linux only"
        MinerFamily.ORA -> "Needs an Algorand node and the goal CLI - Linux only"
        MinerFamily.INFO_ONLY -> "Informational entry - not mineable here"
        else -> if (coin.family in availableFamilies) null
        else "The ${coin.family.name.lowercase()} miner is not included in this build for your device"
    }

    fun isRunnable(coin: Coin, availableFamilies: Set<MinerFamily>): Boolean =
        unavailabilityReason(coin, availableFamilies) == null
}
