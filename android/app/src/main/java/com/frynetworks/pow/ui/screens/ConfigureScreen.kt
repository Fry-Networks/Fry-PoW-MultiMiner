package com.frynetworks.pow.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.frynetworks.pow.catalog.CoinCatalog
import com.frynetworks.pow.catalog.CoinGroup
import com.frynetworks.pow.catalog.MinerFamily
import com.frynetworks.pow.data.ConfigError
import com.frynetworks.pow.data.ConfigValidator
import com.frynetworks.pow.data.MiningConfig
import com.frynetworks.pow.ui.components.FocusableCard
import com.frynetworks.pow.ui.components.FormField
import com.frynetworks.pow.ui.components.StepperRow
import com.frynetworks.pow.ui.theme.Muted
import com.frynetworks.pow.ui.theme.Success

@Composable
fun ConfigureScreen(
    config: MiningConfig,
    maxThreads: Int,
    availableFamilies: Set<MinerFamily>,
    onOpenPicker: () -> Unit,
    onSave: (MiningConfig) -> Unit,
) {
    var draft by remember(config) { mutableStateOf(config) }
    var errors by remember { mutableStateOf<List<ConfigError>>(emptyList()) }
    var saved by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val coin = CoinCatalog.byId(draft.coinId)

    LaunchedEffect(draft) { saved = false }

    fun errorFor(field: ConfigError.Field) = errors.firstOrNull { it.field == field }?.message

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Configure", style = MaterialTheme.typography.headlineSmall)

        FocusableCard(testTag = "card_coin") {
            Text("Coin", style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(
                coin?.let { "${it.name} (${it.ticker})" } ?: "Not selected",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("txt_selected_coin"),
            )
            coin?.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
            TextButton(onClick = onOpenPicker, modifier = Modifier.testTag("btn_pick_coin")) {
                Text("Choose coin")
            }
            errorFor(ConfigError.Field.COIN)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        FormField(
            label = "Wallet address",
            value = draft.wallet,
            onValueChange = { draft = draft.copy(wallet = it) },
            testTag = "cfg_wallet",
            error = errorFor(ConfigError.Field.WALLET),
            trailing = {
                TextButton(
                    onClick = {
                        clipboard.getText()?.text?.let { draft = draft.copy(wallet = it.trim()) }
                    },
                    modifier = Modifier.testTag("btn_paste_wallet"),
                ) { Text("Paste") }
            },
        )
        Text(
            "Typing a long address on a remote is slow - paste it from the clipboard or use a phone remote app.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )

        if (draft.coinId == "ltc-lotto") {
            FormField("DOGE address (merged mining, optional)", draft.dogeWallet,
                { draft = draft.copy(dogeWallet = it) }, "cfg_doge_wallet")
        }
        if (draft.coinId == "doge-lotto") {
            FormField("LTC address (merged mining, optional)", draft.ltcWallet,
                { draft = draft.copy(ltcWallet = it) }, "cfg_ltc_wallet")
        }

        FormField("Worker name", draft.worker, { draft = draft.copy(worker = it) }, "cfg_worker",
            error = errorFor(ConfigError.Field.WORKER))

        StepperRow(
            label = "CPU threads",
            value = draft.threads,
            range = 1..maxThreads,
            onChange = { draft = draft.copy(threads = it) },
            testTag = "cfg_threads",
        )
        errorFor(ConfigError.Field.THREADS)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        val poolLocked = coin?.poolLocked == true
        FormField(
            label = if (poolLocked) "Pool (fixed for this coin)" else "Pool",
            value = draft.pool,
            onValueChange = { draft = draft.copy(pool = it) },
            testTag = "cfg_pool",
            enabled = !poolLocked,
            error = errorFor(ConfigError.Field.POOL),
        )

        FormField("Pool password", draft.password, { draft = draft.copy(password = it) }, "cfg_password")

        Button(
            onClick = {
                val found = ConfigValidator.validate(draft, maxThreads)
                errors = found
                if (found.isEmpty()) {
                    onSave(draft)
                    saved = true
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("cfg_save"),
        ) { Text("Save configuration") }

        if (saved) {
            Text("Configuration saved", color = Success, modifier = Modifier.testTag("txt_saved"))
        }
        if (errors.isNotEmpty()) {
            Text(
                errors.first().message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("err_config"),
            )
        }

        if (coin?.group == CoinGroup.UNMINEABLE) {
            Text(
                "Unmineable pays out in ${coin.ticker} while mining RandomX. The project referral code is appended.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Text(
            "MystNodes bandwidth sharing is not available on Android yet.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(top = 16.dp).testTag("txt_myst"),
        )
    }
}
