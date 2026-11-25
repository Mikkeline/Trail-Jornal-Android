package dev.michelle.trails.domain

object ActivityDuration {
    fun fromParts(hours: String, minutes: String): Result<Int> = runCatching {
        require(hours.isNotBlank() || minutes.isNotBlank()) { "Choose how long the activity lasted." }
        val hourValue = if (hours.isBlank()) 0 else hours.trim().toIntOrNull()
        val minuteValue = if (minutes.isBlank()) 0 else minutes.trim().toIntOrNull()
        require(hourValue != null && hourValue in 0..168) { "Enter hours between 0 and 168." }
        require(minuteValue != null && minuteValue in 0..59) { "Enter minutes between 0 and 59." }
        val total = hourValue * 60 + minuteValue
        require(total in 1..10_080) { "Choose a duration from 1 minute to 7 days." }
        total
    }

    fun format(totalMinutes: Long): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L -> "$minutes min"
            minutes == 0L -> "$hours h"
            else -> "$hours h $minutes min"
        }
    }
}
