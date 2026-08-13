package com.frynetworks.pow.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "frypow_config")

/**
 * Keys are only ever added, and every read supplies a default, so an older store
 * always deserialises without a migration step.
 */
class ConfigRepository(context: Context) {

    private val store = context.dataStore

    private object Keys {
        val COIN = stringPreferencesKey("coin_id")
        val WALLET = stringPreferencesKey("wallet")
        val DOGE_WALLET = stringPreferencesKey("doge_wallet")
        val LTC_WALLET = stringPreferencesKey("ltc_wallet")
        val WORKER = stringPreferencesKey("worker")
        val THREADS = intPreferencesKey("threads")
        val POOL = stringPreferencesKey("pool")
        val PASSWORD = stringPreferencesKey("password")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val MINING_REQUESTED = booleanPreferencesKey("mining_requested")
    }

    private val defaults = MiningConfig()

    val config: Flow<MiningConfig> = store.data.map { p ->
        MiningConfig(
            coinId = p[Keys.COIN] ?: defaults.coinId,
            wallet = p[Keys.WALLET] ?: defaults.wallet,
            dogeWallet = p[Keys.DOGE_WALLET] ?: defaults.dogeWallet,
            ltcWallet = p[Keys.LTC_WALLET] ?: defaults.ltcWallet,
            worker = p[Keys.WORKER] ?: defaults.worker,
            threads = p[Keys.THREADS] ?: defaults.threads,
            pool = p[Keys.POOL] ?: defaults.pool,
            password = p[Keys.PASSWORD] ?: defaults.password,
            startOnBoot = p[Keys.START_ON_BOOT] ?: defaults.startOnBoot,
        )
    }

    /** Survives process death so a sticky service restart knows whether to resume. */
    val miningRequested: Flow<Boolean> = store.data.map { it[Keys.MINING_REQUESTED] ?: false }

    suspend fun save(config: MiningConfig) {
        store.edit { p ->
            p[Keys.COIN] = config.coinId
            p[Keys.WALLET] = config.wallet
            p[Keys.DOGE_WALLET] = config.dogeWallet
            p[Keys.LTC_WALLET] = config.ltcWallet
            p[Keys.WORKER] = config.worker
            p[Keys.THREADS] = config.threads
            p[Keys.POOL] = config.pool
            p[Keys.PASSWORD] = config.password
            p[Keys.START_ON_BOOT] = config.startOnBoot
        }
    }

    suspend fun setMiningRequested(requested: Boolean) {
        store.edit { it[Keys.MINING_REQUESTED] = requested }
    }
}
