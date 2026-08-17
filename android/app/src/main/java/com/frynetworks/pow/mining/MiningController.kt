package com.frynetworks.pow.mining

import android.util.Log
import com.frynetworks.pow.catalog.CoinCatalog
import com.frynetworks.pow.data.ConfigRepository
import com.frynetworks.pow.data.ConfigValidator
import com.frynetworks.pow.data.MiningConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single owner of mining state. Lives in the app container rather than in service
 * static fields so it can be exercised without a device.
 */
class MiningController(
    private val binaries: MinerBinaries,
    private val configRepository: ConfigRepository,
    private val logBuffer: LogBuffer,
    private val lowMemory: Boolean,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<MiningState>(
        if (binaries.anyAvailable) MiningState.Idle
        else MiningState.Unsupported("No miner binary is bundled for this device's CPU architecture"),
    )
    val state: StateFlow<MiningState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var job: Job? = null
    private var session: MiningSession? = null

    val isBusy: Boolean
        get() = _state.value is MiningState.Mining || _state.value is MiningState.Starting

    suspend fun start(config: MiningConfig): Boolean = mutex.withLock {
        if (isBusy) {
            Log.i(MinerBinaries.TAG, "EVT=start_ignored reason=already_active")
            return@withLock true
        }

        val coin = CoinCatalog.byId(config.coinId)
        if (coin == null) {
            _state.value = MiningState.Error(ErrorKind.INVALID_CONFIG, "Choose a coin before starting")
            return@withLock false
        }
        val errors = ConfigValidator.validate(config)
        if (errors.isNotEmpty()) {
            _state.value = MiningState.Error(ErrorKind.INVALID_CONFIG, errors.first().message)
            Log.i(MinerBinaries.TAG, "EVT=config_invalid detail=${errors.first().message}")
            return@withLock false
        }
        if (binaries.binaryFor(coin.family) == null) {
            _state.value = MiningState.Unsupported(
                CoinCatalog.unavailabilityReason(coin, binaries.availableFamilies)
                    ?: "This coin cannot be mined on this device",
            )
            return@withLock false
        }
        if (!binaries.verifyExecutable(coin.family)) {
            _state.value = MiningState.Unsupported(
                "The ${coin.family.name.lowercase()} miner is bundled but will not run on this " +
                    "device's CPU. Choose a coin that uses a different miner.",
            )
            return@withLock false
        }

        _state.value = MiningState.Starting
        val newSession = MiningSession(binaries, logBuffer, lowMemory, ::onEvent)
        session = newSession
        job = scope.launch { newSession.run(coin, config) }
        configRepository.setMiningRequested(true)
        true
    }

    fun stop() {
        if (_state.value is MiningState.Idle) return
        _state.value = MiningState.Stopping
        session?.requestStop()
        job?.cancel()
        job = null
        session = null
        _state.value = MiningState.Idle
        scope.launch { configRepository.setMiningRequested(false) }
        Log.i(MinerBinaries.TAG, "EVT=miner_stopped")
    }

    private fun onEvent(event: SessionEvent) {
        when (event) {
            is SessionEvent.Started ->
                _state.value = MiningState.Mining(MiningStats())

            is SessionEvent.Stats ->
                _state.value = MiningState.Mining(event.stats)

            is SessionEvent.Exited -> {
                _state.value = MiningState.Error(
                    ErrorKind.CRASHED,
                    "The miner exited with code ${event.exitCode}. " +
                        event.tail.lastOrNull().orEmpty(),
                )
                scope.launch { configRepository.setMiningRequested(false) }
                Log.w(MinerBinaries.TAG, "EVT=miner_exited code=${event.exitCode}")
            }

            is SessionEvent.Failed -> {
                _state.value = MiningState.Error(ErrorKind.START_FAILED, event.reason)
                scope.launch { configRepository.setMiningRequested(false) }
                Log.w(MinerBinaries.TAG, "EVT=miner_start_failed reason=${event.reason}")
            }
        }
    }

    fun reportForegroundBlocked() {
        _state.value = MiningState.Error(
            ErrorKind.FGS_BLOCKED,
            "Android would not let mining start in the background. Open FryPoW and press Start.",
        )
    }
}
