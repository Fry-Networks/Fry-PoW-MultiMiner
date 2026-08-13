package com.frynetworks.pow.di

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import com.frynetworks.pow.data.ConfigRepository
import com.frynetworks.pow.mining.LogBuffer
import com.frynetworks.pow.mining.MinerBinaries
import com.frynetworks.pow.mining.MiningController
import com.frynetworks.pow.mining.SessionPlanner
import com.frynetworks.pow.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-built dependency graph. The app has one repository, one controller and one
 * service, so an annotation processor would cost build time and buy nothing.
 */
class AppContainer(context: Context) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val configRepository = ConfigRepository(context)
    val logBuffer = LogBuffer()
    val binaries = MinerBinaries(context)
    val updateRepository = UpdateRepository()

    /** TV boxes report leanback; window size class would misread a landscape phone. */
    val isTv: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            context.packageManager.hasSystemFeature("android.hardware.type.television")

    val cpuCount: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    val totalMemoryBytes: Long = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        info.totalMem
    }.getOrDefault(0L)

    val lowMemory: Boolean =
        totalMemoryBytes in 1 until SessionPlanner.LOW_MEMORY_THRESHOLD_BYTES

    val miningController = MiningController(
        binaries = binaries,
        configRepository = configRepository,
        logBuffer = logBuffer,
        lowMemory = lowMemory,
        scope = scope,
    )
}
