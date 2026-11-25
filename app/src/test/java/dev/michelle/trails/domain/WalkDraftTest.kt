package dev.michelle.trails.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WalkDraftTest {
    private val today = LocalDate.of(2026, 5, 10)
    private val validator = ValidateWalkDraft { today }
    private fun validDraft() = WalkDraft(title = " Hill walk ", date = "2026-05-09", distanceKm = "2.501", durationMinutes = "30")

    @Test fun `kilometres convert to exact integer metres`() {
        val value = checkNotNull(validator.execute(validDraft()).value)
        assertEquals(2501, value.meters)
        assertEquals("Hill walk", value.title)
    }

    @Test fun `whole kilometres are accepted`() {
        assertEquals(2000, validator.execute(validDraft().copy(distanceKm = "2")).value?.meters)
    }

    @Test fun `minimum and maximum distances are accepted`() {
        assertEquals(1, validator.execute(validDraft().copy(distanceKm = "0.001")).value?.meters)
        assertEquals(1_000_000, validator.execute(validDraft().copy(distanceKm = "1000")).value?.meters)
    }

    @Test fun `invalid distances are rejected without rounding`() {
        for (distance in listOf("0", "-1", "1.0001", "1000.001", "NaN", "1e2", "1,5", "999999999999999")) {
            assertTrue(distance, validator.execute(validDraft().copy(distanceKm = distance)).errors.containsKey(FormField.DISTANCE))
        }
    }

    @Test fun `all invalid fields have independent errors`() {
        val draft = validDraft().copy(title = " ", date = "2026-02-30", distanceKm = "", durationMinutes = "0")
        assertEquals(setOf(FormField.TITLE, FormField.DATE, FormField.DISTANCE, FormField.DURATION), validator.execute(draft).errors.keys)
    }

    @Test fun `future completed activities are rejected`() {
        assertTrue(validator.execute(validDraft().copy(date = "2026-05-11")).errors.containsKey(FormField.DATE))
        assertNotNull(validator.execute(validDraft().copy(date = today.toString())).value)
    }

    @Test fun `optional field length limits apply before persistence`() {
        val draft = validDraft().copy(location = "x".repeat(121), notes = "x".repeat(2001))
        assertEquals(setOf(FormField.LOCATION, FormField.NOTES), validator.execute(draft).errors.keys)
    }

    @Test fun `all activity types and effort choices validate`() {
        for (type in ActivityType.entries) {
            val value = validator.execute(validDraft().copy(type = type, effort = Effort.HARD, location = " Park ")).value!!
            assertEquals(type, value.type)
            assertEquals(Effort.HARD, value.effort)
            assertEquals("Park", value.location)
        }
    }

    @Test fun `draft conversion preserves metadata and exact distance`() {
        val original = Walk("same-id", "Hill", today, 1001, 40, "notes", ActivityType.HIKE, "Local hill", Effort.EASY)
        val draft = WalkDraft.from(original)
        val validated = validator.execute(draft).value!!
        assertEquals("same-id", draft.id)
        assertEquals("1.001", draft.distanceKm)
        assertEquals(original.distanceMeters, validated.meters)
        assertEquals(original.effort, validated.effort)
    }

    private class EditableStore(var entry: Walk?) : WalkFinder, WalkUpdater {
        var writes = 0
        var deletedDuringUpdate = false
        override fun find(id: String): Walk? = entry?.takeIf { it.id == id }
        override fun update(walk: Walk): Boolean {
            if (deletedDuringUpdate || entry == null) return false
            entry = walk
            writes++
            return true
        }
    }

    @Test fun `editing preserves the existing id and updates metadata`() {
        val original = Walk("existing-id", "Old", today, 1000, 20, "")
        val store = EditableStore(original)
        val value = validator.execute(validDraft().copy(type = ActivityType.HIKE, effort = Effort.MODERATE)).value!!
        val result = EditWalk(store, store).execute(original.id, value)
        assertEquals(original.id, result.id)
        assertEquals("Hill walk", result.title)
        assertEquals(ActivityType.HIKE, result.type)
        assertEquals(1, store.writes)
    }

    @Test fun `editing a deleted entry does not insert it again`() {
        val store = EditableStore(null)
        val value = validator.execute(validDraft()).value!!
        assertThrows(IllegalStateException::class.java) { EditWalk(store, store).execute("deleted-id", value) }
        assertEquals(0, store.writes)
    }

    @Test fun `deletion between lookup and update is reported`() {
        val store = EditableStore(Walk("id", "Old", today, 10, 1, ""))
        store.deletedDuringUpdate = true
        assertThrows(IllegalStateException::class.java) { EditWalk(store, store).execute("id", validator.execute(validDraft()).value!!) }
        assertEquals(0, store.writes)
    }
}
