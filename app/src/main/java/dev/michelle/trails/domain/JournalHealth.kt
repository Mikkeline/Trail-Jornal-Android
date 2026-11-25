package dev.michelle.trails.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt

enum class HealthAvailability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }
enum class HealthPermissionMode { READ_WORKOUTS, SHARE_WORKOUTS }
data class HealthAccess(val availability: HealthAvailability, val canRead: Boolean = false, val canWrite: Boolean = false)

interface HealthAccessReader { suspend fun access(): HealthAccess }
interface HealthWorkoutReader { suspend fun recentWorkouts(): ExternalWorkouts }
interface HealthWorkoutWriter { suspend fun share(workout: SharedWorkout) }
interface HealthWorkoutDeleter { suspend fun delete(walkId: String) }
interface HealthActivitySharer { suspend fun share(walk: Walk, startTime: LocalTime, zone: ZoneId) }

data class ExternalWorkout(
    val recordId: String,
    val sourcePackage: String,
    val title: String,
    val type: ActivityType,
    val start: Instant,
    val end: Instant,
    val startOffsetSeconds: Int,
    val distanceMeters: Double?,
    val sourceName: String = sourcePackage,
)
data class ExternalWorkouts(val entries: List<ExternalWorkout>, val unsupported: Int, val truncated: Boolean)
data class HealthCandidate(val walk: Walk, val source: String, val start: Instant, val end: Instant)
data class HealthImportPlan(val candidates: List<HealthCandidate>, val existing: Int, val skipped: Int, val truncated: Boolean)
data class SharedWorkout(val walk: Walk, val start: Instant, val end: Instant, val startOffset: ZoneOffset, val endOffset: ZoneOffset, val recordVersion: Long? = null)

object HealthRecordIdentity {
    private const val PREFIX = "health-connect:"
    fun imported(id: String): Boolean = id.startsWith(PREFIX)
    fun id(source: String, recordId: String): String = PREFIX + digest("$source|$recordId")
    fun exportId(id: String): String = "trail-journal:" + digest(id)
    private fun digest(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

class JournalHealth(
    private val reader: HealthWorkoutReader,
    private val writer: HealthWorkoutWriter,
    private val journal: WalkReader,
    private val importer: WalkImporter,
    private val now: () -> Instant = Instant::now,
) : HealthActivitySharer {
    override suspend fun share(walk: Walk, startTime: LocalTime, zone: ZoneId) = share(prepareShare(walk, startTime, zone))

    suspend fun preview(): HealthImportPlan {
        val external = reader.recentWorkouts()
        val existing = journal.all().map { it.id }.toSet()
        val seen = mutableSetOf<String>()
        val candidates = mutableListOf<HealthCandidate>()
        var existingCount = 0
        var skipped = external.unsupported
        for (entry in external.entries) {
            val candidate = toCandidate(entry)
            if (candidate == null) {
                skipped++
                continue
            }
            if (!seen.add(candidate.walk.id)) continue
            if (candidate.walk.id in existing) existingCount++ else candidates += candidate
        }
        return HealthImportPlan(candidates, existingCount, skipped, external.truncated)
    }

    fun import(plan: HealthImportPlan, selectedIds: Set<String>): ImportReport {
        val knownIds = plan.candidates.map { it.walk.id }.toSet()
        require(selectedIds.all { it in knownIds }) { "Choose workouts from this preview." }
        require(selectedIds.isNotEmpty()) { "Choose at least one workout." }
        return importer.mergeMissing(plan.candidates.filter { it.walk.id in selectedIds }.map { it.walk })
    }

    fun prepareShare(walk: Walk, startTime: LocalTime, zone: ZoneId): SharedWorkout {
        require(!HealthRecordIdentity.imported(walk.id)) { "This workout was imported from another app. Share the original there to avoid copies." }
        val interval = ActivityTiming.interval(walk.date, startTime, walk.durationMinutes, zone, now())
        return SharedWorkout(walk, interval.start, interval.end, interval.startOffset, interval.endOffset)
    }

    suspend fun share(workout: SharedWorkout) = writer.share(workout)

    private fun toCandidate(entry: ExternalWorkout): HealthCandidate? {
        val distance = entry.distanceMeters ?: return null
        val elapsed = Duration.between(entry.start, entry.end)
        if (!distance.isFinite() || distance < 1 || distance > 1_000_000 || elapsed.isZero || elapsed.isNegative || elapsed > Duration.ofDays(7) || entry.end > now()) return null
        if (entry.recordId.isBlank() || entry.sourcePackage.isBlank()) return null
        val minutes = (elapsed.seconds / 60 + if (elapsed.seconds % 60 != 0L || elapsed.nano != 0) 1 else 0).toInt()
        val date = entry.start.atOffset(ZoneOffset.ofTotalSeconds(entry.startOffsetSeconds)).toLocalDate()
        val walk = Walk(
            id = HealthRecordIdentity.id(entry.sourcePackage, entry.recordId),
            title = entry.title.trim().ifEmpty { entry.type.label }.take(80),
            date = date,
            distanceMeters = distance.roundToInt(),
            durationMinutes = minutes,
            notes = "Imported from ${entry.sourcePackage}. Elapsed session: ${entry.start} to ${entry.end}. Pauses are included; journal duration is rounded up to a whole minute.".take(2000),
            type = entry.type,
        )
        return HealthCandidate(walk, entry.sourceName, entry.start, entry.end)
    }
}
