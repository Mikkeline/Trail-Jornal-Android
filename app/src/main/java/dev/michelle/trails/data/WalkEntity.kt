package dev.michelle.trails.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.michelle.trails.domain.ActivityType
import dev.michelle.trails.domain.GeoPoint
import dev.michelle.trails.domain.Effort
import dev.michelle.trails.domain.Walk
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "walks")
data class WalkEntity(
    @PrimaryKey val id: String,
    val title: String,
    val date: String,
    val meters: Int,
    val minutes: Int,
    val notes: String,
    @ColumnInfo(defaultValue = "'OUTDOOR_WALK'") val type: String,
    @ColumnInfo(defaultValue = "''") val location: String,
    val effort: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val startTime: String? = null,
    val zoneId: String? = null,
)

fun WalkEntity.toDomain(): Walk {
    return Walk(
        id = id,
        title = title,
        date = LocalDate.parse(date),
        distanceMeters = meters,
        durationMinutes = minutes,
        notes = notes,
        type = ActivityType.valueOf(type),
        location = location,
        effort = effort?.let(Effort::valueOf),
        point = GeoPoint.fromNullable(latitude, longitude),
        startTime = startTime?.let(LocalTime::parse),
        zoneId = zoneId,
    )
}

fun Walk.toEntity(): WalkEntity {
    return WalkEntity(
        id = id,
        title = title,
        date = date.toString(),
        meters = distanceMeters,
        minutes = durationMinutes,
        notes = notes,
        type = type.name,
        location = location,
        effort = effort?.name,
        latitude = point?.latitude,
        longitude = point?.longitude,
        startTime = startTime?.toString(),
        zoneId = zoneId,
    )
}
