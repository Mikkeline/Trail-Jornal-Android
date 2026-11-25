package dev.michelle.trails.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ActivityTimingTest {
    private val now = Instant.parse("2026-06-01T12:00:00Z")
    private val validator = ValidateWalkDraft(now = { now }, today = { LocalDate.of(2026, 6, 1) })
    private val draft = WalkDraft(title = "Park", date = "2026-01-02", distanceKm = "2", durationMinutes = "30",
        startTime = "09:15", zoneId = "Asia/Kuala_Lumpur")

    @Test fun `chosen time and zone survive validation and editing conversion`() {
        val value = validator.execute(draft).value!!
        assertEquals(LocalTime.of(9, 15), value.startTime)
        val walk = Walk("one", value.title, value.date, value.meters, value.minutes, value.notes,
            startTime = value.startTime, zoneId = value.zoneId)
        assertEquals("09:15", WalkDraft.from(walk).startTime)
        assertEquals("Asia/Kuala_Lumpur", WalkDraft.from(walk).zoneId)
    }

    @Test fun `legacy entries without a time remain valid`() {
        assertNotNull(validator.execute(draft.copy(startTime = "", zoneId = "")).value)
    }

    @Test fun `future finish invalid time and invalid zone produce a field error`() {
        for (invalid in listOf(draft.copy(date = "2026-06-01", startTime = "20:00"), draft.copy(startTime = "25:00"), draft.copy(zoneId = "invalid/zone"))) {
            assertNotNull(validator.execute(invalid).errors[FormField.START_TIME])
        }
    }

    @Test fun `daylight saving gaps and repeated times require an unambiguous choice`() {
        for (invalid in listOf(draft.copy(date = "2026-03-29", startTime = "02:30", zoneId = "Europe/Berlin"),
            draft.copy(date = "2025-10-26", startTime = "02:30", zoneId = "Europe/Berlin"))) {
            assertNotNull(validator.execute(invalid).errors[FormField.START_TIME])
        }
    }
}
