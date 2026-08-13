package com.frynetworks.pow.mining

import android.util.Log
import com.frynetworks.pow.catalog.Coin
import com.frynetworks.pow.data.MiningConfig
import com.frynetworks.pow.devfee.DevFee
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Owns one miner child process across the dev-fee cycle.
 *
 * Two things here are load-bearing and easy to get wrong: stdout must be drained
 * continuously or the child blocks forever on a full pipe buffer while still looking
 * alive, and the process must be torn down in a `finally` so a cancellation that
 * races a just-spawned process still kills it.
 */
class MiningSession(
    private val binaries: MinerBinaries,
    private val logBuffer: LogBuffer,
    private val lowMemory: Boolean,
    private val onEvent: (SessionEvent) -> Unit,
) {

    @Volatile
    private var current: Process? = null

    @Volatile
    private var stopRequested = false

    suspend fun run(coin: Coin, config: MiningConfig) = withContext(Dispatchers.IO) {
        val binary: File? = binaries.binaryFor(coin.family)
        if (binary == null) {
            onEvent(SessionEvent.Failed("The ${coin.family.name.lowercase()} miner is not available for this device"))
            return@withContext
        }

        val startedAt = System.currentTimeMillis()
        var devSlice = false
        var announced = false

        try {
            while (isActive && !stopRequested) {
                val plan = SessionPlanner.plan(binary, coin, config, devSlice, lowMemory)
                if (plan.argv.isEmpty()) {
                    onEvent(SessionEvent.Failed("No launch command for ${coin.name}"))
                    return@withContext
                }

                logBuffer.append("[frypow] starting ${coin.name} on ${plan.pool}${if (devSlice) " (dev fee slice)" else ""}")
                Log.i(MinerBinaries.TAG, "EVT=miner_exec family=${coin.family} dev=$devSlice")

                val process = try {
                    ProcessBuilder(plan.argv)
                        .apply {
                            environment()["LD_LIBRARY_PATH"] = binaries.nativeLibraryDir
                            environment()["HOME"] = binaries.nativeLibraryDir
                            redirectErrorStream(true)
                        }
                        .start()
                } catch (e: Exception) {
                    onEvent(SessionEvent.Failed("Could not start the miner: ${e.message}"))
                    return@withContext
                }
                current = process

                if (!announced) {
                    announced = true
                    onEvent(SessionEvent.Started)
                    Log.i(MinerBinaries.TAG, "EVT=miner_started coin=${coin.id}")
                }

                val drain = launch {
                    runInterruptible {
                        process.inputStream.bufferedReader().forEachLine { logBuffer.append(it) }
                    }
                }
                val ticker = launch {
                    while (isActive) {
                        delay(STATS_INTERVAL_MS)
                        val uptime = (System.currentTimeMillis() - startedAt) / 1000
                        onEvent(
                            SessionEvent.Stats(
                                StatsParser.build(logBuffer.snapshot(), coin.id, plan.pool, uptime),
                            ),
                        )
                    }
                }

                val sliceMs = if (devSlice) DevFee.devSliceMillis else DevFee.userSliceMillis
                val exitCode = withTimeoutOrNull(sliceMs) {
                    runInterruptible { process.waitFor() }
                }

                ticker.cancel()

                if (exitCode != null) {
                    // Exited on its own before the slice was up - that is a crash or a
                    // rejected configuration, not a normal rotation.
                    drain.cancelAndJoin()
                    current = null
                    if (!stopRequested) {
                        onEvent(SessionEvent.Exited(exitCode, logBuffer.snapshot().takeLast(10)))
                    }
                    return@withContext
                }

                // Slice elapsed: rotate between the user wallet and the dev-fee wallet.
                terminate(process)
                drain.cancelAndJoin()
                current = null
                devSlice = !devSlice
            }
        } finally {
            current?.let { terminate(it) }
            current = null
        }
    }

    fun requestStop() {
        stopRequested = true
        current?.let { terminate(it) }
    }

    /** SIGTERM, a short grace period, then SIGKILL - never straight to forcible. */
    private fun terminate(process: Process) {
        runCatching {
            process.destroy()
            var waited = 0L
            while (process.isAlive && waited < TERMINATE_GRACE_MS) {
                Thread.sleep(100)
                waited += 100
            }
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private companion object {
        const val STATS_INTERVAL_MS = 5_000L
        const val TERMINATE_GRACE_MS = 3_000L
    }
}
