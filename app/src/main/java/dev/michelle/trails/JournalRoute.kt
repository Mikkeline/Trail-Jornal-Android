package dev.michelle.trails

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.michelle.trails.domain.HealthPermissionMode
import dev.michelle.trails.health.HealthPermissionContract
import dev.michelle.trails.health.HealthConnectSetup
import dev.michelle.trails.presentation.SupportEvent
import dev.michelle.trails.presentation.SupportUiState
import dev.michelle.trails.presentation.HealthEvent
import dev.michelle.trails.presentation.HealthUiState
import dev.michelle.trails.presentation.HealthSyncUiState
import dev.michelle.trails.presentation.HealthSyncEvent
import androidx.compose.ui.platform.LocalContext
import dev.michelle.trails.presentation.JournalEvent
import dev.michelle.trails.presentation.JournalUiState
import java.time.LocalDate

@Composable
internal fun JournalRoute(state: JournalUiState, healthState: HealthUiState, onEvent: (JournalEvent) -> Unit, onHealthEvent: (HealthEvent) -> Unit,
    healthSyncState: HealthSyncUiState, onHealthSyncEvent: (HealthSyncEvent) -> Unit,
    supportState: SupportUiState, onSupportEvent: (SupportEvent) -> Unit) {
    val context = LocalContext.current
    val resolver = context.applicationContext.contentResolver
    var showHealthPanel by rememberSaveable { mutableStateOf(false) }
    var showSyncHistory by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(HealthPermissionContract()) {
        onHealthEvent(HealthEvent.Refresh)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onEvent(JournalEvent.BackupStatusRefresh)
                onHealthEvent(HealthEvent.Refresh)
                onHealthSyncEvent(HealthSyncEvent.ActiveChanged(true))
            }
            if (event == Lifecycle.Event.ON_PAUSE) onHealthSyncEvent(HealthSyncEvent.ActiveChanged(false))
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            onHealthSyncEvent(HealthSyncEvent.ActiveChanged(false))
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { onEvent(JournalEvent.ExportRequested(AndroidBackupDocument(resolver, it))) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onEvent(JournalEvent.ImportRequested(AndroidBackupDocument(resolver, it))) }
    }
    val diagnosticsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { onSupportEvent(SupportEvent.ExportRequested(AndroidBackupDocument(resolver, it))) }
    }
    JournalScreen(
        state = state,
        onEvent = onEvent,
        onExport = { exportLauncher.launch("trail-journal-${LocalDate.now()}.json") },
        onImport = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
        onOpenSyncHistory = { showSyncHistory = true; onHealthSyncEvent(HealthSyncEvent.Refresh) },
        onOpenHealth = { showHealthPanel = true; onHealthEvent(HealthEvent.Refresh); onHealthSyncEvent(HealthSyncEvent.Refresh) },
        healthStatuses = healthSyncState.report.activities,
        onRetryHealth = { onHealthSyncEvent(HealthSyncEvent.RetryActivity(it)) },
        onShareHealth = { walk -> onHealthEvent(HealthEvent.ShareRequested(walk)); onHealthEvent(HealthEvent.Refresh) },
    )
    if (showHealthPanel) {
        HealthConnectionsDialog(
            state = healthState,
            onEvent = onHealthEvent,
            onRequestPermission = { permissionLauncher.launch(it) },
            onSetup = { HealthConnectSetup.open(context) },
            onViewData = { HealthConnectSetup.openData(context) },
            syncState = healthSyncState,
            onSyncEvent = onHealthSyncEvent,
            needsStartTime = state.entries.filter { it.id in healthSyncState.report.needsStartTimeIds },
            onDismiss = { showHealthPanel = false },
            onOpenHistory = { showHealthPanel = false; showSyncHistory = true; onHealthSyncEvent(HealthSyncEvent.Refresh) },
        )
    }
    if (showSyncHistory) {
        SyncHistoryDialog(healthSyncState, supportState, onHealthSyncEvent, onSupportEvent,
            onSetup = { HealthConnectSetup.open(context) },
            onEdit = { id ->
                state.entries.find { it.id == id }?.let { walk ->
                    showSyncHistory = false
                    onEvent(JournalEvent.EditRequested(walk))
                }
            }, onDismiss = { showSyncHistory = false })
    }
    if (supportState.preview != null) {
        SupportDiagnosticsDialog(supportState, onSupportEvent) {
            diagnosticsLauncher.launch("trail-journal-diagnostics-${LocalDate.now()}.txt")
        }
    }
    if (healthState.shareWalk != null) {
        ShareHealthDialog(healthState, onHealthEvent, { permissionLauncher.launch(it) }, { HealthConnectSetup.open(context) })
    }
    healthState.sharedActivityTitle?.let { title ->
        HealthShareResultDialog(
            title = title,
            onViewData = { HealthConnectSetup.openData(context) },
            onDismiss = { onHealthEvent(HealthEvent.ShareResultDismissed) },
        )
    }
}
