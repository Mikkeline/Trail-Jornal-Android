package dev.michelle.trails.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class HistoryPeriod(val label: String) { ALL("All dates"), THIS_WEEK("This week"), THIS_MONTH("This month") }

data class HistoryFilter(
    val query: String = "",
    val type: ActivityType? = null,
    val period: HistoryPeriod = HistoryPeriod.ALL,
)

class FilterHistory {
    fun execute(entries: List<Walk>, filter: HistoryFilter, today: LocalDate): List<Walk> {
        val query = filter.query.trim()
        val start = when (filter.period) {
            HistoryPeriod.ALL -> LocalDate.MIN
            HistoryPeriod.THIS_WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            HistoryPeriod.THIS_MONTH -> today.withDayOfMonth(1)
        }
        return entries.filter { walk ->
            val matchesText = query.isEmpty() || listOf(walk.title, walk.location, walk.notes).any { it.contains(query, ignoreCase = true) }
            val matchesPeriod = filter.period == HistoryPeriod.ALL || walk.date in start..today
            matchesText && matchesPeriod && (filter.type == null || walk.type == filter.type)
        }.sortedWith(compareByDescending<Walk> { it.date }.thenByDescending { it.id })
    }
}

class RestoreDeletedWalk(private val writer: WalkWriter) {
    /** Insert with the original ID. A conflicting ID must fail instead of replacing data. */
    fun execute(walk: Walk) = writer.save(walk)
}
