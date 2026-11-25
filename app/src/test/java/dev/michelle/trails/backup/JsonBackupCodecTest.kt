package dev.michelle.trails.backup

import dev.michelle.trails.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class JsonBackupCodecTest {
    private val codec = JsonBackupCodec()
    private val entry = Walk("id-1", "Hill \"walk\"", LocalDate.of(2026, 1, 2), 2501, 30, "保留 notes\nLine two", ActivityType.HIKE, "Park", Effort.MODERATE, GeoPoint(0.0, 0.0))

    @Test fun `all fields unicode quotes and zero coordinates round trip`() {
        assertEquals(listOf(entry), codec.decode(codec.encode(listOf(entry))))
    }

    @Test fun `null optional fields and empty backups round trip`() {
        assertEquals(listOf(entry.copy(point = null, effort = null)), codec.decode(codec.encode(listOf(entry.copy(point = null, effort = null)))))
        assertTrue(codec.decode(codec.encode(emptyList())).isEmpty())
    }

    @Test fun `start time and original zone survive backup restore`() {
        val timed = entry.copy(startTime = java.time.LocalTime.of(9, 15), zoneId = "Asia/Kuala_Lumpur")
        assertEquals(listOf(timed), codec.decode(codec.encode(listOf(timed))))
    }

    @Test fun `version one backup still restores without invented timing`() {
        val legacy = codec.encode(listOf(entry)).replace("\"version\": 2", "\"version\": 1")
            .replace("    \"startTime\": null,\n", "").replace("    \"zoneId\": null,\n", "")
        // Match indentation without depending on the pretty-printer's array nesting depth.
        val stripped = legacy.replace(Regex("(?m)^\\s*\"(?:startTime|zoneId)\": null,\\n"), "")
        assertEquals(listOf(entry), codec.decode(stripped))
    }

    @Test fun `partial or invalid timing is rejected before a restore`() {
        val timed = codec.encode(listOf(entry.copy(startTime = java.time.LocalTime.of(9, 15), zoneId = "Asia/Kuala_Lumpur")))
        for (invalid in listOf(timed.replace("09:15", "25:15"), timed.replace("Asia/Kuala_Lumpur", "bad/zone"),
            timed.replace("\"zoneId\": \"Asia/Kuala_Lumpur\"", "\"zoneId\": null"))) {
            assertThrows(IllegalArgumentException::class.java) { codec.decode(invalid) }
        }
    }

    @Test fun `unsupported format and version fail`() {
        for (text in listOf(codec.encode(listOf(entry)).replace("trail-journal", "other-app"), codec.encode(listOf(entry)).replace("\"version\": 2", "\"version\": 999"))) {
            assertThrows(IllegalArgumentException::class.java) { codec.decode(text) }
        }
    }

    @Test fun `trailing content duplicate fields and unknown fields fail`() {
        val valid = codec.encode(listOf(entry))
        for (text in listOf(valid + " {}", valid.replace("\"format\":", "\"extra\": {}, \"format\":"), valid.replace("\"version\": 2", "\"version\": 2, \"version\": 2"))) {
            assertThrows(IllegalArgumentException::class.java) { codec.decode(text) }
        }
    }

    @Test fun `numeric strings and fractions are not accepted as whole metres`() {
        val valid = codec.encode(listOf(entry))
        for (replacement in listOf("\"2501\"", "2501.5")) {
            assertThrows(IllegalArgumentException::class.java) { codec.decode(valid.replace("\"distanceMeters\": 2501", "\"distanceMeters\": $replacement")) }
        }
    }

    @Test fun `invalid domain values fail before import`() {
        val valid = codec.encode(listOf(entry))
        for (text in listOf(valid.replace("2026-01-02", "2026-02-30"), valid.replace("\"distanceMeters\": 2501", "\"distanceMeters\": 0"), valid.replace("HIKE", "UNKNOWN"))) {
            assertThrows(IllegalArgumentException::class.java) { codec.decode(text) }
        }
    }

    @Test fun `duplicate activity ids are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { codec.decode(codec.encode(listOf(entry, entry))) }
    }

    @Test fun `out of range and partial coordinates fail`() {
        val valid = codec.encode(listOf(entry))
        for (text in listOf(valid.replace("\"latitude\": 0.0", "\"latitude\": 91.0"), valid.replace("\"longitude\": 0.0", "\"longitude\": null"))) {
            assertThrows(IllegalArgumentException::class.java) { codec.decode(text) }
        }
    }

    @Test fun `preview counts missing ids and restore keeps existing data`() {
        val store = MemoryStore(listOf(entry))
        val backups = JournalBackups(store, store, codec)
        val changedExisting = entry.copy(title = "Do not replace me")
        val newEntry = entry.copy(id = "id-2")
        val plan = backups.preview(codec.encode(listOf(changedExisting, newEntry)))
        assertEquals(1, plan.additions)
        assertEquals(1, plan.skipped)
        assertEquals(ImportReport(1, 1), backups.restore(plan))
        assertEquals(entry, store.entries.first { it.id == entry.id })
        assertEquals(2, store.entries.size)
        assertEquals(ImportReport(0, 2), backups.restore(plan))
    }

    @Test fun `malformed backup never calls the import writer`() {
        val store = MemoryStore(emptyList())
        assertThrows(IllegalArgumentException::class.java) { JournalBackups(store, store, codec).preview("not json") }
        assertEquals(0, store.imports)
    }

    private class MemoryStore(var entries: List<Walk>) : WalkReader, WalkImporter {
        var imports = 0
        override fun all() = entries.toList()
        override fun mergeMissing(entries: List<Walk>): ImportReport {
            imports++
            val existing = this.entries.map { it.id }.toSet()
            val additions = entries.filter { it.id !in existing }
            this.entries += additions
            return ImportReport(additions.size, entries.size - additions.size)
        }
    }
}
