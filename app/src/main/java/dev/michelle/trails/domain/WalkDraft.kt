package dev.michelle.trails.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant

enum class FormField { TITLE, DATE, DISTANCE, DURATION, START_TIME, LOCATION, NOTES }

data class WalkDraft(
    val id: String? = null,
    val title: String = "",
    val date: String = "",
    val distanceKm: String = "",
    val durationMinutes: String = "",
    val notes: String = "",
    val type: ActivityType = ActivityType.OUTDOOR_WALK,
    val location: String = "",
    val effort: Effort? = null,
    val point: GeoPoint? = null,
    val startTime: String = "",
    val zoneId: String = "",
) {
    companion object {
        fun from(walk: Walk): WalkDraft {
            return WalkDraft(
                id = walk.id,
                title = walk.title,
                date = walk.date.toString(),
                distanceKm = BigDecimal.valueOf(walk.distanceMeters.toLong(), 3).stripTrailingZeros().toPlainString(),
                durationMinutes = walk.durationMinutes.toString(),
                notes = walk.notes,
                type = walk.type,
                location = walk.location,
                effort = walk.effort,
                point = walk.point,
                startTime = walk.startTime?.toString().orEmpty(),
                zoneId = walk.zoneId.orEmpty(),
            )
        }
    }
}

data class ValidatedWalk(
    val title: String,
    val date: LocalDate,
    val meters: Int,
    val minutes: Int,
    val notes: String,
    val type: ActivityType,
    val location: String,
    val effort: Effort?,
    val point: GeoPoint? = null,
    val startTime: LocalTime? = null,
    val zoneId: String? = null,
)

data class DraftValidation(
    val value: ValidatedWalk?,
    val errors: Map<FormField, String>,
)

/** Shared validation runs before either add or edit reaches storage. */
class ValidateWalkDraft(private val now: () -> Instant = Instant::now, private val today: () -> LocalDate = LocalDate::now) {
    fun execute(draft: WalkDraft): DraftValidation {
        val errors = mutableMapOf<FormField, String>()
        val title = draft.title.trim()
        val notes = draft.notes.trim()
        val location = draft.location.trim()
        val date = runCatching { LocalDate.parse(draft.date.trim()) }.getOrNull()
        val meters = parseMeters(draft.distanceKm)
        val minutes = draft.durationMinutes.trim().toIntOrNull()

        if (title.isBlank() || title.length > 80) {
            errors[FormField.TITLE] = "Enter a name between 1 and 80 characters."
        }
        if (date == null) {
            errors[FormField.DATE] = "Choose a valid date."
        } else if (date > today()) {
            errors[FormField.DATE] = "Completed activities cannot be in the future."
        }
        if (meters == null || meters !in 1..1_000_000) {
            errors[FormField.DISTANCE] = "Enter 0.001 to 1,000 km, with at most 3 decimal places."
        }
        if (minutes == null || minutes !in 1..10_080) {
            errors[FormField.DURATION] = "Choose a duration from 1 minute to 7 days."
        }
        if (location.length > 120) {
            errors[FormField.LOCATION] = "Keep the location within 120 characters."
        }
        if (notes.length > 2000) {
            errors[FormField.NOTES] = "Keep notes within 2,000 characters."
        }
        val time = if (draft.startTime.isBlank()) null else runCatching { LocalTime.parse(draft.startTime) }.getOrNull()
        val zone = if (time == null) null else runCatching { ZoneId.of(draft.zoneId) }.getOrNull()
        if (draft.startTime.isNotBlank() && (time == null || zone == null)) {
            errors[FormField.START_TIME] = "Choose a valid start time and time zone."
        } else if (time != null && zone != null && date != null && minutes != null && minutes in 1..10_080) {
            runCatching { ActivityTiming.interval(date, time, minutes, zone, now()) }.exceptionOrNull()?.let {
                errors[FormField.START_TIME] = it.message ?: "Check the activity start time."
            }
        }
        if (errors.isNotEmpty()) {
            return DraftValidation(null, errors)
        }

        val value = ValidatedWalk(title, checkNotNull(date), checkNotNull(meters), checkNotNull(minutes), notes, draft.type, location, draft.effort, draft.point, time, zone?.id)
        return DraftValidation(value, emptyMap())
    }

    private fun parseMeters(value: String): Int? {
        val text = value.trim()
        // Accept decimal kilometres without silently rounding away part of a metre.
        if (!text.matches(Regex("[0-9]+(?:\\.[0-9]{1,3})?"))) {
            return null
        }
        return try {
            BigDecimal(text).movePointRight(3).setScale(0, RoundingMode.UNNECESSARY).intValueExact()
        } catch (error: ArithmeticException) {
            null
        }
    }
}
