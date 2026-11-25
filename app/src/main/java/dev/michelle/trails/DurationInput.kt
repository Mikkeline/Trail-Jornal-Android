package dev.michelle.trails

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.text.KeyboardActions
import dev.michelle.trails.domain.ActivityDuration

@Composable
internal fun DurationInput(value: String, enabled: Boolean, error: String?, onChanged: (String) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val total = value.toIntOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = { showPicker = true },
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 74.dp).testTag("field-DURATION").semantics { error?.let { this.error(it) } },
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Duration", style = MaterialTheme.typography.labelMedium)
                    Text(if (total != null && total > 0) ActivityDuration.format(total.toLong()) else "Choose duration", style = MaterialTheme.typography.titleMedium)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60).forEach { preset ->
                FilterChip(
                    selected = total == preset,
                    enabled = enabled,
                    onClick = { onChanged(preset.toString()) },
                    label = { Text(ActivityDuration.format(preset.toLong())) },
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (showPicker) {
        DurationPicker(total, onDismiss = { showPicker = false }) { minutes ->
            onChanged(minutes.toString())
            showPicker = false
        }
    }
}

@Composable
private fun DurationPicker(initialMinutes: Int?, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var hours by rememberSaveable { mutableStateOf(initialMinutes?.let { (it / 60).toString() }.orEmpty()) }
    var minutes by rememberSaveable { mutableStateOf(initialMinutes?.let { (it % 60).toString() }.orEmpty()) }
    val result = ActivityDuration.fromParts(hours, minutes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How long was your activity?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hours, onValueChange = { hours = it }, label = { Text("Hours") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("duration-hours"),
                    )
                    OutlinedTextField(
                        value = minutes, onValueChange = { minutes = it }, label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("duration-minutes"),
                    )
                }
                if (result.isSuccess) Text(ActivityDuration.format(result.getOrThrow().toLong()))
                else if (hours.isNotEmpty() || minutes.isNotEmpty()) Text(result.exceptionOrNull()?.message.orEmpty(), color = MaterialTheme.colorScheme.error)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 30, 60, 90).forEach { preset ->
                        TextButton(onClick = { hours = (preset / 60).toString(); minutes = (preset % 60).toString() }) {
                            Text(ActivityDuration.format(preset.toLong()))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = result.isSuccess, onClick = { result.getOrNull()?.let(onConfirm) }) { Text("Set duration") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
