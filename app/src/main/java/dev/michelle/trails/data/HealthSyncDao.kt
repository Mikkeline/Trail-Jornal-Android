package dev.michelle.trails.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthSyncDao {
    @Query("SELECT * FROM health_sync ORDER BY notBefore, walkId") fun all(): List<HealthSyncEntity>
    @Query("SELECT * FROM health_sync WHERE walkId = :id") fun find(id: String): HealthSyncEntity?
    @Query("SELECT * FROM health_sync ORDER BY notBefore, walkId") fun observe(): Flow<List<HealthSyncEntity>>
    @Query("SELECT * FROM health_sync_settings WHERE id = 1") fun settings(): HealthSyncSettingsEntity?
    @Query("SELECT * FROM health_sync_settings WHERE id = 1") fun observeSettings(): Flow<HealthSyncSettingsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun put(job: HealthSyncEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun configure(settings: HealthSyncSettingsEntity)
    @Query("UPDATE health_sync SET state = :state, lastSyncedAt = :at, error = NULL, attempts = 0 WHERE walkId = :id AND revision = :revision")
    fun complete(id: String, revision: Long, state: String, at: Long)
    @Query("UPDATE health_sync SET state = :state, error = :error, attempts = attempts + 1, notBefore = :retryAt WHERE walkId = :id AND revision = :revision")
    fun fail(id: String, revision: Long, state: String, error: String, retryAt: Long)
}
