package dev.michelle.trails

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import dev.michelle.trails.domain.*
import dev.michelle.trails.presentation.HealthEvent
import dev.michelle.trails.presentation.HealthUiState
import dev.michelle.trails.presentation.HealthSyncUiState
import dev.michelle.trails.presentation.HealthSyncEvent
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.testTag

@Composable
internal fun HealthConnectionsDialog(
    state: HealthUiState,
    onEvent: (HealthEvent) -> Unit,
    onRequestPermission: (HealthPermissionMode) -> Unit,
    onSetup: () -> Unit,
    onViewData: () -> Unit,
    syncState: HealthSyncUiState,
    onSyncEvent: (HealthSyncEvent) -> Unit,
    needsStartTime: List<Walk>,
    onDismiss: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    // Preview selection is a separate page in the same flow, not another stacked dialog.
    if (state.preview != null) {
        WorkoutImportDialog(state, onEvent)
        return
    }
    val enabled = !state.isBusy && !syncState.isBusy
    JournalDetailDialog("Health apps", onDismiss, closeEnabled = !state.isBusy) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Connect your journal through Health Connect.", style = MaterialTheme.typography.bodyLarge) }
            if (state.isBusy || syncState.isBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { message -> item { JournalMessage("Health connection needs attention", message, error = true) } }
            state.message?.let { message -> item { JournalMessage("Health update", message) } }
            syncState.error?.let { message -> item { JournalMessage("Couldn’t update sharing", message, error = true) } }
            item {
                JournalDetailSection("Connection") {
                    when (state.access.availability) {
                        HealthAvailability.UNAVAILABLE -> Text("Health Connect is not available on this device. Your journal still works normally.")
                        HealthAvailability.UPDATE_REQUIRED -> {
                            Text("Install or update Health Connect to connect your health apps.")
                            Button(enabled = enabled, onClick = onSetup) { Text("Install or update Health Connect") }
                        }
                        HealthAvailability.AVAILABLE -> {
                            Text("Workout reading: ${if (state.access.canRead) "Allowed" else "Not allowed"}")
                            Text("Workout sharing: ${if (state.access.canWrite) "Allowed" else "Not allowed"}")
                            if (!state.access.canRead) OutlinedButton(enabled = enabled, onClick = { onRequestPermission(HealthPermissionMode.READ_WORKOUTS) }) { Text("Allow workout & distance reading") }
                            if (!state.access.canWrite) OutlinedButton(enabled = enabled, onClick = { onRequestPermission(HealthPermissionMode.SHARE_WORKOUTS) }) { Text("Allow sharing to health apps") }
                            TextButton(enabled = enabled, onClick = onSetup) { Text("Health Connect settings") }
                            TextButton(enabled = enabled, onClick = onViewData) { Text("View Health Connect data") }
                        }
                    }
                }
            }
            item {
                JournalDetailSection("Automatic sharing") {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(syncState.report.enabled, enabled = enabled,
                        role = Role.Switch, onValueChange = { onSyncEvent(HealthSyncEvent.EnabledChanged(it)) }),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Automatically share & update", modifier = Modifier.weight(1f))
                        Switch(checked = syncState.report.enabled, enabled = enabled, onCheckedChange = null)
                    }
                    Text("Save an activity with its actual start time to queue sharing. Later edits update the same Health Connect records.", style = MaterialTheme.typography.bodyMedium)
                    Text("${syncState.report.linked} set up · ${syncState.report.pending} pending · ${syncState.report.needsStartTime} need a start time", style = MaterialTheme.typography.bodySmall)
                    if (syncState.report.updated > 0) Text("Updated ${syncState.report.updated} activities in Health Connect.")
                    OutlinedButton(enabled = enabled, onClick = { onSyncEvent(HealthSyncEvent.Retry) }) { Text("Check for updates") }
                    TextButton(enabled = enabled, onClick = onOpenHistory) { Text("Sync history & diagnostics") }
                }
            }
            if (needsStartTime.isNotEmpty()) item {
                JournalDetailSection("Add a start time") {
                    Text("These activities need an actual start time before they can be shared.", style = MaterialTheme.typography.bodySmall)
                    JournalHelp("Choose an activity (${needsStartTime.size})") {
                        needsStartTime.forEach { walk ->
                            TextButton(enabled = enabled, onClick = { onEvent(HealthEvent.ShareRequested(walk)) }) {
                                Text("Set up: ${walk.title} · ${walk.date}")
                            }
                        }
                    }
                }
            }
            item {
                JournalDetailSection("Import workouts") {
                    Text("Bring walking and hiking workouts from compatible health apps into your journal. Review and select before importing.")
                    Button(enabled = enabled && state.access.availability == HealthAvailability.AVAILABLE && state.access.canRead,
                        onClick = { onEvent(HealthEvent.PreviewRequested) }) { Text("Preview recent workouts") }
                    if (!state.access.canRead) Text("Allow workout reading in Connection first.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                JournalHelp("Compatibility & sharing help") {
                    Text("Your watch companion app must also share workouts and distance with Health Connect. This is not direct watch pairing.")
                    Text("To receive data in Samsung Health or another app, enable its Health Connect connection and exercise/distance reading. The receiving app controls when and how imported data appears.")
                    Text("Only exercise and distance are shared. Steps, calories, notes and map pins are not exported. Apple Health needs a separate iOS companion, which is not included.")
                    Text("Failed updates retry with a delay, including in the background when Android allows. Imported workouts are excluded from re-sharing. Deleting a shared entry queues removal after an Undo grace period.")
                }
            }
        }
    }
}

