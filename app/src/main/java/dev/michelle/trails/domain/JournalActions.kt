package dev.michelle.trails.domain

import java.time.LocalDate

data class WalkForm(
    val title: String,
    val date: String,
    val meters: String,
    val minutes: String,
    val notes: String,
)

data class JournalState(val walks: List<Walk>, val summary: Summary)

class JournalActions(
    private val reader: WalkReader,
    private val writer: WalkWriter,
    private val add: AddWalk = AddWalk(writer),
) {
    fun load(): JournalState {
        val walks = reader.all()
        val summary = SummarizeWalks().execute(walks)
        return JournalState(walks, summary)
    }

    fun add(form: WalkForm): JournalState {
        val date = parseDate(form.date)
        val meters = form.meters.toIntOrNull() ?: 0
        val minutes = form.minutes.toIntOrNull() ?: 0

        add.execute(form.title, date, meters, minutes, form.notes)
        return load()
    }

    fun delete(id: String): JournalState {
        writer.delete(id)
        return load()
    }

    private fun parseDate(value: String): LocalDate {
        return try {
            LocalDate.parse(value.trim())
        } catch (error: Exception) {
            throw IllegalArgumentException("Use date format YYYY-MM-DD", error)
        }
    }
}
