package com.frynetworks.pow.mining

/**
 * Port of the Linux `stats.cgi` log scraping. This is the only stats mechanism the
 * non-XMRig miners have, so it stays the primary path rather than a fallback.
 * Pure functions only - these are the unit-tested core.
 */
object StatsParser {

    private val ANSI = Regex("""\[[0-9;]*m""")

    // XMRig: "speed 10s/60s/15m 218.2 220.6 n/a H/s"
    private val XMRIG_SPEED = Regex("""speed\s+\S+\s+([0-9]+\.?[0-9]*)\s+\S+\s+\S+\s+([kKMGT]?H/s)""")

    // Generic trailing rate, e.g. cpuminer "4.52 khash/s" or "123.4 kH/s"
    private val GENERIC_RATE = Regex("""([0-9]+\.?[0-9]*)\s*([kKMGT]?)(?:H/s|hash/s|H/S)""")

    // "accepted: 66208/66506"
    private val ACCEPTED_PAIR = Regex("""accepted:\s*([0-9]+)\s*/\s*([0-9]+)""")
    private val ACCEPTED_ONE = Regex("""accepted:\s*([0-9]+)""")

    private val ALGO_LABEL = Regex("""[Aa]lgorithm:\s*([A-Za-z0-9/_.-]+)""")
    private val ALGO_FLAG = Regex("""algo\s+([A-Za-z0-9/_.-]+)""")
    private val DIFF_SET = Regex("""difficulty set to\s*([0-9]+)""")
    private val DIFF_SHORT = Regex("""diff\s+([0-9]+)""")

    fun strip(line: String): String = ANSI.replace(line, "")

    fun parseHashrate(log: List<String>): Pair<Double, String>? {
        for (raw in log.asReversed()) {
            val line = strip(raw)
            XMRIG_SPEED.find(line)?.let { m ->
                val value = m.groupValues[1].toDoubleOrNull() ?: return@let
                return value to m.groupValues[2]
            }
        }
        for (raw in log.asReversed()) {
            val line = strip(raw)
            GENERIC_RATE.find(line)?.let { m ->
                val value = m.groupValues[1].toDoubleOrNull() ?: return@let
                val unit = m.groupValues[2].uppercase().ifEmpty { "" } + "H/s"
                return value to unit
            }
        }
        return null
    }

    /** Returns accepted to rejected. The `X/Y` form means Y total, so rejected is Y-X. */
    fun parseShares(log: List<String>): Pair<Long, Long> {
        for (raw in log.asReversed()) {
            val line = strip(raw)
            ACCEPTED_PAIR.find(line)?.let { m ->
                val good = m.groupValues[1].toLongOrNull() ?: 0L
                val total = m.groupValues[2].toLongOrNull() ?: good
                return good to (total - good).coerceAtLeast(0L)
            }
        }
        for (raw in log.asReversed()) {
            ACCEPTED_ONE.find(strip(raw))?.let { m ->
                return (m.groupValues[1].toLongOrNull() ?: 0L) to 0L
            }
        }
        val accepted = log.count { it.contains("accepted", true) || it.contains("yay!", true) }.toLong()
        val rejected = log.count { it.contains("reject", true) || it.contains("booo", true) }.toLong()
        return accepted to rejected
    }

    fun parseAlgo(log: List<String>): String? {
        for (raw in log.asReversed()) {
            val line = strip(raw)
            ALGO_LABEL.find(line)?.let { return it.groupValues[1] }
            ALGO_FLAG.find(line)?.let { return it.groupValues[1] }
        }
        val joined = log.joinToString(" ")
        return when {
            joined.contains("Verus Hashing", true) || joined.contains("verushash", true) -> "verushash"
            joined.contains("panthera", true) -> "panthera"
            joined.contains("RandomX", true) -> "rx/0"
            else -> null
        }
    }

    fun parseDifficulty(log: List<String>): String? {
        for (raw in log.asReversed()) {
            val line = strip(raw)
            DIFF_SET.find(line)?.let { return it.groupValues[1] }
            DIFF_SHORT.find(line)?.let { return it.groupValues[1] }
        }
        return null
    }

    /** Distinguishes a crash from a clean stop, replacing the shell's log-tail grep. */
    fun looksLikeCrash(log: List<String>): Boolean {
        val tail = log.takeLast(10).joinToString(" ").lowercase()
        return listOf("segmentation fault", "core dumped", "fatal error", "aborted")
            .any { tail.contains(it) }
    }

    fun build(log: List<String>, coinId: String, pool: String, uptimeSeconds: Long): MiningStats {
        val rate = parseHashrate(log)
        val (accepted, rejected) = parseShares(log)
        return MiningStats(
            hashrate = rate?.first,
            hashrateUnit = rate?.second ?: "H/s",
            accepted = accepted,
            rejected = rejected,
            algo = parseAlgo(log),
            pool = pool,
            difficulty = parseDifficulty(log),
            uptimeSeconds = uptimeSeconds,
            coinId = coinId,
        )
    }
}
