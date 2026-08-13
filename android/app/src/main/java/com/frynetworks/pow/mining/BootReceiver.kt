package com.frynetworks.pow.mining

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Optional auto-start. Off by default: starting a foreground service from a boot
 * broadcast is refused outright on some versions, so the failure is caught and the
 * user simply starts mining from the app instead.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            val start = Intent(context, MiningService::class.java)
                .setAction(MiningService.ACTION_START)
            context.startForegroundService(start)
            Log.i(MinerBinaries.TAG, "EVT=boot_autostart_requested")
        } catch (e: Exception) {
            Log.w(MinerBinaries.TAG, "EVT=boot_autostart_refused reason=${e.javaClass.simpleName}")
        }
    }
}
