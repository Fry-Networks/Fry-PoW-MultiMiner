package com.frynetworks.pow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.frynetworks.pow.catalog.CoinCatalog
import com.frynetworks.pow.data.MiningConfig
import com.frynetworks.pow.mining.MiningState
import com.frynetworks.pow.ui.components.FocusableCard
import com.frynetworks.pow.ui.components.StatTile
import com.frynetworks.pow.ui.theme.Crimson
import com.frynetworks.pow.ui.theme.Danger
import com.frynetworks.pow.ui.theme.Muted
import com.frynetworks.pow.ui.theme.Salmon
import com.frynetworks.pow.ui.theme.Success
import com.frynetworks.pow.ui.theme.Warning

@Composable
fun DashboardScreen(
    state: MiningState,
    config: MiningConfig,
    isTv: Boolean,
    webUiUrl: String?,
    webServerEnabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val stats = (state as? MiningState.Mining)?.stats
    val coin = CoinCatalog.byId(config.coinId)

    val statusWord = when (state) {
        is MiningState.Idle -> "Stopped"
        is MiningState.Starting -> "Starting"
        is MiningState.Mining -> "Mining"
        is MiningState.Stopping -> "Stopping"
        is MiningState.Error -> "Error"
        is MiningState.Unsupported -> "Unsupported"
    }
    val statusColor = when (state) {
        is MiningState.Mining -> Success
        is MiningState.Error -> Danger
        is MiningState.Unsupported -> Warning
        else -> Muted
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isTv) 32.dp else 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("FryPoW", style = MaterialTheme.typography.displaySmall, color = Crimson)

        val running = state is MiningState.Mining || state is MiningState.Starting
        Button(
            onClick = { if (running) onStop() else onStart() },
            enabled = state !is MiningState.Unsupported,
            colors = ButtonDefaults.buttonColors(containerColor = if (running) Danger else Crimson),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .testTag(if (running) "btn_stop" else "btn_start"),
        ) {
            Text(if (running) "Stop Mining" else "Start Mining", style = MaterialTheme.typography.titleMedium)
        }

        FocusableCard(testTag = "card_status") {
            Text("Status", style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(statusWord, style = MaterialTheme.typography.headlineSmall, color = statusColor,
                modifier = Modifier.testTag("txt_status"))
            Text(
                coin?.let { "${it.name} (${it.ticker}) - ${it.algo}" } ?: "No coin selected",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("txt_coin"),
            )
            when (state) {
                is MiningState.Error -> Text(
                    state.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Danger,
                    modifier = Modifier.padding(top = 6.dp).testTag("txt_error"),
                )
                is MiningState.Unsupported -> Text(
                    state.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = Warning,
                    modifier = Modifier.padding(top = 6.dp).testTag("txt_unsupported"),
                )
                else -> Unit
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Hashrate", stats?.hashrateDisplay ?: "--", "txt_hashrate", Modifier.weight(1f))
            StatTile("Accepted", (stats?.accepted ?: 0).toString(), "txt_accepted", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Uptime", formatUptime(stats?.uptimeSeconds ?: 0), "txt_uptime", Modifier.weight(1f))
            StatTile("Efficiency", "${stats?.efficiencyPercent ?: 100}%", "txt_efficiency", Modifier.weight(1f))
        }

        FocusableCard(testTag = "card_session") {
            Text("Session", style = MaterialTheme.typography.labelSmall, color = Muted)
            Text("Algorithm: ${stats?.algo ?: coin?.algo ?: "--"}", style = MaterialTheme.typography.bodyMedium)
            Text("Pool: ${stats?.pool ?: config.pool.ifBlank { coin?.defaultPool ?: "--" }}",
                style = MaterialTheme.typography.bodyMedium)
            Text("Difficulty: ${stats?.difficulty ?: "--"}", style = MaterialTheme.typography.bodyMedium)
            Text("Rejected: ${stats?.rejected ?: 0}", style = MaterialTheme.typography.bodyMedium)
        }

        FocusableCard(testTag = "card_webui") {
            Text("Web Dashboard", style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(
                if (webServerEnabled) "Web Dashboard: Active" else "Web Dashboard: Off",
                style = MaterialTheme.typography.bodyMedium,
                color = if (webServerEnabled) Success else Muted,
                modifier = Modifier.testTag("txt_webui_state"),
            )
            if (webServerEnabled && webUiUrl != null) {
                Text(
                    webUiUrl,
                    style = MaterialTheme.typography.titleMedium,
                    color = Salmon,
                    modifier = Modifier.testTag("txt_webui_url"),
                )
            }
        }
    }
}

private fun formatUptime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return "${h}h ${m}m"
}
