package dev.michelle.trails.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.michelle.trails.domain.*
import java.time.LocalDate
import java.time.LocalTime
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthSyncStorageTest {
    private lateinit var database: TrailDatabase
    private lateinit var queue: RoomHealthSyncQueue
    private lateinit var repository: RoomWalkRepository
    private var clock = 1000L
    private val legacy = object : HealthSyncStore {
        override fun enabled() = false
        override fun setEnabled(enabled: Boolean) = Unit
        override fun targets() = emptyList<HealthSyncTarget>()
        override fun save(target: HealthSyncTarget) = Unit
    }
    private val walk = Walk("one", "Park", LocalDate.of(2026, 1, 2), 2000, 30, "",
        startTime = LocalTime.of(9, 0), zoneId = "Asia/Kuala_Lumpur")

    @Before fun open() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TrailDatabase::class.java).build()
        queue = RoomHealthSyncQueue(database, legacy, now = { clock })
        repository = RoomWalkRepository(database.walkDao(), queue)
        queue.setEnabled(true)
    }
    @After fun close() = database.close()

    @Test fun savingAndQueueingAreOneTransactionAndSurviveQueueRecreation() {
        repository.save(walk)
        val snapshot = RoomHealthSyncQueue(database, legacy).snapshot()
        assertEquals(walk, snapshot.walks.single())
        assertEquals(HealthJobOperation.UPSERT, snapshot.jobs.single().operation)
    }

    @Test fun queueFailureRollsBackJournalInsert() {
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_job BEFORE INSERT ON health_sync BEGIN SELECT RAISE(ABORT, 'Queue failed'); END")
        assertThrows(Exception::class.java) { repository.save(walk) }
        assertNull(repository.find(walk.id))
    }

    @Test fun undoSupersedesDeletionAndStaleAcknowledgements() {
        repository.save(walk)
        repository.delete(walk.id)
        val deletion = queue.snapshot().jobs.single()
        assertEquals(HealthJobOperation.DELETE, deletion.operation)
        assertEquals(clock + RoomHealthSyncQueue.DELETE_GRACE_MS, deletion.notBefore)
        repository.save(walk)
        queue.complete(deletion, clock)
        val restored = queue.snapshot().jobs.single()
        assertEquals(HealthJobOperation.UPSERT, restored.operation)
        assertEquals(HealthJobState.PENDING, restored.state)
        assertTrue(restored.revision > deletion.revision)
    }

    @Test fun privateEditsDoNotQueueAnotherExportAndImportedRecordsAreExcluded() {
        repository.save(walk)
        val original = queue.snapshot().jobs.single()
        queue.complete(original, clock)
        repository.update(walk.copy(notes = "Private changes", effort = Effort.HARD))
        assertEquals(original.revision, queue.snapshot().jobs.single().revision)
        repository.save(walk.copy(id = "health-connect:original"))
        assertEquals(1, queue.snapshot().jobs.size)
    }

    @Test fun backupMergeIsAtomicAcrossActivitiesAndSyncJobs() {
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_second BEFORE INSERT ON health_sync WHEN NEW.walkId = 'second' BEGIN SELECT RAISE(ABORT, 'Queue failed'); END")
        assertThrows(Exception::class.java) { repository.mergeMissing(listOf(walk, walk.copy(id = "second"))) }
        assertTrue(repository.all().isEmpty())
        assertTrue(queue.snapshot().jobs.isEmpty())
    }

    @Test fun legacyTimingAndConsentAreImportedExactlyOnce() {
        database.healthSyncDao().configure(HealthSyncSettingsEntity(enabled = false))
        database.walkDao().insert(walk.copy(startTime = null, zoneId = null).toEntity())
        database.openHelper.writableDatabase.execSQL("DELETE FROM health_sync_settings")
        val previous = object : HealthSyncStore {
            override fun enabled() = true
            override fun setEnabled(enabled: Boolean) = Unit
            override fun targets() = listOf(HealthSyncTarget("one", "09:15", "Asia/Kuala_Lumpur", "old-signature"))
            override fun save(target: HealthSyncTarget) = Unit
        }
        val migrated = RoomHealthSyncQueue(database, previous)
        migrated.initialize()
        assertTrue(migrated.snapshot().enabled)
        assertEquals(LocalTime.of(9, 15), repository.find("one")?.startTime)
        migrated.setEnabled(false)
        migrated.initialize()
        assertFalse(migrated.snapshot().enabled)
    }

    @Test fun schedulerFailureDoesNotTurnACommittedSaveIntoAFailedSaveRetry() {
        val unavailable = RoomHealthSyncQueue(database, legacy, schedule = { error("Scheduler unavailable") })
        RoomWalkRepository(database.walkDao(), unavailable).save(walk)
        assertEquals(walk, repository.find("one"))
        assertEquals(HealthJobState.PENDING, queue.snapshot().jobs.single().state)
    }

    @Test fun explicitRemovalRetryBypassesFailureDelayButPreservesTheOriginalUndoGrace() {
        repository.save(walk)
        repository.delete(walk.id)
        val removal = queue.snapshot().jobs.single()
        queue.retry(walk.id)
        assertEquals(removal.notBefore, queue.snapshot().jobs.single().notBefore)
        clock = removal.notBefore
        queue.fail(removal, "Provider unavailable", false, clock + 60_000)
        queue.retry(walk.id)
        assertEquals(0L, queue.snapshot().jobs.single().notBefore)
    }
}
