package dev.michelle.trails

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.michelle.trails.domain.BackupOverview
import dev.michelle.trails.presentation.JournalEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BackupRecoveryCard(overview: BackupOverview, enabled: Boolean, onEvent: (JournalEvent) -> Unit,
    onExport: () -> Unit, onImport: () -> Unit) {
    var showRecovery by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Keep your journal", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(overview.receipt?.let { "Last backup created: ${backupDate(it.createdAt)} · ${it.entries} activities" }
                ?: "No backup recorded on this installation.")
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(if (overview.receipt == null) "Create a backup to protect your entries." else if (overview.hasUnbackedChanges) "A new backup is recommended." else "Your journal matches the last recorded backup.",
                    Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Button(enabled = enabled, onClick = onExport, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Back up now") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(enabled = enabled, onClick = onImport) { Text("Restore") }
                TextButton(onClick = { showRecovery = true }) { Text("Recovery guide") }
            }
            HorizontalDivider()
            overview.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (overview.reminderDue) {
                Text("Your latest journal changes need a backup.", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(enabled = enabled, onClick = { onEvent(JournalEvent.BackupSnoozed) }) { Text("Remind me in 7 days") }
            } else if (overview.remindersEnabled && overview.snoozedUntil != null && overview.hasUnbackedChanges) {
                Text("Reminder snoozed until ${backupDate(overview.snoozedUntil)}.", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth().toggleable(overview.remindersEnabled, enabled = enabled, role = Role.Switch,
                onValueChange = { onEvent(JournalEvent.BackupRemindersChanged(it)) }).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Backup reminders")
                    Text("Shown here only; no notifications.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = overview.remindersEnabled, onCheckedChange = null, enabled = enabled)
            }
            JournalHelp("About your backup") {
                Text("The receipt records a completed export, not whether the file is still accessible. Keep the selected file before changing phones or uninstalling.", style = MaterialTheme.typography.bodySmall)
                Text("Backup files contain plain-text activity details, notes and pins. Choose a destination you trust.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (showRecovery) JournalDetailDialog("Recovery guide", { showRecovery = false }, bottomBar = {
        JournalDetailActions { Button(onClick = { showRecovery = false }, modifier = Modifier.fillMaxWidth()) { Text("Done") } }
    }) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            JournalDetailSection("1. Save a backup") {
                Text("Tap Back up now and save the JSON file in Files or a cloud folder you control. Keep that file before reinstalling or changing phones.")
            }
            JournalDetailSection("2. Restore your entries") {
                Text("On the new installation, tap Restore, choose the file, review the preview and confirm. Restore adds missing activities; existing IDs and edits are kept unchanged.")
            }
            JournalDetailSection("3. Reconnect health apps") {
                Text("Allow Health Connect permissions and choose your automatic-sharing setting again. Sync consent and pending jobs are not restored from the backup.")
            }
            JournalMessage("Keep the file safe", "Backups include activity details, notes, map pins and saved start times as plain text. Health Connect is not a full journal backup. Uninstalling without an exported file may lose your local journal.")
            Text("Undo deletion is session-only and does not replace a backup.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun backupDate(instant: Instant): String = instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
