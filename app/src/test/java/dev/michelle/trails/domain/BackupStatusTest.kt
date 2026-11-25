package dev.michelle.trails.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class BackupStatusTest {
    private val walk = Walk("one", "Park", LocalDate.of(2026, 1, 2), 2000, 30, "Private notes")
    private val store = MemoryBackupStatusStore()
    private var clock = Instant.parse("2026-01-03T12:00:00Z")
    private val monitor = BackupMonitor(store) { clock }
    private fun record(entries: List<Walk>) = monitor.recorded(PreparedBackup("file contents", BackupFingerprint.of(entries), entries.size))

    @Test fun `empty journal does not nag but a first unsaved activity gets a reminder`() {
        assertFalse(monitor.overview(emptyList()).reminderDue)
        assertTrue(monitor.overview(listOf(walk)).reminderDue)
    }

    @Test fun `completed export records time and actual content and clears reminder`() {
        record(listOf(walk))
        val overview = monitor.overview(listOf(walk))
        assertEquals(clock, overview.receipt?.createdAt)
        assertEquals(1, overview.receipt?.entries)
        assertFalse(overview.hasUnbackedChanges)
        assertFalse(overview.reminderDue)
    }

    @Test fun `unchanged old backup does not generate repeated reminders`() {
        record(listOf(walk)); clock = clock.plusSeconds(60L * 86400)
        assertFalse(monitor.overview(listOf(walk)).reminderDue)
    }

    @Test fun `changed content is visible immediately but reminder waits seven days`() {
        record(listOf(walk))
        val changed = listOf(walk.copy(notes = "New private notes"))
        assertTrue(monitor.overview(changed).hasUnbackedChanges)
        assertFalse(monitor.overview(changed).reminderDue)
        clock = clock.plus(BackupMonitor.REMINDER_INTERVAL)
        assertTrue(monitor.overview(changed).reminderDue)
    }

    @Test fun `snooze survives monitor recreation and expires after seven days`() {
        monitor.snooze()
        val reopened = BackupMonitor(store) { clock }
        assertFalse(reopened.overview(listOf(walk)).reminderDue)
        clock = clock.plus(BackupMonitor.REMINDER_INTERVAL)
        assertTrue(reopened.overview(listOf(walk)).reminderDue)
    }

    @Test fun `reminders can be switched off without losing backup receipt`() {
        record(listOf(walk)); monitor.reminders(false)
        clock = clock.plus(BackupMonitor.REMINDER_INTERVAL)
        val overview = monitor.overview(listOf(walk.copy(distanceMeters = 3000)))
        assertFalse(overview.reminderDue)
        assertNotNull(overview.receipt)
        assertTrue(overview.hasUnbackedChanges)
    }

    @Test fun `fresh export clears snooze and uses the exported snapshot rather than current entries`() {
        monitor.snooze(); record(listOf(walk))
        val overview = monitor.overview(listOf(walk, walk.copy(id = "two")))
        assertNull(overview.snoozedUntil)
        assertEquals(1, overview.receipt?.entries)
        assertTrue(overview.hasUnbackedChanges)
    }

    @Test fun `deleting the final entry still marks a previous backup as out of date`() {
        record(listOf(walk))
        assertTrue(monitor.overview(emptyList()).hasUnbackedChanges)
    }

    @Test fun `fingerprint is independent of ordering and includes private fields and timing`() {
        val another = walk.copy(id = "two")
        assertEquals(BackupFingerprint.of(listOf(walk, another)), BackupFingerprint.of(listOf(another, walk)))
        for (changed in listOf(walk.copy(title = "Hill"), walk.copy(notes = "Changed"), walk.copy(location = "New venue"),
            walk.copy(point = GeoPoint(0.0, 0.0)), walk.copy(effort = Effort.HARD), walk.copy(date = walk.date.plusDays(1)),
            walk.copy(startTime = LocalTime.of(9, 15), zoneId = "Asia/Kuala_Lumpur"))) {
            assertNotEquals(BackupFingerprint.of(listOf(walk)), BackupFingerprint.of(listOf(changed)))
        }
    }
}
