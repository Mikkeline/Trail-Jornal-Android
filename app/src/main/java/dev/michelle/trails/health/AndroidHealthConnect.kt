package dev.michelle.trails.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Length
import dev.michelle.trails.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Health Connect SDK types stay here; the journal uses provider-neutral workout ports. */
class AndroidHealthConnect(context: Context) : HealthAccessReader, HealthWorkoutReader, HealthWorkoutWriter, HealthWorkoutDeleter {
    private val context = context.applicationContext
    private val versions = this.context.getSharedPreferences("health-export-versions", Context.MODE_PRIVATE)

    override suspend fun access(): HealthAccess {
        val status = when (HealthConnectClient.getSdkStatus(context, PROVIDER)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
            else -> HealthAvailability.UNAVAILABLE
        }
        if (status != HealthAvailability.AVAILABLE) return HealthAccess(status)
        val granted = client().permissionController.getGrantedPermissions()
        return HealthAccess(status, granted.containsAll(readPermissions), granted.containsAll(writePermissions))
    }

    override suspend fun recentWorkouts(): ExternalWorkouts {
        check(access().canRead) { "Allow workout and distance reading first." }
        val client = client()
        val end = Instant.now()
        val start = end.minus(30, ChronoUnit.DAYS)
        val entries = mutableListOf<ExternalWorkout>()
        var skipped = 0
        var scanned = 0
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = false,
                    pageSize = MAX_SESSIONS - scanned,
                    pageToken = token,
                ),
            )
            for (record in response.records) {
                scanned++
                val type = when (record.exerciseType) {
                    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> ActivityType.OUTDOOR_WALK
                    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> ActivityType.HIKE
                    else -> null
                }
                if (type == null || record.metadata.dataOrigin.packageName == context.packageName) {
                    skipped++
                    continue
                }
                val distance = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime),
                        dataOriginFilter = setOf(record.metadata.dataOrigin),
                    ),
                )[DistanceRecord.DISTANCE_TOTAL]?.inMeters
                entries += ExternalWorkout(
                    recordId = record.metadata.clientRecordId?.let { "client:$it" } ?: "record:${record.metadata.id}",
                    sourcePackage = record.metadata.dataOrigin.packageName,
                    title = record.title.orEmpty(),
                    type = type,
                    start = record.startTime,
                    end = record.endTime,
                    startOffsetSeconds = (record.startZoneOffset ?: ZoneId.systemDefault().rules.getOffset(record.startTime)).totalSeconds,
                    distanceMeters = distance,
                    sourceName = listOfNotNull(record.metadata.device?.manufacturer, record.metadata.device?.model)
                        .joinToString(" ").ifBlank { record.metadata.dataOrigin.packageName },
                )
            }
            token = response.pageToken
        } while (token != null && scanned < MAX_SESSIONS)
        return ExternalWorkouts(entries, skipped, token != null)
    }

    override suspend fun share(workout: SharedWorkout) {
        check(access().canWrite) { "Allow workout and distance sharing first." }
        require(!HealthRecordIdentity.imported(workout.walk.id)) { "Imported workouts are not copied back to Health Connect." }
        val key = HealthRecordIdentity.exportId(workout.walk.id)
        val version = workout.recordVersion ?: nextVersion(key)
        val exerciseType = if (workout.walk.type == ActivityType.HIKE) ExerciseSessionRecord.EXERCISE_TYPE_HIKING else ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        val session = ExerciseSessionRecord(
            startTime = workout.start,
            startZoneOffset = workout.startOffset,
            endTime = workout.end,
            endZoneOffset = workout.endOffset,
            exerciseType = exerciseType,
            title = workout.walk.title,
            metadata = Metadata.manualEntry(clientRecordId = "$key:session", clientRecordVersion = version),
        )
        val distance = DistanceRecord(
            startTime = workout.start,
            startZoneOffset = workout.startOffset,
            endTime = workout.end,
            endZoneOffset = workout.endOffset,
            distance = Length.meters(workout.walk.distanceMeters.toDouble()),
            metadata = Metadata.manualEntry(clientRecordId = "$key:distance", clientRecordVersion = version),
        )
        // Stable client IDs update this app's previous copy instead of adding a new one.
        val result = client().insertRecords(listOf(session, distance))
        check(result.recordIdsList.size == 2 && result.recordIdsList.all { it.isNotBlank() }) {
            "Health Connect did not confirm both records. Check its data screen before retrying."
        }
    }

    override suspend fun delete(walkId: String) {
        check(access().canWrite) { "Allow exercise and distance sharing to remove this app's records." }
        require(!HealthRecordIdentity.imported(walkId)) { "Imported workouts belong to their original app." }
        val key = HealthRecordIdentity.exportId(walkId)
        // Client IDs are owned by this application. Each operation can safely be repeated after interruption.
        client().deleteRecords(ExerciseSessionRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf("$key:session"))
        client().deleteRecords(DistanceRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf("$key:distance"))
    }

    fun recordVersionFloor(walkId: String): Long = versions.getLong(HealthRecordIdentity.exportId(walkId), 0)

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context, PROVIDER)

    @Synchronized
    private fun nextVersion(key: String): Long {
        val version = maxOf(Instant.now().toEpochMilli(), versions.getLong(key, 0) + 1)
        check(versions.edit().putLong(key, version).commit()) { "Could not prepare the health export. Please retry." }
        return version
    }

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"
        const val MAX_SESSIONS = 100
        val readPermissions = setOf(HealthPermission.getReadPermission(ExerciseSessionRecord::class), HealthPermission.getReadPermission(DistanceRecord::class))
        val writePermissions = setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class), HealthPermission.getWritePermission(DistanceRecord::class))
    }
}
