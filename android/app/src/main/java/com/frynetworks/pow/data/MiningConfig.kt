package com.frynetworks.pow.data

/**
 * The subset of the Linux `/opt/frynet-config/config.txt` keys that can exist on
 * Android. Dropped keys and why:
 *   cpu_mining              - CPU is the only engine here, so a false value is unrepresentable
 *   gpu_mining, gpu_miner   - SRBMiner/lolMiner/T-Rex are desktop x86-64 only
 *   usbasic_*               - bfgminer/cgminer need USB host access and root
 *   ore_*                   - needs the Solana ore-cli toolchain and a funded keypair on disk
 *   ora_*                   - needs a reachable Algorand node plus goal
 */
data class MiningConfig(
    val coinId: String = "",
    val wallet: String = "",
    val dogeWallet: String = "",
    val ltcWallet: String = "",
    val worker: String = "worker1",
    val threads: Int = 2,
    val pool: String = "",
    val password: String = "x",
    val startOnBoot: Boolean = false,
)
