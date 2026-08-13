package com.frynetworks.pow.mining

data class MiningStats(
    val hashrate: Double? = null,
    val hashrateUnit: String = "H/s",
    val accepted: Long = 0,
    val rejected: Long = 0,
    val algo: String? = null,
    val pool: String? = null,
    val difficulty: String? = null,
    val uptimeSeconds: Long = 0,
    val coinId: String = "",
) {
    /** Linux panel parity: an empty share count reads as 100%, not 0%. */
    val efficiencyPercent: Int
        get() {
            val total = accepted + rejected
            return if (total <= 0L) 100 else ((accepted * 100L) / total).toInt()
        }

    val hashrateDisplay: String
        get() = hashrate?.let { String.format("%.2f %s", it, hashrateUnit) } ?: "--"
}

enum class ErrorKind { START_FAILED, CRASHED, FGS_BLOCKED, INVALID_CONFIG }

sealed interface MiningState {
    data object Idle : MiningState
    data object Starting : MiningState
    data class Mining(val stats: MiningStats) : MiningState
    data object Stopping : MiningState
    data class Error(val kind: ErrorKind, val detail: String) : MiningState
    data class Unsupported(val reason: String) : MiningState
}

/** What the session thread reports back to the controller. */
sealed interface SessionEvent {
    data object Started : SessionEvent
    data class Stats(val stats: MiningStats) : SessionEvent
    data class Exited(val exitCode: Int, val tail: List<String>) : SessionEvent
    data class Failed(val reason: String) : SessionEvent
}