@Composable
internal fun WorkoutImportDialog(state: HealthUiState, onEvent: (HealthEvent) -> Unit) {
    val preview = state.preview ?: return
    JournalDetailDialog("Import workouts", { onEvent(HealthEvent.PreviewDismissed) }, closeEnabled = !state.isBusy,
        bottomBar = {
            JournalDetailActions {
                Text("${state.selectedIds.size} selected", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Button(enabled = !state.isBusy && state.selectedIds.isNotEmpty(), onClick = { onEvent(HealthEvent.ImportConfirmed) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("import-selected")) {
                    Text(if (state.isBusy) "Importing…" else "Import ${state.selectedIds.size} workouts")
                }
            }
        }) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("workout-import-list"), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("${preview.candidates.size} new · ${preview.existing} already imported · ${preview.skipped} skipped", style = MaterialTheme.typography.titleMedium)
                Text("If multiple apps shared the same workout, choose only the copy you want.", style = MaterialTheme.typography.bodyMedium)
            }
            if (state.isBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { message -> item { JournalMessage("Couldn’t import workouts", message, error = true) } }
            if (preview.truncated) item { Text("More sessions exist outside this preview limit.", style = MaterialTheme.typography.bodySmall) }
            if (preview.candidates.isEmpty()) item {
                JournalMessage("No new workouts", if (preview.existing > 0) "All eligible workouts in this preview are already in your journal."
                    else "No eligible workouts found. Check that your companion app shares walking/hiking sessions and distance to Health Connect.")
            } else item {
                Row {
                    TextButton(enabled = !state.isBusy, onClick = { onEvent(HealthEvent.SelectAll) }) { Text("Select all") }
                    TextButton(enabled = !state.isBusy, onClick = { onEvent(HealthEvent.ClearSelection) }) { Text("Clear") }
                }
            }
            items(preview.candidates, key = { it.walk.id }) { candidate ->
                OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().toggleable(candidate.walk.id in state.selectedIds, enabled = !state.isBusy, role = Role.Checkbox,
                        onValueChange = { onEvent(HealthEvent.SelectionChanged(candidate.walk.id)) }).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = candidate.walk.id in state.selectedIds, enabled = !state.isBusy, onCheckedChange = null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(candidate.walk.title, style = MaterialTheme.typography.titleSmall)
                            Text("${candidate.walk.date} · ${formatKm(candidate.walk.distanceMeters.toLong())} km · ${ActivityDuration.format(candidate.walk.durationMinutes.toLong())}", style = MaterialTheme.typography.bodyMedium)
                            Text(candidate.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { JournalHelp("About workout imports") {
                Text("Checks up to 100 sessions from the last 30 days. Walking/hiking sessions need distance from the same source. Duration includes pauses and is rounded up to a minute.")
                Text("Import adds journal copies. It does not change or delete workouts in your other apps.")
            } }
        }
    }
}

@Composable
internal fun HealthShareResultDialog(title: String, onViewData: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved to Health Connect") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$title: Health Connect accepted the workout and distance records.")
                Text("Open its data screen and look for this activity under Exercise and Distance, with Trail Journal as the source.")
                Text("This confirms delivery to Health Connect. The other app controls when it reads or displays the data; allow that app to read exercise and distance in its Health Connect settings.")
            }
        },
        confirmButton = { TextButton(onClick = onViewData) { Text("View Health Connect data") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
internal fun ShareHealthDialog(state: HealthUiState, onEvent: (HealthEvent) -> Unit, onRequestPermission: (HealthPermissionMode) -> Unit, onSetup: () -> Unit) {
    val walk = state.shareWalk ?: return
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { onEvent(HealthEvent.ShareDismissed) },
        title = { Text("Share activity to Health Connect") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${walk.title} · ${walk.date}")
                Text("${ActivityDuration.format(walk.durationMinutes.toLong())} · ${formatKm(walk.distanceMeters.toLong())} km")
                Text("Choose the actual start time (${walk.zoneId ?: ZoneId.systemDefault().id}). Shares type, distance, duration, title, and this time.")
                OutlinedButton(enabled = !state.isBusy, onClick = {
                    val initial = runCatching { LocalTime.parse(state.shareStartTime) }.getOrNull() ?: LocalTime.now()
                    TimePickerDialog(context, { _, hour, minute ->
                        onEvent(HealthEvent.StartTimeSelected(String.format(Locale.ROOT, "%02d:%02d", hour, minute)))
                    }, initial.hour, initial.minute, android.text.format.DateFormat.is24HourFormat(context)).show()
                }) { Text(state.shareStartTime.ifBlank { "Choose start time" }) }
                Text("The start time is saved to this activity. Enable automatic sharing in Health apps & smartwatches to sync future saves. Deleting a shared entry also queues removal of its Health Connect records; Undo restores it.", style = MaterialTheme.typography.bodySmall)
                if (state.access.availability == HealthAvailability.UNAVAILABLE) Text("Health Connect sharing is unavailable on this device.")
                if (state.access.availability == HealthAvailability.UPDATE_REQUIRED) TextButton(enabled = !state.isBusy, onClick = onSetup) { Text("Install or update Health Connect") }
                if (!state.access.canWrite) TextButton(enabled = !state.isBusy && state.access.availability == HealthAvailability.AVAILABLE, onClick = { onRequestPermission(HealthPermissionMode.SHARE_WORKOUTS) }) { Text("Allow sharing") }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = !state.isBusy && state.shareStartTime.isNotBlank() && state.access.canWrite, onClick = { onEvent(HealthEvent.ShareConfirmed) }) { Text("Share workout") } },
        dismissButton = { TextButton(enabled = !state.isBusy, onClick = { onEvent(HealthEvent.ShareDismissed) }) { Text("Cancel") } },
    )
}
