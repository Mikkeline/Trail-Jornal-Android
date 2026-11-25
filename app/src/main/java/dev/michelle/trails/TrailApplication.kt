package dev.michelle.trails

import android.app.Application
import dev.michelle.trails.health.AndroidHealthConnect
import dev.michelle.trails.health.SavedHealthSyncStore
import dev.michelle.trails.domain.QueuedHealthSync
import dev.michelle.trails.data.RoomHealthSyncQueue
import dev.michelle.trails.health.HealthSyncScheduler
import kotlinx.coroutines.*
import dev.michelle.trails.domain.JournalHealth
import dev.michelle.trails.backup.JsonBackupCodec
import dev.michelle.trails.domain.JournalBackups
import dev.michelle.trails.domain.RestoreDeletedWalk
import dev.michelle.trails.data.RoomWalkRepository
import dev.michelle.trails.data.TrailDatabase
import dev.michelle.trails.domain.AddWalk
import dev.michelle.trails.domain.DeleteWalk
import dev.michelle.trails.domain.EditWalk

/** Composition root: concrete storage choices stay out of the ViewModel and screen. */
class TrailApplication : Application() {
    val container: TrailContainer by lazy { TrailContainer(this) }
}

class TrailContainer(application: Application) {
    private val database = TrailDatabase.open(application)
    val healthAccess = AndroidHealthConnect(application)
    private val scheduler = HealthSyncScheduler(application)
    val healthQueue = RoomHealthSyncQueue(database, SavedHealthSyncStore(application), scheduler::schedule, versionFloor = healthAccess::recordVersionFloor)
    val repository = RoomWalkRepository(database.walkDao(), healthQueue)
    val addWalk = AddWalk(repository)
    val editWalk = EditWalk(repository, repository)
    val deleteWalk = DeleteWalk(repository)
    val restoreDeletedWalk = RestoreDeletedWalk(repository)
    val backups = JournalBackups(repository, repository, JsonBackupCodec())
    val preferences = SavedJournalPreferences(application)
    val backupMonitor = dev.michelle.trails.domain.BackupMonitor(SavedBackupStatusStore(application))
    val health = JournalHealth(healthAccess, healthAccess, repository, repository)
    val healthAutoSync = QueuedHealthSync(healthQueue, healthAccess, health, healthAccess)
    val diagnostics = dev.michelle.trails.domain.SupportDiagnostics(AndroidAppEnvironmentReader(application), healthAutoSync)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun startSync() { scope.launch { runCatching { healthQueue.initialize(); scheduler.schedule() } } }
}
