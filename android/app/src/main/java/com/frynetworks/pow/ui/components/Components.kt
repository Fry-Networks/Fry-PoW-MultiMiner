package com.frynetworks.pow.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frynetworks.pow.ui.theme.Crimson
import com.frynetworks.pow.ui.theme.Muted

/**
 * Lets a D-pad leave a single-line text field. Without this the editor swallows the
 * arrow keys and a remote user is trapped in the field with no way out. Hardware keys
 * only, so touch and IME behaviour on a phone are unchanged.
 */
fun Modifier.dpadNavigable(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
            else -> false
        }
    }
}

/** A visible focus ring - a TV has no cursor, so default indication is not enough. */
fun Modifier.focusRing(interactionSource: MutableInteractionSource): Modifier = composed {
    val focused by interactionSource.collectIsFocusedAsState()
    border(
        width = if (focused) 2.dp else 0.dp,
        color = if (focused) Crimson else androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(12.dp),
    )
}

/**
 * A card that participates in D-pad traversal. A list whose items have no focusable
 * child cannot be scrolled by a remote at all.
 */
@Composable
fun FocusableCard(
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .focusable(interactionSource = interaction)
            .focusRing(interaction),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    numeric: Boolean = false,
    error: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            isError = error != null,
            trailingIcon = trailing,
            keyboardOptions = if (numeric) {
                androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
            } else {
                androidx.compose.foundation.text.KeyboardOptions.Default
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .dpadNavigable(),
        )
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp).testTag("${testTag}_err"),
            )
        }
    }
}

/** Numeric entry without an IME round-trip, which is painful on a remote. */
@Composable
fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    testTag: String,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(testTag))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { if (value > range.first) onChange(value - 1) },
                modifier = Modifier.size(56.dp).testTag("${testTag}_minus"),
            ) { Text("-", style = MaterialTheme.typography.titleLarge) }
            TextButton(
                onClick = { if (value < range.last) onChange(value + 1) },
                modifier = Modifier.size(56.dp).testTag("${testTag}_plus"),
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

/**
 * A settings switch a D-pad can reach: the row is the single focus target and the
 * Switch is display-only, so a remote sees one stop instead of two.
 */
@Composable
fun ToggleRow(
    label: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(interactionSource = interaction, indication = null) { onCheckedChange(!checked) }
            .focusRing(interaction)
            .testTag(testTag)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun StatTile(label: String, value: String, testTag: String, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .focusable(interactionSource = interaction)
            .focusRing(interaction)
            .padding(14.dp),
    ) {
        Column {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = Crimson, modifier = Modifier.testTag(testTag))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}
