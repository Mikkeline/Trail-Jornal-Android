package dev.michelle.trails.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SupportDiagnosticsTest {
    private class Sync : HealthSyncEngine {
        var inspections = 0
        var failure: Exception? = null
        var report = HealthSyncReport(true, 0, 0, access = HealthAccess(HealthAvailability.AVAILABLE, true, false))
        override suspend fun inspect(): HealthSyncReport { inspections++; failure?.let { throw it }; return report }
        override suspend fun synchronize(): HealthSyncReport = error("Diagnostics must not trigger sync")
        override suspend fun setEnabled(enabled: Boolean) = error("Diagnostics must not change settings")
        override suspend fun share(walk: Walk, startTime: LocalTime, zone: ZoneId) = error("Diagnostics must not share")
    }
    private val environment = object : AppEnvironmentReader { override fun read() = AppEnvironment("0.8.0", 10, 35) }
    private val now = Instant.parse("2026-11-25T06:00:00Z")

    @Test fun `diagnostics uses read only inspection and only allowlisted keys`() = runBlocking {
        val sync = Sync()
        val text = SupportDiagnostics(environment, sync) { now }.prepare()
        assertEquals(1, sync.inspections)
        assertTrue(text.contains("app_version: 0.8.0")); assertTrue(text.contains("exercise_distance_write: not_allowed"))
        assertEquals(setOf("format_version", "generated_utc", "app_version", "app_version_code", "android_api", "sync_inspection",
            "health_connect", "exercise_distance_read", "exercise_distance_write", "automatic_sharing", "pending_work", "needs_permission",
            "needs_start_time", "provider_retry_needed", "user_attention_needed", "undo_grace_pending", "raw_errors_included", "activity_details_included"),
            text.lineSequence().filter { ": " in it }.map { it.substringBefore(": ") }.toSet())
    }

    @Test fun `activity identifiers titles errors dates locations and values cannot leak through report serialization`() = runBlocking {
        val sync = Sync()
        val secrets = listOf("secret-journal-id", "Private mountain title", "SECRET_NOTES_私密", "geo:12.34567,98.76543", "activity-date=2025-03-04", "distance=987654")
        val sensitive = secrets.joinToString(" | ")
        sync.report = sync.report.copy(error = sensitive, needsStartTimeIds = setOf(secrets.first()),
            activities = mapOf(secrets.first() to ActivityHealthStatus(ActivityHealthState.FAILED, error = sensitive)),
            history = listOf(SyncHistoryEntry(secrets.first(), sensitive, HealthJobOperation.UPSERT, SyncHistoryState.FAILED,
                1234, 5678, 999, sensitive, true)))
        val text = SupportDiagnostics(environment, sync) { now }.prepare()
        secrets.forEach { assertFalse("Leaked $it", text.contains(it)) }
        assertTrue(text.contains("provider_retry_needed: yes"))
        assertFalse(text.contains("failed_attempts")); assertFalse(text.contains("last_synced"))
    }

    @Test fun `failed inspection produces useful unavailable flags without raw exception text`() = runBlocking {
        val sync = Sync().apply { failure = IllegalStateException("secret-file-path-or-provider-payload") }
        val text = SupportDiagnostics(environment, sync) { now }.prepare()
        assertTrue(text.contains("sync_inspection: unavailable"))
        assertTrue(text.contains("health_connect: UNKNOWN"))
        assertFalse(text.contains("secret-file-path-or-provider-payload"))
    }

    @Test fun `cancellation does not create a misleading successful diagnostic snapshot`() = runBlocking {
        val sync = Sync().apply { failure = CancellationException("Stopped") }
        try { SupportDiagnostics(environment, sync).prepare(); fail("Expected cancellation") } catch (_: CancellationException) { }
    }
}
