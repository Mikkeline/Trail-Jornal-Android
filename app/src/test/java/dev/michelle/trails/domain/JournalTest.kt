package dev.michelle.trails.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class JournalTest {
    private val date = LocalDate.of(2026, 1, 1)
    private class MemoryWriter : WalkWriter {
        val walks = mutableListOf<Walk>()
        override fun save(walk: Walk) { walks += walk }
        override fun delete(id: String) { walks.removeAll { it.id == id } }
    }
    @Test fun `add trims input and persists a valid walk`() {
        val writer = MemoryWriter()
        val result = AddWalk(writer) { "walk-1" }.execute("  Morning walk  ", date, 2500, 30, "  Park  ")
        assertEquals("Morning walk", result.title); assertEquals("Park", result.notes)
        assertEquals(listOf(result), writer.walks); assertEquals("walk-1", result.id)
    }
    @Test fun `invalid input never reaches persistence`() {
        val writer = MemoryWriter()
        assertThrows(IllegalArgumentException::class.java) { AddWalk(writer).execute(" ", date, 100, 10, "") }
        assertTrue(writer.walks.isEmpty())
    }
    @Test fun `distance and duration have meaningful bounds`() {
        for ((meters, minutes) in listOf(0 to 1, -1 to 1, 1_000_001 to 1, 1 to 0, 1 to 10_081)) {
            assertThrows(IllegalArgumentException::class.java) { Walk("a", "walk", date, meters, minutes, "") }
        }
    }
    @Test fun `oversized notes are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Walk("a", "walk", date, 10, 10, "x".repeat(2001)) }
    }
    @Test fun `empty journal has zero totals`() { assertEquals(Summary(0, 0, 0), SummarizeWalks().execute(emptyList())) }
    @Test fun `summary accumulates long totals`() {
        val walks = List(3000) { Walk("$it", "walk", date, 1_000_000, 10_000, "") }
        assertEquals(Summary(3000, 3_000_000_000, 30_000_000), SummarizeWalks().execute(walks))
    }
    @Test fun `storage failures propagate to the caller`() {
        val writer = object : WalkWriter { override fun save(walk: Walk) { error("Disk full") }; override fun delete(id: String) = Unit }
        assertThrows(IllegalStateException::class.java) { AddWalk(writer).execute("walk", date, 10, 10, "") }
    }

    private class MemoryStore : WalkReader, WalkWriter {
        val entries = mutableListOf<Walk>()
        override fun all() = entries.toList()
        override fun save(walk: Walk) { entries += walk }
        override fun delete(id: String) { entries.removeAll { it.id == id } }
    }
    @Test fun `journal action returns refreshed state after save and delete`() {
        val store = MemoryStore(); val actions = JournalActions(store, store)
        val saved = actions.add(WalkForm("Hill", "2026-01-02", "1500", "30", ""))
        assertEquals(Summary(1, 1500, 30), saved.summary)
        assertEquals(0, actions.delete(saved.walks.single().id).walks.size)
    }
    @Test fun `invalid date is rejected without persistence`() {
        val store = MemoryStore(); val actions = JournalActions(store, store)
        assertThrows(IllegalArgumentException::class.java) { actions.add(WalkForm("Hill", "2026-02-30", "100", "10", "")) }
        assertTrue(store.entries.isEmpty())
    }
    @Test fun `non numeric distances are rejected without persistence`() {
        val store = MemoryStore(); val actions = JournalActions(store, store)
        assertThrows(IllegalArgumentException::class.java) { actions.add(WalkForm("Hill", "2026-01-02", "abc", "10", "")) }
        assertTrue(store.entries.isEmpty())
    }
}
