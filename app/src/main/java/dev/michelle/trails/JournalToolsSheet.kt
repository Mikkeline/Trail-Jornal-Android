package dev.michelle.trails

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.michelle.trails.presentation.JournalEvent
import dev.michelle.trails.presentation.JournalUiState
import dev.michelle.trails.presentation.JournalScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JournalToolsSheet(state: JournalUiState, onEvent: (JournalEvent) -> Unit, onDismiss: () -> Unit,
    onExport: () -> Unit, onImport: () -> Unit, onOpenHealth: () -> Unit, onOpenSyncHistory: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Journal tools", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text("Look after your data and choose how you share it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            JournalToolRow("Health apps & smartwatches", "Permissions, workout import and automatic sharing", !state.isSaving) { onDismiss(); onOpenHealth() }
            JournalToolRow("Sync history & diagnostics", "Review pending operations or prepare a support report", !state.isSaving) { onDismiss(); onOpenSyncHistory() }
            BackupRecoveryCard(state.backup, !state.isSaving && !state.isLoading && state.loadError == null, onEvent,
                { onDismiss(); onExport() }, { onDismiss(); onImport() })
            if (state.screen == JournalScreen.EDITOR) TextButton(enabled = !state.isSaving, onClick = { onDismiss(); onEvent(JournalEvent.NewEntryRequested) }) {
                Text(if (state.draft.id == null) "Clear this form" else "Start a new activity")
            }
            Text("Appearance follows your device's light or dark setting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}
