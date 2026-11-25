package dev.michelle.trails.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HistoryTest {
    private val today = LocalDate.of(2026, 1, 5) // Monday: a useful week-boundary case.
    private fun walk(id: String, date: LocalDate, type: ActivityType = ActivityType.OUTDOOR_WALK) = Walk(id, "Park", date, 1000, 10, "Morning air", type, "Hill venue")

    @Test fun `search matches name location and notes ignoring case`() {
        val entries = listOf(walk("1", today))
        for (query in listOf("PARK", " venue ", "AIR")) assertEquals(entries, FilterHistory().execute(entries, HistoryFilter(query), today))
    }

    @Test fun `this week includes Monday and excludes Sunday and future dates`() {
        val entries = listOf(walk("mon", today), walk("sun", today.minusDays(1)), walk("future", today.plusDays(1)))
        assertEquals(listOf("mon"), FilterHistory().execute(entries, HistoryFilter(period = HistoryPeriod.THIS_WEEK), today).map { it.id })
    }

    @Test fun `type query and period filters combine`() {
        val entries = listOf(walk("1", today, ActivityType.HIKE), walk("2", today), walk("3", today.minusMonths(1), ActivityType.HIKE))
        assertEquals(listOf("1"), FilterHistory().execute(entries, HistoryFilter("park", ActivityType.HIKE, HistoryPeriod.THIS_MONTH), today).map { it.id })
    }

    @Test fun `history has stable date and id ordering`() {
        val entries = listOf(walk("a", today), walk("b", today), walk("c", today.minusDays(1)))
        assertEquals(listOf("b", "a", "c"), FilterHistory().execute(entries, HistoryFilter(), today).map { it.id })
    }

    @Test fun `empty and unmatched history returns no records`() {
        assertTrue(FilterHistory().execute(emptyList(), HistoryFilter(), today).isEmpty())
        assertTrue(FilterHistory().execute(listOf(walk("1", today)), HistoryFilter("ocean"), today).isEmpty())
    }
}
