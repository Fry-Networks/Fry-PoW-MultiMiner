package com.frynetworks.pow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frynetworks.pow.mining.MiningService
import com.frynetworks.pow.ui.screens.AboutScreen
import com.frynetworks.pow.ui.screens.CoinPickerScreen
import com.frynetworks.pow.ui.screens.ConfigureScreen
import com.frynetworks.pow.ui.screens.DashboardScreen
import com.frynetworks.pow.ui.screens.LogScreen
import com.frynetworks.pow.ui.theme.FryPoWTheme
import kotlinx.coroutines.launch

private enum class Dest(val tag: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD("nav_dashboard", "Dashboard", Icons.Filled.Home),
    CONFIGURE("nav_configure", "Configure", Icons.Filled.Settings),
    LOG("nav_log", "Log", Icons.Filled.List),
    ABOUT("nav_about", "About", Icons.Filled.Info),
}

class MainActivity : ComponentActivity() {

    private val app by lazy { application as FryPowApp }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = app.container

        setContent {
            FryPoWTheme {
                var dest by remember { mutableStateOf(Dest.DASHBOARD) }
                var pickingCoin by remember { mutableStateOf(false) }

                val state by container.miningController.state.collectAsStateWithLifecycle()
                val config by container.configRepository.config.collectAsStateWithLifecycle(
                    initialValue = com.frynetworks.pow.data.MiningConfig(),
                )
                val logLines by container.logBuffer.state.collectAsStateWithLifecycle()
                val scope = rememberScope()

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true },
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (pickingCoin) {
                        CoinPickerScreen(
                            availableFamilies = container.binaries.availableFamilies,
                            onPick = { coin ->
                                scope.launch {
                                    container.configRepository.save(
                                        config.copy(
                                            coinId = coin.id,
                                            pool = coin.defaultPool.orEmpty(),
                                        ),
                                    )
                                }
                                pickingCoin = false
                            },
                            onBack = { pickingCoin = false },
                        )
                        return@Surface
                    }

                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                Dest.entries.forEach { d ->
                                    NavigationBarItem(
                                        selected = dest == d,
                                        onClick = { dest = d },
                                        icon = { Icon(d.icon, contentDescription = d.label) },
                                        label = { Text(d.label) },
                                        modifier = Modifier.testTag(d.tag),
                                    )
                                }
                            }
                        },
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            when (dest) {
                                Dest.DASHBOARD -> DashboardScreen(
                                    state = state,
                                    config = config,
                                    isTv = container.isTv,
                                    onStart = {
                                        requestNotificationPermissionIfNeeded()
                                        startService(MiningService.ACTION_START)
                                    },
                                    onStop = { startService(MiningService.ACTION_STOP) },
                                )
                                Dest.CONFIGURE -> ConfigureScreen(
                                    config = config,
                                    maxThreads = container.cpuCount,
                                    availableFamilies = container.binaries.availableFamilies,
                                    onOpenPicker = { pickingCoin = true },
                                    onSave = { updated -> scope.launch { container.configRepository.save(updated) } },
                                )
                                Dest.LOG -> LogScreen(
                                    lines = logLines,
                                    onClear = { container.logBuffer.clear() },
                                )
                                Dest.ABOUT -> AboutScreen(
                                    repository = container.updateRepository,
                                    isTv = container.isTv,
                                    availableFamilies = container.binaries.availableFamilies,
                                    lowMemory = container.lowMemory,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startService(action: String) {
        val intent = Intent(this, MiningService::class.java).setAction(action)
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            app.container.miningController.reportForegroundBlocked()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        // Asked at the first Start press rather than on launch: denial only costs the
        // shade entry, it never blocks mining.
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun rememberScope() = androidx.compose.runtime.rememberCoroutineScope()
