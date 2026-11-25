package dev.michelle.trails.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WalkDao {
    @Query("SELECT * FROM walks ORDER BY date DESC, id DESC")
    fun all(): List<WalkEntity>

    @Query("SELECT * FROM walks ORDER BY date DESC, id DESC")
    fun observe(): Flow<List<WalkEntity>>

    @Query("SELECT * FROM walks WHERE id = :id")
    fun find(id: String): WalkEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(walk: WalkEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIfMissing(walk: WalkEntity): Long

    @Transaction
    fun mergeMissing(entries: List<WalkEntity>): Int {
        return entries.count { insertIfMissing(it) != -1L }
    }

    @Update
    fun update(walk: WalkEntity): Int

    @Query("DELETE FROM walks WHERE id = :id")
    fun delete(id: String)
}
