package dev.michelle.trails.presentation

import dev.michelle.trails.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test

class HealthControllerTest {
    private class FakeStore : WalkReader, WalkImporter {
        var records = emptyList<Walk>()
        override fun all() = records
        override fun mergeMissing(entries: List<Walk>): ImportReport { records += entries; return ImportReport(entries.size, 0) }
    }
    private class Fixture : AutoCloseable {
        var access = HealthAccess(HealthAvailability.AVAILABLE, true, true)
        var writes = 0
        var failRead = false
        var failWrite = false
        val store = FakeStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        private val reader = object : HealthWorkoutReader {
            override suspend fun recentWorkouts(): ExternalWorkouts {
                if (failRead) error("Provider unavailable")
                val item = ExternalWorkout("1", "watch.app", "Walk", ActivityType.OUTDOOR_WALK, Instant.parse("2026-01-02T01:00:00Z"), Instant.parse("2026-01-02T01:30:00Z"), 0, 2000.0)
                return ExternalWorkouts(listOf(item), 0, false)
            }
        }
        private val writer = object : HealthWorkoutWriter {
            override suspend fun share(workout: SharedWorkout) {
                if (failWrite) error("Health Connect rejected the write")
                writes++
            }
        }
        val controller = HealthController(
            object : HealthAccessReader { override suspend fun access() = access },
            JournalHealth(reader, writer, store, store) { Instant.parse("2026-01-03T12:00:00Z") },
            scope, Dispatchers.Unconfined, { ZoneId.of("UTC") },
        )
        override fun close() = scope.cancel()
    }

    @Test fun `denied read permission produces an error and leaves the journal intact`() {
        Fixture().use { it.access = HealthAccess(HealthAvailability.AVAILABLE); it.controller.dispatch(HealthEvent.PreviewRequested); assertNotNull(it.controller.state.value.error); assertTrue(it.store.records.isEmpty()) }
    }
    @Test fun `preview does not import without confirmation`() {
        Fixture().use { it.controller.dispatch(HealthEvent.PreviewRequested); assertEquals(1, it.controller.state.value.preview?.candidates?.size); assertTrue(it.store.records.isEmpty()); it.controller.dispatch(HealthEvent.PreviewDismissed); assertTrue(it.store.records.isEmpty()) }
    }
    @Test fun `confirmed selection imports and clears the preview`() {
        Fixture().use { it.controller.dispatch(HealthEvent.PreviewRequested); it.controller.dispatch(HealthEvent.ImportConfirmed); assertEquals(1, it.store.records.size); assertNull(it.controller.state.value.preview) }
    }
    @Test fun `provider errors clear busy state without importing`() {
        Fixture().use { it.failRead = true; it.controller.dispatch(HealthEvent.PreviewRequested); assertEquals("Provider unavailable", it.controller.state.value.error); assertFalse(it.controller.state.value.isBusy); assertTrue(it.store.records.isEmpty()) }
    }
    @Test fun `sharing requires an actual chosen start time`() {
        Fixture().use {
            val walk = Walk("manual", "Park", LocalDate.of(2026, 1, 2), 2000, 30, "")
            it.controller.dispatch(HealthEvent.ShareRequested(walk)); it.controller.dispatch(HealthEvent.ShareConfirmed)
            assertEquals(0, it.writes); assertNotNull(it.controller.state.value.error)
            it.controller.dispatch(HealthEvent.StartTimeSelected("09:00")); it.controller.dispatch(HealthEvent.ShareConfirmed)
            assertEquals(1, it.writes); assertNull(it.controller.state.value.shareWalk)
        }
    }
    @Test fun `revoked sharing permission prevents a health write`() {
        Fixture().use {
            it.access = HealthAccess(HealthAvailability.AVAILABLE, true, false)
            it.controller.dispatch(HealthEvent.ShareRequested(Walk("manual", "Park", LocalDate.of(2026, 1, 2), 2000, 30, "")))
            it.controller.dispatch(HealthEvent.StartTimeSelected("09:00")); it.controller.dispatch(HealthEvent.ShareConfirmed)
            assertEquals(0, it.writes); assertNotNull(it.controller.state.value.error)
        }
    }

    @Test fun `revoked reading permission prevents confirmation of an old preview`() {
        Fixture().use {
            it.controller.dispatch(HealthEvent.PreviewRequested)
            it.access = HealthAccess(HealthAvailability.AVAILABLE, false, true)
            it.controller.dispatch(HealthEvent.ImportConfirmed)
            assertTrue(it.store.records.isEmpty())
            assertNotNull(it.controller.state.value.error)
        }
    }

    @Test fun `granting permissions and refreshing does not silently transfer data`() {
        Fixture().use {
            it.controller.dispatch(HealthEvent.Refresh)
            assertTrue(it.controller.state.value.access.canWrite)
            assertEquals(0, it.writes)
            assertTrue(it.store.records.isEmpty())
        }
    }

    @Test fun `successful share receipt survives returning from health settings until dismissed`() {
        Fixture().use {
            it.controller.dispatch(HealthEvent.ShareRequested(Walk("manual", "Park", LocalDate.of(2026, 1, 2), 2000, 30, "")))
            it.controller.dispatch(HealthEvent.StartTimeSelected("09:00"))
            it.controller.dispatch(HealthEvent.ShareConfirmed)
            assertEquals("Park", it.controller.state.value.sharedActivityTitle)
            assertNull(it.controller.state.value.shareWalk)
            val message = it.controller.state.value.message
            assertNotNull(message)
            it.controller.dispatch(HealthEvent.Refresh)
            assertEquals("Park", it.controller.state.value.sharedActivityTitle)
            assertEquals(message, it.controller.state.value.message)
            assertEquals(1, it.writes)
            it.controller.dispatch(HealthEvent.ShareResultDismissed)
            assertNull(it.controller.state.value.sharedActivityTitle)
        }
    }

    @Test fun `rejected write keeps share dialog open and never reports delivery`() {
        Fixture().use {
            it.failWrite = true
            it.controller.dispatch(HealthEvent.ShareRequested(Walk("manual", "Park", LocalDate.of(2026, 1, 2), 2000, 30, "")))
            it.controller.dispatch(HealthEvent.StartTimeSelected("09:00"))
            it.controller.dispatch(HealthEvent.ShareConfirmed)
            assertNotNull(it.controller.state.value.shareWalk)
            assertNull(it.controller.state.value.sharedActivityTitle)
            assertNull(it.controller.state.value.message)
            assertEquals("Health Connect rejected the write", it.controller.state.value.error)
            assertEquals(0, it.writes)
        }
    }
}
