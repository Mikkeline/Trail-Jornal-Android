package dev.michelle.trails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.michelle.trails.domain.DiagnosticsReader
import dev.michelle.trails.presentation.SupportController
import dev.michelle.trails.presentation.SupportEvent
import dev.michelle.trails.domain.BackupMonitor
import dev.michelle.trails.domain.JournalHealth
import dev.michelle.trails.domain.HealthSyncEngine
import dev.michelle.trails.presentation.HealthSyncController
import dev.michelle.trails.presentation.HealthSyncEvent
import dev.michelle.trails.domain.HealthAccessReader
import dev.michelle.trails.presentation.HealthController
import dev.michelle.trails.presentation.HealthEvent
import dev.michelle.trails.domain.JournalBackups
import dev.michelle.trails.domain.RestoreDeletedWalk
import dev.michelle.trails.presentation.JournalPreferences
import dev.michelle.trails.domain.AddWalk
import dev.michelle.trails.domain.DeleteWalk
import dev.michelle.trails.domain.EditWalk
import dev.michelle.trails.domain.WalkObserver
import dev.michelle.trails.presentation.JournalController
import dev.michelle.trails.presentation.JournalEvent
import kotlinx.coroutines.Dispatchers

class JournalViewModel(
    observer: WalkObserver,
    addWalk: AddWalk,
    editWalk: EditWalk,
    deleteWalk: DeleteWalk,
    restoreDeletedWalk: RestoreDeletedWalk,
    backups: JournalBackups,
    preferences: JournalPreferences,
    savedStateHandle: SavedStateHandle,
    health: JournalHealth,
    healthAccess: HealthAccessReader,
    healthAutoSync: HealthSyncEngine,
    backupMonitor: BackupMonitor,
    diagnostics: DiagnosticsReader,
) : ViewModel() {
    private val controller = JournalController(
        observer = observer,
        addWalk = addWalk,
        editWalk = editWalk,
        deleteWalk = deleteWalk,
        restoreDeletedWalk = restoreDeletedWalk,
        backups = backups,
        preferences = preferences,
        backupMonitor = backupMonitor,
        draftStore = SavedStateDraftStore(savedStateHandle),
        scope = viewModelScope,
        ioDispatcher = Dispatchers.IO,
    )
    private val healthSyncController = HealthSyncController(observer, healthAutoSync, viewModelScope, Dispatchers.IO)
    private val healthController = HealthController(healthAccess, health, viewModelScope, Dispatchers.IO, sharer = healthAutoSync)
    private val supportController = SupportController(diagnostics, viewModelScope, Dispatchers.IO)
    val supportState = supportController.state
    fun onSupportEvent(event: SupportEvent) = supportController.dispatch(event)
    val healthSyncState = healthSyncController.state
    val healthState = healthController.state
    val state = controller.state

    fun onHealthEvent(event: HealthEvent) {
        healthController.dispatch(event)
        // Recheck after a completed initial share without initiating another write.
        if (event == HealthEvent.ShareResultDismissed) healthSyncController.dispatch(HealthSyncEvent.Refresh)
    }

    fun onHealthSyncEvent(event: HealthSyncEvent) = healthSyncController.dispatch(event)

    fun onEvent(event: JournalEvent) = controller.dispatch(event)
}
