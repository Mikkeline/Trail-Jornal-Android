package dev.michelle.trails.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class QueuedHealthSyncTest {
    private class Queue : HealthSyncQueue {
        var autoEnabled = true
        var walks = listOf(Walk("one", "Park", LocalDate.of(2026, 1, 2), 1000, 20, "",
            startTime = LocalTime.of(9, 0), zoneId = "UTC"))
        var jobs = listOf(HealthSyncJob("one", HealthJobOperation.UPSERT, 1, 100, true, 0))
        override fun snapshot() = HealthQueueSnapshot(autoEnabled, walks, jobs)
        override fun setEnabled(enabled: Boolean) { this.autoEnabled = enabled }
        override fun link(walkId: String, time: LocalTime, zone: ZoneId) {
            require(walks.any { it.id == walkId })
            walks = walks.map { if (it.id == walkId) it.copy(startTime = time, zoneId = zone.id) else it }
            jobs = listOf(jobs.first().copy(automatic = false, state = HealthJobState.PENDING))
        }
        override fun retry(walkId: String?) {
            jobs = jobs.map { if (walkId == null || it.walkId == walkId) it.copy(state = HealthJobState.PENDING, notBefore = 0) else it }
        }
        override fun complete(job: HealthSyncJob, at: Long) {
            jobs = jobs.map { if (it.walkId == job.walkId && it.revision == job.revision)
                it.copy(state = if (job.operation == HealthJobOperation.DELETE) HealthJobState.DELETED else HealthJobState.SYNCED, lastSyncedAt = at, error = null) else it }
        }
        override fun fail(job: HealthSyncJob, error: String, blocked: Boolean, retryAt: Long) {
            jobs = jobs.map { if (it.walkId == job.walkId && it.revision == job.revision)
                it.copy(state = if (blocked) HealthJobState.BLOCKED else HealthJobState.FAILED, attempts = it.attempts + 1, error = error, notBefore = retryAt) else it }
        }
        override fun changes() = emptyFlow<Unit>()
    }
    private class Fixture {
        val queue = Queue()
        var clock = Instant.parse("2026-02-01T12:00:00Z")
        var allowed = true
        var failure: Exception? = null
        var onWrite: () -> Unit = {}
        val writes = mutableListOf<SharedWorkout>()
        val deletions = mutableListOf<String>()
        val health = JournalHealth(
            object : HealthWorkoutReader { override suspend fun recentWorkouts() = ExternalWorkouts(emptyList(), 0, false) },
            object : HealthWorkoutWriter { override suspend fun share(workout: SharedWorkout) { failure?.let { throw it }; writes += workout; onWrite() } },
            object : WalkReader { override fun all() = queue.walks },
            object : WalkImporter { override fun mergeMissing(entries: List<Walk>) = ImportReport(0, 0) }, { clock },
        )
        fun instance() = QueuedHealthSync(queue,
            object : HealthAccessReader { override suspend fun access() = HealthAccess(HealthAvailability.AVAILABLE, false, allowed) },
            health, object : HealthWorkoutDeleter { override suspend fun delete(walkId: String) { failure?.let { throw it }; deletions += walkId } }, { clock })
        val sync = instance()
    }

    @Test fun `new timed entry writes and displays accepted time without repeated writes`() = runBlocking {
        val f = Fixture()
        val report = f.sync.synchronize()
        assertEquals(ActivityHealthState.SYNCED, report.activities["one"]?.state)
        assertEquals(f.clock.toEpochMilli(), report.activities["one"]?.lastSyncedAt)
        assertEquals(100L, f.writes.single().recordVersion)
        f.sync.synchronize(); assertEquals(1, f.writes.size)
    }

    @Test fun `disabled automation keeps new jobs paused but explicit share still works`() = runBlocking {
        val f = Fixture(); f.queue.autoEnabled = false
        assertEquals(ActivityHealthState.PAUSED, f.sync.synchronize().activities["one"]?.state)
        assertTrue(f.writes.isEmpty())
        f.sync.share(f.queue.walks.single(), LocalTime.of(9, 0), ZoneId.of("UTC"))
        assertEquals(1, f.writes.size)
    }

    @Test fun `permission revocation preserves work and exposes actionable status`() = runBlocking {
        val f = Fixture(); f.allowed = false
        val report = f.sync.synchronize()
        assertEquals(ActivityHealthState.NEEDS_PERMISSION, report.activities["one"]?.state)
        assertFalse(report.retryNeeded); assertTrue(f.writes.isEmpty())
        f.allowed = true
        assertEquals(1, f.sync.synchronize().updated)
    }

    @Test fun `failed provider write respects backoff and retries after service recreation`() = runBlocking {
        val f = Fixture(); f.failure = IllegalStateException("Provider unavailable")
        val failed = f.sync.synchronize()
        assertEquals(ActivityHealthState.FAILED, failed.activities["one"]?.state)
        assertTrue(failed.retryNeeded)
        f.failure = null
        assertEquals(0, f.instance().synchronize().updated)
        f.clock = f.clock.plusSeconds(30)
        assertEquals(1, f.instance().synchronize().updated)
        assertEquals(100L, f.writes.single().recordVersion)
    }

    @Test fun `invalid timing blocks background retries until corrected`() = runBlocking {
        val f = Fixture(); f.queue.walks = f.queue.walks.map { it.copy(date = LocalDate.of(2027, 1, 1)) }
        val report = f.sync.synchronize()
        assertEquals(ActivityHealthState.FAILED, report.activities["one"]?.state)
        assertFalse(report.retryNeeded); assertTrue(f.writes.isEmpty())
    }

    @Test fun `missing start time stays local and does not receive a synthetic interval`() = runBlocking {
        val f = Fixture(); f.queue.walks = f.queue.walks.map { it.copy(startTime = null, zoneId = null) }
        val report = f.sync.synchronize()
        assertEquals(ActivityHealthState.NEEDS_TIME, report.activities["one"]?.state)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun `deletion waits for undo grace and removes only the queued identity`() = runBlocking {
        val f = Fixture(); f.queue.walks = emptyList()
        f.queue.jobs = f.queue.jobs.map { it.copy(operation = HealthJobOperation.DELETE, automatic = false, notBefore = f.clock.toEpochMilli() + 30_000) }
        assertTrue(f.sync.synchronize().retryNeeded); assertTrue(f.deletions.isEmpty())
        f.clock = f.clock.plusSeconds(30)
        assertFalse(f.sync.synchronize().retryNeeded)
        assertEquals(listOf("one"), f.deletions)
    }

    @Test fun `undo supersedes deletion and restores latest activity even with automation off`() = runBlocking {
        val f = Fixture(); f.queue.autoEnabled = false
        f.queue.jobs = f.queue.jobs.map { it.copy(revision = 3, recordVersion = 300, automatic = false) }
        f.queue.walks = f.queue.walks.map { it.copy(title = "Restored walk") }
        f.sync.synchronize()
        assertTrue(f.deletions.isEmpty()); assertEquals("Restored walk", f.writes.single().walk.title)
    }

    @Test fun `edit during IPC is not lost by a stale completion acknowledgement`() = runBlocking {
        val f = Fixture()
        f.onWrite = {
            f.queue.jobs = f.queue.jobs.map { it.copy(revision = 2, recordVersion = 200) }
            f.queue.walks = f.queue.walks.map { it.copy(distanceMeters = 3000) }
            f.onWrite = {}
        }
        val first = f.sync.synchronize()
        assertEquals(ActivityHealthState.PENDING, first.activities["one"]?.state)
        f.sync.synchronize()
        assertEquals(3000, f.writes.last().walk.distanceMeters)
        assertEquals(200L, f.writes.last().recordVersion)
    }

    @Test fun `cancellation leaves the job unacknowledged`() = runBlocking {
        val f = Fixture(); f.failure = CancellationException("Stopped")
        try { f.sync.synchronize(); fail("Expected cancellation") } catch (_: CancellationException) { }
        assertEquals(HealthJobState.PENDING, f.queue.jobs.single().state)
    }

    @Test fun `backoff grows and is bounded`() {
        assertEquals(30_000L, QueuedHealthSync.retryDelay(0))
        assertEquals(60_000L, QueuedHealthSync.retryDelay(1))
        assertEquals(21_600_000L, QueuedHealthSync.retryDelay(99))
    }

    @Test fun `inspection shows pending history without sending or acknowledging eligible work`() = runBlocking {
        val f = Fixture()
        val report = f.sync.inspect()
        assertEquals(SyncHistoryState.PENDING, report.history.single().state)
        assertTrue(f.writes.isEmpty()); assertTrue(f.deletions.isEmpty())
        assertEquals(HealthJobState.PENDING, f.queue.jobs.single().state)
        assertEquals(1, f.sync.synchronize().updated)
    }
}
