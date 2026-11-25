package dev.michelle.trails.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.michelle.trails.domain.HealthJobOperation
import dev.michelle.trails.domain.HealthJobState
import dev.michelle.trails.domain.HealthSyncJob

@Entity(tableName = "health_sync")
data class HealthSyncEntity(
    @PrimaryKey val walkId: String,
    val operation: String,
    val revision: Long,
    val recordVersion: Long,
    val automatic: Boolean,
    val notBefore: Long,
    val state: String,
    val attempts: Int,
    val lastSyncedAt: Long?,
    val error: String?,
) {
    fun toDomain() = HealthSyncJob(walkId, HealthJobOperation.valueOf(operation), revision, recordVersion,
        automatic, notBefore, HealthJobState.valueOf(state), attempts, lastSyncedAt, error)
}

@Entity(tableName = "health_sync_settings")
data class HealthSyncSettingsEntity(@PrimaryKey val id: Int = 1, val enabled: Boolean)
