package dev.michelle.trails.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class HealthSyncHistoryTest {
    private val now = 100_000L
    private val access = HealthAccess(HealthAvailability.AVAILABLE, true, true)
    private val walk = Walk("one", "Park", LocalDate.of(2026, 1, 2), 2000, 30, "Private",
        startTime = LocalTime.of(9, 0), zoneId = "UTC")
    private val job = HealthSyncJob("one", HealthJobOperation.UPSERT, 1, 50_000, true, 0)
    private fun project(value: HealthSyncJob = job, walks: List<Walk> = listOf(walk), enabled: Boolean = true,
        permissions: HealthAccess = access) = HealthSyncHistory.project(HealthQueueSnapshot(enabled, walks, listOf(value)), permissions, now).single()

    @Test fun `pending exports have an explicit action and no invented success time`() {
        val entry = project()
        assertEquals(SyncHistoryState.PENDING, entry.state)
        assertTrue(entry.canRetry); assertNull(entry.lastSyncedAt); assertNull(entry.retryAt)
    }

    @Test fun `deletion remains visible after the journal row is gone and respects undo grace`() {
        val entry = project(job.copy(operation = HealthJobOperation.DELETE, automatic = false, notBefore = now + 30_000), walks = emptyList())
        assertEquals(SyncHistoryState.UNDO_GRACE, entry.state)
        assertNull(entry.title); assertFalse(entry.canRetry)
        assertEquals(now + 30_000, entry.retryAt)
    }

    @Test fun `due deletion is actionable even when automatic sharing is disabled`() {
        val entry = project(job.copy(operation = HealthJobOperation.DELETE, automatic = false), walks = emptyList(), enabled = false)
        assertEquals(SyncHistoryState.REMOVAL_PENDING, entry.state)
        assertTrue(entry.canRetry)
    }

    @Test fun `successful removal remains visible with its accepted timestamp`() {
        val entry = project(job.copy(operation = HealthJobOperation.DELETE, state = HealthJobState.DELETED, lastSyncedAt = 90_000), walks = emptyList())
        assertEquals(SyncHistoryState.REMOVED, entry.state)
        assertEquals(90_000L, entry.lastSyncedAt); assertTrue(entry.complete); assertFalse(entry.canRetry)
    }

    @Test fun `revoked permission and unavailable provider have different guidance`() {
        assertEquals(SyncHistoryState.NEEDS_PERMISSION, project(permissions = HealthAccess(HealthAvailability.AVAILABLE)).state)
        assertEquals(SyncHistoryState.UNAVAILABLE, project(permissions = HealthAccess(HealthAvailability.UPDATE_REQUIRED)).state)
        assertFalse(project(permissions = HealthAccess(HealthAvailability.AVAILABLE)).canRetry)
    }

    @Test fun `paused automation can be explicitly retried without claiming a success`() {
        val entry = project(enabled = false)
        assertEquals(SyncHistoryState.PAUSED, entry.state); assertTrue(entry.canRetry); assertFalse(entry.complete)
    }

    @Test fun `missing timing directs the user to editing rather than retrying an invalid export`() {
        val entry = project(walks = listOf(walk.copy(startTime = null, zoneId = null)))
        assertEquals(SyncHistoryState.NEEDS_TIME, entry.state); assertFalse(entry.canRetry)
    }

    @Test fun `failure retains deadline and failure count while blocked work remains actionable`() {
        val failed = project(job.copy(state = HealthJobState.FAILED, attempts = 2, error = "Provider unavailable", notBefore = now + 60_000))
        assertEquals(SyncHistoryState.FAILED, failed.state)
        assertEquals(2, failed.failedAttempts); assertEquals(now + 60_000, failed.retryAt)
        assertTrue(project(job.copy(state = HealthJobState.BLOCKED)).canRetry)
    }

    @Test fun `pending filters include deletions and completed filters include accepted removals`() {
        val pending = project(job.copy(operation = HealthJobOperation.DELETE, automatic = false), walks = emptyList())
        val removed = project(job.copy(state = HealthJobState.DELETED, operation = HealthJobOperation.DELETE), walks = emptyList())
        assertTrue(SyncHistoryFilter.PENDING.matches(pending)); assertFalse(SyncHistoryFilter.COMPLETED.matches(pending))
        assertTrue(SyncHistoryFilter.COMPLETED.matches(removed)); assertFalse(SyncHistoryFilter.PENDING.matches(removed))
    }

    @Test fun `foreign imported identities never appear as this apps sync history`() {
        assertTrue(HealthSyncHistory.project(HealthQueueSnapshot(true, emptyList(), listOf(job.copy(walkId = "health-connect:original"))), access, now).isEmpty())
    }

    @Test fun `manual retry preserves initial deletion grace but bypasses failed removal backoff`() {
        val removal = job.copy(operation = HealthJobOperation.DELETE, automatic = false, notBefore = now + 30_000)
        assertEquals(now + 30_000, removal.manualRetryNotBefore())
        assertEquals(0L, removal.copy(state = HealthJobState.FAILED).manualRetryNotBefore())
        assertEquals(0L, removal.copy(state = HealthJobState.BLOCKED).manualRetryNotBefore())
    }
}
