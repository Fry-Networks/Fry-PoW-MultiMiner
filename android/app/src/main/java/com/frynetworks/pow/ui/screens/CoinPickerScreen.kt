package com.frynetworks.pow.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.frynetworks.pow.catalog.Coin
import com.frynetworks.pow.catalog.CoinCatalog
import com.frynetworks.pow.catalog.MinerFamily
import com.frynetworks.pow.ui.components.FocusableCard
import com.frynetworks.pow.ui.theme.Crimson
import com.frynetworks.pow.ui.theme.Muted
import com.frynetworks.pow.ui.theme.Warning

/**
 * A full screen rather than a dropdown: nearly fifty entries in a popup menu is
 * unusable with a D-pad, and a list can show why an entry is unavailable.
 */
@Composable
fun CoinPickerScreen(
    availableFamilies: Set<MinerFamily>,
    onPick: (Coin) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column {
                Text("Choose a coin", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack, modifier = Modifier.testTag("btn_picker_back")) { Text("Back") }
            }
        }

        CoinCatalog.grouped().forEach { (group, coins) ->
            item {
                Text(
                    group.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Crimson,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(coins) { coin ->
                val reason = CoinCatalog.unavailabilityReason(coin, availableFamilies)
                FocusableCard(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clickable(enabled = reason == null) { onPick(coin) },
                    testTag = "coin_${coin.id}",
                ) {
                    Text("${coin.name} (${coin.ticker})", style = MaterialTheme.typography.bodyLarge)
                    Text(coin.algo, style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (reason != null) {
                        Text(reason, style = MaterialTheme.typography.bodySmall, color = Warning)
                    }
                }
            }
        }
    }
}
