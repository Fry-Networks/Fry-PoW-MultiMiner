package com.frynetworks.pow.mining

import android.content.Context
import android.util.Log
import com.frynetworks.pow.catalog.MinerFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Locates the bundled miner executables.
 *
 * They ship as `lib*.so` inside jniLibs so the package manager extracts them into
 * nativeLibraryDir, which is the only app-owned directory allowed to hold executable
 * files from Android 10 onward. A family whose binary is missing for this device's
 * ABI is simply reported unavailable - the app stays fully usable, which is what
 * keeps a 32-bit TV box from turning a missing slice into a crash.
 */
class MinerBinaries(context: Context) {

    private val nativeDir: File = File(context.applicationInfo.nativeLibraryDir)

    private val fileNames = mapOf(
        MinerFamily.XMRIG to "libxmrig.so",
        MinerFamily.XLARIG to "libxlarig.so",
        MinerFamily.CPUMINER to "libcpuminer.so",
        MinerFamily.VERUS to "libverus.so",
    )

    fun binaryFor(family: MinerFamily): File? {
        val name = fileNames[family] ?: return null
        val f = File(nativeDir, name)
        return if (f.exists() && f.canExecute()) f else null
    }

    private val verified = mutableMapOf<MinerFamily, Boolean>()

    /**
     * Proves a binary actually runs, rather than merely existing.
     *
     * A miner built for the wrong ABI variant is present and executable but dies on
     * its first instruction (SIGBUS), which the file check above cannot see - the
     * session would spawn it and it would fail in a loop. Every bundled miner exits 0
     * for `--version`, so that is a valid health probe for all four families.
     *
     * Runs off the main thread and caches per family, so a start/stop cycle does not
     * re-probe.
     */
    suspend fun verifyExecutable(family: MinerFamily): Boolean {
        verified[family]?.let { return it }
        val binary = binaryFor(family) ?: return false
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val process = ProcessBuilder(binary.absolutePath, "--version")
                    .apply {
                        environment()["LD_LIBRARY_PATH"] = nativeDir.absolutePath
                        redirectErrorStream(true)
                    }
                    .start()
                try {
                    process.inputStream.use { it.readBytes() }
                    val exited = process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    exited && process.exitValue() == 0
                } finally {
                    if (process.isAlive) process.destroyForcibly()
                }
            }.getOrDefault(false)
        }
        verified[family] = ok
        Log.i(TAG, "EVT=miner_verify family=$family runnable=$ok")
        return ok
    }

    val availableFamilies: Set<MinerFamily> by lazy {
        fileNames.keys.filter { binaryFor(it) != null }.toSet().also {
            Log.i(TAG, "EVT=miner_binaries available=${it.joinToString(",")} dir=$nativeDir")
        }
    }

    val anyAvailable: Boolean get() = availableFamilies.isNotEmpty()

    val nativeLibraryDir: String get() = nativeDir.absolutePath

    companion object {
        const val TAG = "FryPoWQA"
        private const val PROBE_TIMEOUT_SECONDS = 10L
    }
}
