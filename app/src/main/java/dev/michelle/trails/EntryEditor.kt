package dev.michelle.trails

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import java.time.format.DateTimeFormatter
import androidx.compose.material3.SelectableDates
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import dev.michelle.trails.domain.ActivityType
import dev.michelle.trails.domain.Effort
import dev.michelle.trails.domain.FormField
import dev.michelle.trails.presentation.JournalEvent
import dev.michelle.trails.presentation.JournalUiState
import android.app.TimePickerDialog
import androidx.compose.ui.platform.LocalContext
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryEditor(state: JournalUiState, showActions: Boolean = true, onEvent: (JournalEvent) -> Unit) {
    val context = LocalContext.current
    val draft = state.draft
    val enabled = !state.isSaving
    var showDetails by rememberSaveable(draft.id) { mutableStateOf(draft.id != null) }
    LaunchedEffect(state.fieldErrors) {
        if (state.fieldErrors.keys.any { it in setOf(FormField.TITLE, FormField.LOCATION, FormField.NOTES) }) showDetails = true
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showMapPicker by rememberSaveable { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "The basics",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text("Choose the activity, distance and duration.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text("Activity type", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActivityType.entries.forEach { type ->
                    FilterChip(
                        selected = draft.type == type,
                        enabled = enabled,
                        onClick = { onEvent(JournalEvent.TypeSelected(type)) },
                        label = { Text(type.label) },
                    )
                }
            }
            DraftField("Distance", draft.distanceKm, FormField.DISTANCE, state, onEvent, KeyboardType.Decimal)
            DurationInput(draft.durationMinutes, enabled, state.fieldErrors[FormField.DURATION]) {
                onEvent(JournalEvent.FieldChanged(FormField.DURATION, it))
            }
            HorizontalDivider()
            Text("When", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            OutlinedTextField(
                value = runCatching { LocalDate.parse(draft.date).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")) }.getOrDefault(draft.date),
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth().testTag("field-DATE").semantics { state.fieldErrors[FormField.DATE]?.let { error(it) } },
                isError = state.fieldErrors.containsKey(FormField.DATE),
                supportingText = { state.fieldErrors[FormField.DATE]?.let { Text(it) } },
                trailingIcon = {
                    TextButton(enabled = enabled, onClick = { showDatePicker = true }) {
                        Text("Choose")
                    }
                },
            )
            OutlinedTextField(
                value = draft.startTime,
                onValueChange = {}, readOnly = true, enabled = enabled,
                label = { Text("Start time (optional)") },
                modifier = Modifier.fillMaxWidth().testTag("field-START_TIME").semantics { state.fieldErrors[FormField.START_TIME]?.let { error(it) } },
                isError = state.fieldErrors.containsKey(FormField.START_TIME),
                supportingText = { Text(state.fieldErrors[FormField.START_TIME] ?: "${draft.zoneId.ifBlank { ZoneId.systemDefault().id }} · Set a time for automatic Health Connect sharing.") },
                trailingIcon = { TextButton(enabled = enabled, onClick = {
                    val initial = runCatching { LocalTime.parse(draft.startTime) }.getOrNull() ?: LocalTime.now()
                    TimePickerDialog(context, { _, hour, minute ->
                        onEvent(JournalEvent.FieldChanged(FormField.START_TIME, String.format(Locale.ROOT, "%02d:%02d", hour, minute)))
                    }, initial.hour, initial.minute, android.text.format.DateFormat.is24HourFormat(context)).show()
                }) { Text("Choose") } },
            )
            if (draft.startTime.isNotBlank()) TextButton(enabled = enabled, onClick = {
                onEvent(JournalEvent.FieldChanged(FormField.START_TIME, ""))
            }) { Text("Clear start time") }
            HorizontalDivider()
            TextButton(onClick = { showDetails = !showDetails }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (showDetails) "Hide details" else "Add details", modifier = Modifier.weight(1f))
                Icon(if (showDetails) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            if (showDetails) {
                DraftField("Activity name", draft.title, FormField.TITLE, state, onEvent)
                DraftField("Location or venue (optional)", draft.location, FormField.LOCATION, state, onEvent)
                draft.point?.let { point ->
                    Text("Pin: ${point.latitude}, ${point.longitude}", style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = enabled, onClick = { showMapPicker = true }) {
                        Text(if (draft.point == null) "Choose on map" else "Change map pin")
                    }
                    if (draft.point != null) {
                        TextButton(enabled = enabled, onClick = { onEvent(JournalEvent.LocationSelected(null)) }) {
                            Text("Remove pin")
                        }
                    }
                }
                Text("Effort (optional)", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = draft.effort == null,
                        enabled = enabled,
                        onClick = { onEvent(JournalEvent.EffortSelected(null)) },
                        label = { Text("Not set") },
                    )
                    Effort.entries.forEach { effort ->
                        FilterChip(
                            selected = draft.effort == effort,
                            enabled = enabled,
                            onClick = { onEvent(JournalEvent.EffortSelected(effort)) },
                            label = { Text(effort.label) },
                        )
                    }
                }
                DraftField("Notes (optional)", draft.notes, FormField.NOTES, state, onEvent, multiline = true)
            }
            if (showActions) {
            Button(
                onClick = { onEvent(JournalEvent.SaveRequested) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("save-entry"),
            ) {
                Text(if (state.isSaving) "Saving…" else if (draft.id == null) "Save activity" else "Save changes")
            }
            TextButton(
                onClick = { onEvent(JournalEvent.NewEntryRequested) },
                enabled = enabled,
            ) {
                Text(if (draft.id == null) "Clear form" else "Cancel editing")
            }
            }
        }
    }

    if (showMapPicker) {
        MapLocationDialog(
            initialPoint = draft.point,
            onDismiss = { showMapPicker = false },
            onConfirm = { point ->
                onEvent(JournalEvent.LocationSelected(point))
                showMapPicker = false
            },
        )
    }

    if (showDatePicker) {
        ActivityDatePicker(
            initialDate = draft.date,
            onDismiss = { showDatePicker = false },
            onSelected = { date ->
                onEvent(JournalEvent.FieldChanged(FormField.DATE, date))
                showDatePicker = false
            },
        )
    }
}

@Composable
private fun DraftField(
    label: String,
    value: String,
    field: FormField,
    state: JournalUiState,
    onEvent: (JournalEvent) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    multiline: Boolean = false,
) {
    val focus = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = { onEvent(JournalEvent.FieldChanged(field, it)) },
        label = { Text(label) },
        suffix = { if (field == FormField.DISTANCE) Text("km") },
        shape = MaterialTheme.shapes.medium,
        enabled = !state.isSaving,
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (multiline) ImeAction.Default else ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { keyboardController?.hide(); focus.clearFocus() }),
        isError = state.fieldErrors.containsKey(field),
        supportingText = { state.fieldErrors[field]?.let { Text(it) } },
        modifier = Modifier.fillMaxWidth().testTag("field-${field.name}").semantics { state.fieldErrors[field]?.let { error(it) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityDatePicker(initialDate: String, onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    val initialMillis = runCatching {
        LocalDate.parse(initialDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrNull()
    val dateState = rememberDatePickerState(initialSelectedDateMillis = initialMillis, selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= LocalDate.now()
        override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
    })
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = dateState.selectedDateMillis != null,
                onClick = {
                    // Material date-picker values use UTC, independent of the device's time zone.
                    val selected = checkNotNull(dateState.selectedDateMillis)
                    onSelected(Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate().toString())
                },
            ) { Text("Choose date") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = dateState)
    }
}
