package com.frynetworks.pow

import android.app.Application
import android.util.Log
import com.frynetworks.pow.di.AppContainer
import com.frynetworks.pow.mining.MinerBinaries

class FryPowApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Log.i(
            MinerBinaries.TAG,
            "EVT=app_start tv=${container.isTv} cpus=${container.cpuCount} " +
                "lowMem=${container.lowMemory} miners=${container.binaries.availableFamilies.joinToString("|")}",
        )
    }
}
