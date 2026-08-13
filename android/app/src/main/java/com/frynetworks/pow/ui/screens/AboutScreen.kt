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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.frynetworks.pow.BuildConfig
import com.frynetworks.pow.catalog.MinerFamily
import com.frynetworks.pow.devfee.DevFee
import com.frynetworks.pow.ui.components.FocusableCard
import com.frynetworks.pow.ui.theme.Crimson
import com.frynetworks.pow.ui.theme.Muted
import com.frynetworks.pow.ui.theme.Success
import com.frynetworks.pow.ui.theme.Warning
import com.frynetworks.pow.update.UpdateRepository
import com.frynetworks.pow.update.UpdateState
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(
    repository: UpdateRepository,
    isTv: Boolean,
    availableFamilies: Set<MinerFamily>,
    lowMemory: Boolean,
) {
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("About", style = MaterialTheme.typography.headlineSmall)

        FocusableCard(testTag = "card_version") {
            Text("Version", style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(
                "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("txt_version"),
            )
            Button(
                onClick = { scope.launch { state = UpdateState.Checking; state = repository.check() } },
                modifier = Modifier.padding(top = 8.dp).testTag("btn_check_update"),
            ) { Text("Check for updates") }

            val message = when (val s = state) {
                is UpdateState.Idle -> "Not checked yet"
                is UpdateState.Checking -> "Checking..."
                is UpdateState.UpToDate -> "Up to date (${s.current})"
                is UpdateState.Available -> "Update available: ${s.latest}"
                is UpdateState.NoReleases -> "No Android releases published yet"
                is UpdateState.Failed -> s.message
            }
            val colour = when (state) {
                is UpdateState.Available -> Warning
                is UpdateState.UpToDate -> Success
                is UpdateState.Failed -> MaterialTheme.colorScheme.error
                else -> Muted
            }
            Text(message, color = colour, modifier = Modifier.padding(top = 6.dp).testTag("txt_update_status"))
            (state as? UpdateState.Available)?.let {
                Text(it.url, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }

        FocusableCard(modifier = Modifier.padding(top = 12.dp), testTag = "card_devfee") {
            Text("Dev fee", style = MaterialTheme.typography.labelSmall, color = Muted)
            Text("${DevFee.PERCENT}%", style = MaterialTheme.typography.titleMedium, color = Crimson)
            Text(
                "Mining runs on your wallet for ${DevFee.USER_MINUTES} minutes of every " +
                    "${DevFee.USER_MINUTES + DevFee.DEV_MINUTES}, then on the project wallet for " +
                    "${DevFee.DEV_MINUTES} minute. Unmineable coins also carry the project referral code.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("txt_devfee"),
            )
        }

        FocusableCard(modifier = Modifier.padding(top = 12.dp), testTag = "card_device") {
            Text("This device", style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(if (isTv) "Android TV (D-pad navigation)" else "Phone / tablet (touch)",
                style = MaterialTheme.typography.bodyMedium)
            Text(
                "Miners available: " +
                    (availableFamilies.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ") { it.name.lowercase() } ?: "none for this CPU architecture"),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("txt_miners"),
            )
            if (lowMemory) {
                Text(
                    "Limited memory detected - RandomX runs in light mode and will be slow. " +
                        "A lighter algorithm such as cn-pico is a better fit here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Warning,
                )
            }
            Text(
                "TLS pools (stratum+ssl://) are not supported in this build.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }

        FocusableCard(modifier = Modifier.padding(top = 12.dp), testTag = "card_licences") {
            Text("Open source licences", style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(
                "This app bundles unmodified builds of XMRig (GPLv3), XLArig (GPLv3), " +
                    "cpuminer (GPLv2) and a VerusHash CPU miner (GPLv2), cross-compiled for Android. " +
                    "Their source is the upstream release tagged in build-native/, and the exact build " +
                    "scripts are published alongside this app in the repository.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Repository: ${BuildConfig.UPDATE_REPO}",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}
