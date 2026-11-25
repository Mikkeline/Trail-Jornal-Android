package dev.michelle.trails.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

data class WorkoutInterval(val start: Instant, val end: Instant, val startOffset: ZoneOffset, val endOffset: ZoneOffset)

object ActivityTiming {
    fun interval(date: LocalDate, time: LocalTime, minutes: Int, zone: ZoneId, now: Instant): WorkoutInterval {
        val localStart = date.atTime(time)
        val offsets = zone.rules.getValidOffsets(localStart)
        require(offsets.size == 1) { "This start time falls in a daylight-saving transition. Choose an unambiguous time." }
        val start = localStart.toInstant(offsets.single())
        val end = start.plusSeconds(minutes.toLong() * 60)
        require(end <= now) { "The activity would finish in the future. Check its date and start time." }
        return WorkoutInterval(start, end, offsets.single(), zone.rules.getOffset(end))
    }
}
