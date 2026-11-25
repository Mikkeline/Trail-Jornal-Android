package dev.michelle.trails

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.michelle.trails.domain.*
import dev.michelle.trails.presentation.JournalEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActivityCard(walk: Walk, enabled: Boolean, onEvent: (JournalEvent) -> Unit,
    onViewLocation: (GeoPoint) -> Unit, onShareHealth: (Walk) -> Unit = {},
    syncStatus: ActivityHealthStatus? = null, onRetryHealth: (String) -> Unit = {}) {
    var menuOpen by remember { mutableStateOf(false) }
    var detailsOpen by rememberSaveable(walk.id) { mutableStateOf(false) }
    val imported = HealthRecordIdentity.imported(walk.id)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActivityGlyph(walk.type)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(walk.type.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(walk.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(activityDate(walk.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(enabled = enabled, onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options for ${walk.title}, ${walk.date}")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(enabled = enabled, text = { Text("Edit activity") }, leadingIcon = { Icon(Icons.Default.Edit, null) },
                            modifier = Modifier.semantics { contentDescription = "Edit ${walk.title}, ${walk.date}" },
                            onClick = { menuOpen = false; onEvent(JournalEvent.EditRequested(walk)) })
                        if (!imported) DropdownMenuItem(enabled = enabled, text = { Text("Share to Health Connect") }, leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            onClick = { menuOpen = false; onShareHealth(walk) })
                        walk.point?.let { point -> DropdownMenuItem(enabled = enabled, text = { Text("View on map") }, leadingIcon = { Icon(Icons.Default.Place, null) },
                            onClick = { menuOpen = false; onViewLocation(point) }) }
                        HorizontalDivider()
                        DropdownMenuItem(enabled = enabled, text = { Text("Delete activity", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.semantics { contentDescription = "Delete ${walk.title}, ${walk.date}" },
                            onClick = { menuOpen = false; onEvent(JournalEvent.DeleteRequested(walk)) })
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column { Text("${formatKm(walk.distanceMeters.toLong())} km", style = MaterialTheme.typography.titleLarge); Text("Distance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Column { Text(ActivityDuration.format(walk.durationMinutes.toLong()), style = MaterialTheme.typography.titleLarge); Text("Duration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (walk.location.isNotBlank()) Text(walk.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (walk.notes.isNotBlank()) {
                var showAllNotes by rememberSaveable(walk.id) { mutableStateOf(false) }
                var notesOverflow by remember(walk.notes) { mutableStateOf(false) }
                Text(walk.notes, maxLines = if (showAllNotes) Int.MAX_VALUE else 3, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, onTextLayout = { notesOverflow = it.hasVisualOverflow })
                if (notesOverflow || showAllNotes) TextButton(onClick = { showAllNotes = !showAllNotes }) { Text(if (showAllNotes) "Show less" else "Read notes") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            FlowRow(horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                if (imported) ActivityStatusPill("Imported", false)
                else syncStatus?.let { ActivityStatusPill(it.state.label, it.state == ActivityHealthState.FAILED) }
                    ?: ActivityStatusPill("Saved in your journal", false)
                TextButton(enabled = enabled, onClick = { detailsOpen = !detailsOpen }) { Text(if (detailsOpen) "Hide details" else "Details") }
            }
            if (!imported && syncStatus?.state == ActivityHealthState.FAILED) {
                Text(syncStatus.error ?: "Health sharing needs another try.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = enabled, modifier = Modifier.semantics { contentDescription = "Retry sharing ${walk.title}, ${walk.date}" }, onClick = { onRetryHealth(walk.id) }) { Text("Retry sharing") }
            }
            if (detailsOpen) {
                walk.startTime?.let { Text("Started $it · ${walk.zoneId}", style = MaterialTheme.typography.bodySmall) }
                walk.effort?.let { Text("Effort: ${it.label}", style = MaterialTheme.typography.bodySmall) }
                syncStatus?.lastSyncedAt?.let { timestamp ->
                    Text("Last sync: ${java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))}", style = MaterialTheme.typography.bodySmall)
                }
                if (!imported) {
                    TextButton(enabled = enabled, onClick = { onShareHealth(walk) }) { Text("Share to Health Connect") }
                    if (syncStatus?.state == ActivityHealthState.NEEDS_PERMISSION) Text("Allow sharing in Journal tools → Health apps & smartwatches.", style = MaterialTheme.typography.bodySmall)
                    if (syncStatus?.state in setOf(ActivityHealthState.PENDING, ActivityHealthState.PAUSED)) TextButton(enabled = enabled, onClick = { onRetryHealth(walk.id) }) { Text("Retry sharing") }
                }
                walk.point?.let { point -> TextButton(enabled = enabled, onClick = { onViewLocation(point) }) { Text("View on map") } }
            }
        }
    }
}

@Composable
private fun ActivityStatusPill(label: String, error: Boolean) {
    Surface(shape = MaterialTheme.shapes.small,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun activityDate(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
}
