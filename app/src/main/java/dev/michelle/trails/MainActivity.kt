package dev.michelle.trails

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val journalViewModel: JournalViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = (application as TrailApplication).container
                JournalViewModel(
                    observer = container.repository,
                    addWalk = container.addWalk,
                    editWalk = container.editWalk,
                    deleteWalk = container.deleteWalk,
                    restoreDeletedWalk = container.restoreDeletedWalk,
                    backups = container.backups,
                    preferences = container.preferences,
                    savedStateHandle = createSavedStateHandle(),
                    health = container.health,
                    healthAccess = container.healthAccess,
                    healthAutoSync = container.healthAutoSync,
                    backupMonitor = container.backupMonitor,
                    diagnostics = container.diagnostics,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as TrailApplication).container.startSync()
        enableEdgeToEdge()
        setContent {
            TrailTheme {
                val state = journalViewModel.state.collectAsStateWithLifecycle().value
                val healthState = journalViewModel.healthState.collectAsStateWithLifecycle().value
                val healthSyncState = journalViewModel.healthSyncState.collectAsStateWithLifecycle().value
                val supportState = journalViewModel.supportState.collectAsStateWithLifecycle().value
                JournalRoute(state, healthState, journalViewModel::onEvent, journalViewModel::onHealthEvent,
                    healthSyncState, journalViewModel::onHealthSyncEvent, supportState, journalViewModel::onSupportEvent)
            }
        }
    }
}
