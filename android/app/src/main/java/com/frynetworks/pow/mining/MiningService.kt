package com.frynetworks.pow.mining

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.frynetworks.pow.FryPowApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MiningService : Service() {

    private val container by lazy { (application as FryPowApp).container }
    private var wakeLock: PowerManager.WakeLock? = null
    private var stateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        MinerNotification.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground() must happen before ANY other path through this method,
        // including the early returns below. A service started with
        // startForegroundService() that exits without it is killed outright with
        // "did not then call Service.startForeground()".
        promoteToForeground("FryPoW", "Preparing to mine")

        when (intent?.action) {
            ACTION_STOP -> {
                container.miningController.stop()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> resumeOrStart()
        }

        acquireWakeLock()
        observeState()
        return START_STICKY
    }

    private fun resumeOrStart() {
        container.scope.launch {
            if (container.miningController.isBusy) return@launch
            val config = container.configRepository.config.first()
            container.miningController.start(config)
        }
    }

    /**
     * Mirrors mining state into the notification, and retires the service once a
     * session ends.
     *
     * `wasActive` matters: the controller's initial value is Idle, so without it the
     * very first emission would tear the service down before start() had a chance to
     * move to Starting.
     */
    private fun observeState() {
        stateJob?.cancel()
        stateJob = container.scope.launch {
            var wasActive = false
            container.miningController.state.collect { state ->
                when (state) {
                    is MiningState.Starting -> wasActive = true

                    is MiningState.Mining -> {
                        wasActive = true
                        val stats = state.stats
                        promoteToForeground(
                            "FryPoW - mining ${stats.coinId.uppercase()}",
                            "${stats.hashrateDisplay}  |  ${stats.pool ?: "connecting"}",
                        )
                    }

                    is MiningState.Idle, is MiningState.Error, is MiningState.Unsupported -> {
                        if (wasActive) {
                            wasActive = false
                            stopForegroundCompat()
                            stopSelf()
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun promoteToForeground(title: String, text: String) {
        val notification = MinerNotification.build(this, title, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                MinerNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(MinerNotification.NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FryPoW::Mining").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(MinerBinaries.TAG, "EVT=task_removed mining_continues")
    }

    override fun onDestroy() {
        stateJob?.cancel()
        stateJob = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        container.miningController.stop()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.frynetworks.pow.action.START"
        const val ACTION_STOP = "com.frynetworks.pow.action.STOP"
        private const val WAKE_LOCK_TIMEOUT_MS = 35L * 60 * 1000
    }
}
