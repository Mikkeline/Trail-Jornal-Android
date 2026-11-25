package dev.michelle.trails

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.michelle.trails.domain.*
import dev.michelle.trails.presentation.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SyncHistoryDialog(
    sync: HealthSyncUiState,
    support: SupportUiState,
    onSyncEvent: (HealthSyncEvent) -> Unit,
    onSupportEvent: (SupportEvent) -> Unit,
    onSetup: () -> Unit,
    onEdit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(SyncHistoryFilter.ALL) }
    var showMenu by remember { mutableStateOf(false) }
    val visible = sync.report.history.filter(filter::matches)
    val attentionStates = setOf(SyncHistoryState.FAILED, SyncHistoryState.BLOCKED, SyncHistoryState.NEEDS_PERMISSION,
        SyncHistoryState.NEEDS_TIME, SyncHistoryState.UNAVAILABLE)
    val attention = sync.report.history.count { it.state in attentionStates }
    val waiting = sync.report.history.count { !it.complete && it.state !in attentionStates }
    JournalDetailDialog("Sync history", onDismiss, actions = {
        Box {
            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Sync history options") }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Manage health access") }, enabled = !sync.isBusy,
                    onClick = { showMenu = false; onSetup() })
                DropdownMenuItem(text = { Text("Preview diagnostics") }, enabled = !support.isBusy,
                    onClick = { showMenu = false; onSupportEvent(SupportEvent.PreviewRequested) })
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("sync-history-list"), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                JournalDetailSection("Health Connect updates") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$waiting waiting", style = MaterialTheme.typography.titleMedium)
                        Text("$attention need attention", style = MaterialTheme.typography.titleMedium,
                            color = if (attention > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        Text("${sync.report.history.count { it.complete }} completed", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Latest operation for each shared activity, including removals. Other apps control when they read these records.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(enabled = !sync.isBusy, onClick = { onSyncEvent(HealthSyncEvent.Refresh) }) { Text("Refresh status") }
                }
            }
            if (sync.isBusy || support.isBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            sync.error?.let { message -> item { JournalMessage("Couldn’t refresh sync status", message, error = true) } }
            support.error?.let { message -> item { JournalMessage("Couldn’t prepare diagnostics", message, error = true) } }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SyncHistoryFilter.entries.forEach { item ->
                        FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) })
                    }
                }
            }
            if (sync.report.history.isEmpty()) item {
                JournalMessage("No shared activities yet", "Set a start time and enable automatic sharing, or share an activity from its card.")
            } else if (visible.isEmpty()) item {
                JournalMessage("No operations match this filter", "Choose All to see your latest operations.") {
                    TextButton(onClick = { filter = SyncHistoryFilter.ALL }) { Text("Show all") }
                }
            }
            items(visible, key = { it.walkId }) { entry ->
                SyncHistoryCard(entry, !sync.isBusy, onRetry = { onSyncEvent(HealthSyncEvent.RetryActivity(entry.walkId)) }, onEdit = { onEdit(entry.walkId) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncHistoryCard(entry: SyncHistoryEntry, enabled: Boolean, onRetry: () -> Unit, onEdit: () -> Unit) {
    val title = entry.title ?: "Deleted activity · ${HealthRecordIdentity.exportId(entry.walkId).substringAfter(':').take(8)}"
    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(entry.state.label, style = MaterialTheme.typography.labelLarge, color = if (entry.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(historyAdvice(entry.state), style = MaterialTheme.typography.bodyMedium)
            entry.lastSyncedAt?.let { Text("Last successful operation: ${historyTime(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            entry.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.canRetry) TextButton(enabled = enabled, onClick = onRetry,
                    modifier = Modifier.semantics { contentDescription = "Retry ${entry.operation.name.lowercase()} for $title" }) {
                    Text(if (entry.operation == HealthJobOperation.DELETE) "Retry removal" else "Retry sharing")
                }
                if (entry.title != null && entry.operation == HealthJobOperation.UPSERT && entry.state in setOf(SyncHistoryState.NEEDS_TIME, SyncHistoryState.BLOCKED)) {
                    TextButton(enabled = enabled, onClick = onEdit) { Text("Edit activity") }
                }
            }
            JournalHelp("Operation details") {
                Text(if (entry.operation == HealthJobOperation.DELETE) "Operation: remove shared records" else "Operation: share or update", style = MaterialTheme.typography.bodySmall)
                entry.retryAt?.let { Text(if (entry.state == SyncHistoryState.UNDO_GRACE) "Removal no earlier than ${historyTime(it)}" else "Earliest retry: ${historyTime(it)}", style = MaterialTheme.typography.bodySmall) }
                if (entry.failedAttempts > 0) Text("Failed attempts: ${entry.failedAttempts}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun historyAdvice(state: SyncHistoryState): String = when (state) {
    SyncHistoryState.SYNCED -> "Health Connect accepted the exercise and distance records."
    SyncHistoryState.REMOVED -> "Health Connect accepted removal of this app's exercise and distance records. Other apps manage their own retained copies."
    SyncHistoryState.UNDO_GRACE -> "The short grace period gives you time to use Undo. Android may run removal later."
    SyncHistoryState.NEEDS_PERMISSION -> "Allow exercise and distance sharing in Health Connect, then retry."
    SyncHistoryState.UNAVAILABLE -> "Install or update Health Connect on a supported Android device, then refresh."
    SyncHistoryState.NEEDS_TIME -> "Edit the activity and choose its actual start time."
    SyncHistoryState.PAUSED -> "Enable automatic sharing, or use Retry sharing to send this activity explicitly."
    SyncHistoryState.BLOCKED -> "Check the error below. Correct the activity or health access, then retry."
    SyncHistoryState.FAILED -> "The operation is retained for retry. Check health access or retry when the provider is available."
    SyncHistoryState.PENDING, SyncHistoryState.REMOVAL_PENDING -> "Waiting for Health Connect. Updates also retry when Android permits background work."
}

private fun historyTime(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss"))
