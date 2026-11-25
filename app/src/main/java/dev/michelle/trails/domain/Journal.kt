package dev.michelle.trails.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class ActivityType(val label: String) {
    INDOOR_WALK("Indoor walking"),
    OUTDOOR_WALK("Outdoor walking"),
    HIKE("Hiking"),
}

enum class Effort(val label: String) {
    EASY("Easy"), MODERATE("Moderate"), HARD("Hard"),
}

data class Walk(
    val id: String,
    val title: String,
    val date: LocalDate,
    val distanceMeters: Int,
    val durationMinutes: Int,
    val notes: String,
    val type: ActivityType = ActivityType.OUTDOOR_WALK,
    val location: String = "",
    val effort: Effort? = null,
    val point: GeoPoint? = null,
    val startTime: LocalTime? = null,
    val zoneId: String? = null,
) {
    init {
        require((startTime == null) == (zoneId == null)) { "Start time and time zone must be supplied together" }
        zoneId?.let { ZoneId.of(it) }
        require(id.isNotBlank()) { "Missing walk ID" }
        require(title.isNotBlank() && title.length <= 80) { "Use a title between 1 and 80 characters" }
        require(distanceMeters in 1..1_000_000) { "Distance must be between 1 and 1,000,000 metres" }
        require(durationMinutes in 1..10_080) { "Duration must be between 1 and 10,080 minutes" }
        require(notes.length <= 2000) { "Notes must be 2,000 characters or fewer" }
        require(location.length <= 120) { "Location must be 120 characters or fewer" }
    }
}

interface WalkReader {
    fun all(): List<Walk>
}

interface WalkWriter {
    /** Insert a new ID or throw. Never replace another record implicitly. */
    fun save(walk: Walk)
    fun delete(id: String)
}

interface WalkFinder {
    fun find(id: String): Walk?
}

interface WalkUpdater {
    /** Return false when the entry no longer exists; never recreate it. */
    fun update(walk: Walk): Boolean
}

interface WalkObserver {
    fun observe(): Flow<List<Walk>>
}

class AddWalk(
    private val writer: WalkWriter,
    private val nextId: () -> String = { UUID.randomUUID().toString() },
) {
    fun execute(
        title: String,
        date: LocalDate,
        meters: Int,
        minutes: Int,
        notes: String,
        type: ActivityType = ActivityType.OUTDOOR_WALK,
        location: String = "",
        effort: Effort? = null,
        point: GeoPoint? = null,
        startTime: LocalTime? = null,
        zoneId: String? = null,
    ): Walk {
        val walk = Walk(nextId(), title.trim(), date, meters, minutes, notes.trim(), type, location.trim(), effort, point, startTime, zoneId)
        writer.save(walk)
        return walk
    }
}

class EditWalk(private val finder: WalkFinder, private val updater: WalkUpdater) {
    fun execute(id: String, value: ValidatedWalk): Walk {
        val existing = finder.find(id)
            ?: throw IllegalStateException("This entry was deleted. Cancel editing to add a new entry.")
        val updated = existing.copy(
            title = value.title,
            date = value.date,
            distanceMeters = value.meters,
            durationMinutes = value.minutes,
            notes = value.notes,
            type = value.type,
            location = value.location,
            effort = value.effort,
            point = value.point,
            startTime = value.startTime,
            zoneId = value.zoneId,
        )
        check(updater.update(updated)) { "This entry was deleted before the update could be saved." }
        return updated
    }
}

class DeleteWalk(private val writer: WalkWriter) {
    fun execute(id: String) = writer.delete(id)
}

data class Summary(val count: Int, val meters: Long, val minutes: Long)

class SummarizeWalks {
    fun execute(walks: List<Walk>): Summary {
        return Summary(
            count = walks.size,
            meters = walks.sumOf { it.distanceMeters.toLong() },
            minutes = walks.sumOf { it.durationMinutes.toLong() },
        )
    }
}
