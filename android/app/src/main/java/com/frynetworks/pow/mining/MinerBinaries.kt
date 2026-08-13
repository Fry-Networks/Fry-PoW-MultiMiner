package com.frynetworks.pow.mining

import android.content.Context
import android.util.Log
import com.frynetworks.pow.catalog.MinerFamily
import java.io.File

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

    val availableFamilies: Set<MinerFamily> by lazy {
        fileNames.keys.filter { binaryFor(it) != null }.toSet().also {
            Log.i(TAG, "EVT=miner_binaries available=${it.joinToString(",")} dir=$nativeDir")
        }
    }

    val anyAvailable: Boolean get() = availableFamilies.isNotEmpty()

    val nativeLibraryDir: String get() = nativeDir.absolutePath

    companion object {
        const val TAG = "FryPoWQA"
    }
}
