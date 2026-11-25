package dev.michelle.trails.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class JournalHealthTest {
    private val now = Instant.parse("2026-01-03T12:00:00Z")
    private val external = ExternalWorkout("record-1", "watch.app", "Morning walk", ActivityType.OUTDOOR_WALK, Instant.parse("2026-01-02T01:00:00Z"), Instant.parse("2026-01-02T01:30:01Z"), 8 * 3600, 2500.0)
    private class Store : WalkReader, WalkImporter {
        var entries = emptyList<Walk>()
        override fun all() = entries
        override fun mergeMissing(entries: List<Walk>): ImportReport {
            val known = this.entries.map { it.id }.toSet()
            val added = entries.filter { it.id !in known }
            this.entries += added
            return ImportReport(added.size, entries.size - added.size)
        }
    }
    private fun service(store: Store, values: List<ExternalWorkout> = listOf(external)): JournalHealth {
        val reader = object : HealthWorkoutReader { override suspend fun recentWorkouts() = ExternalWorkouts(values, 0, false) }
        val writer = object : HealthWorkoutWriter { override suspend fun share(workout: SharedWorkout) = Unit }
        return JournalHealth(reader, writer, store, store) { now }
    }
    private fun manual() = Walk("manual-id", "Park", LocalDate.of(2026, 1, 2), 2500, 30, "")

    @Test fun `import preview retains real date distance and clearly rounds elapsed duration`() = runBlocking {
        val plan = service(Store()).preview()
        val walk = plan.candidates.single().walk
        assertEquals(LocalDate.of(2026, 1, 2), walk.date)
        assertEquals(2500, walk.distanceMeters)
        assertEquals(31, walk.durationMinutes)
        assertTrue(walk.notes.contains("Pauses are included"))
        val fractional = external.copy(end = Instant.parse("2026-01-02T01:30:00.500Z"))
        assertEquals(31, service(Store(), listOf(fractional)).preview().candidates.single().walk.durationMinutes)
    }

    @Test fun `missing distance and invalid elapsed sessions are skipped not invented`() = runBlocking {
        val plan = service(Store(), listOf(external.copy(distanceMeters = null), external.copy(end = external.start), external.copy(distanceMeters = Double.NaN))).preview()
        assertTrue(plan.candidates.isEmpty())
        assertEquals(3, plan.skipped)
    }

    @Test fun `repeat import keeps the same id and does not duplicate activities`() = runBlocking {
        val store = Store(); val service = service(store)
        val plan = service.preview()
        service.import(plan, plan.candidates.map { it.walk.id }.toSet())
        val repeated = service.preview()
        assertTrue(repeated.candidates.isEmpty())
        assertEquals(1, repeated.existing)
        assertEquals(1, store.entries.size)
    }

    @Test fun `selection imports only the chosen workouts`() = runBlocking {
        val store = Store(); val service = service(store, listOf(external, external.copy(recordId = "record-2")))
        val plan = service.preview()
        assertEquals(ImportReport(1, 0), service.import(plan, setOf(plan.candidates.first().walk.id)))
        assertEquals(1, store.entries.size)
    }

    @Test fun `sharing uses chosen start time and the saved duration`() {
        val workout = service(Store()).prepareShare(manual(), LocalTime.of(9, 15), ZoneId.of("Asia/Kuala_Lumpur"))
        assertEquals(Instant.parse("2026-01-02T01:15:00Z"), workout.start)
        assertEquals(Instant.parse("2026-01-02T01:45:00Z"), workout.end)
    }

    @Test fun `a future end cannot be published as a completed workout`() {
        assertThrows(IllegalArgumentException::class.java) { service(Store()).prepareShare(manual().copy(date = LocalDate.of(2026, 1, 4)), LocalTime.NOON, ZoneId.of("UTC")) }
    }

    @Test fun `imported workouts cannot be copied back into the source store`() {
        val imported = manual().copy(id = HealthRecordIdentity.id("watch.app", "record-1"))
        assertThrows(IllegalArgumentException::class.java) { service(Store()).prepareShare(imported, LocalTime.NOON, ZoneId.of("UTC")) }
    }

    @Test fun `ambiguous daylight saving times require a different explicit start time`() {
        val walk = manual().copy(date = LocalDate.of(2025, 11, 2))
        assertThrows(IllegalArgumentException::class.java) { service(Store()).prepareShare(walk, LocalTime.of(1, 30), ZoneId.of("America/New_York")) }
    }
}
