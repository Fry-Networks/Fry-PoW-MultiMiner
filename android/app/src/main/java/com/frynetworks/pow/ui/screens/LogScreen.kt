package com.frynetworks.pow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.frynetworks.pow.ui.theme.MonoStyle
import com.frynetworks.pow.ui.theme.Muted

@Composable
fun LogScreen(lines: List<String>, onClear: () -> Unit) {
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("Miner log", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear, modifier = Modifier.testTag("btn_clear_log")) { Text("Clear") }
        }
        Text("${lines.size} lines", style = MaterialTheme.typography.bodySmall, color = Muted,
            modifier = Modifier.testTag("txt_log_count"))

        // One focusable container rather than 500 focusable rows: individual lines
        // would make D-pad traversal unusable.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
                .background(Color.Black)
                .focusable()
                .testTag("log_pane"),
        ) {
            items(lines.size) { index ->
                Text(lines[index], style = MonoStyle, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}
