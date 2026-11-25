package dev.michelle.trails.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.michelle.trails.domain.ActivityType
import dev.michelle.trails.domain.Effort
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "legacy-migration-test.db"
    private var room: TrailDatabase? = null

    @Before
    fun createLegacyDatabase() {
        context.deleteDatabase(databaseName)
        val helper = object : SQLiteOpenHelper(context, databaseName, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE walks(id TEXT PRIMARY KEY, title TEXT NOT NULL, date TEXT NOT NULL, meters INTEGER NOT NULL, minutes INTEGER NOT NULL, notes TEXT NOT NULL)")
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase.insertOrThrow("walks", null, ContentValues().apply {
            put("id", "legacy-id")
            put("title", "Morning's walk")
            put("date", "2026-01-02")
            put("meters", 2500)
            put("minutes", 30)
            put("notes", "Keep my original notes")
        })
        helper.close()
    }

    @After
    fun closeDatabase() {
        room?.close()
        context.deleteDatabase(databaseName)
    }

    private fun openRoom(): RoomWalkRepository {
        val database = Room.databaseBuilder(context, TrailDatabase::class.java, databaseName)
            .addMigrations(TrailDatabase.MIGRATION_1_2, TrailDatabase.MIGRATION_2_3, TrailDatabase.MIGRATION_3_4)
            .build()
        room = database
        return RoomWalkRepository(database.walkDao())
    }

    @Test
    fun legacyEntrySurvivesRoomMigration() {
        val repository = openRoom()
        val entry = repository.all().single()
        assertEquals("legacy-id", entry.id)
        assertEquals("Morning's walk", entry.title)
        assertEquals("2026-01-02", entry.date.toString())
        assertEquals(2500, entry.distanceMeters)
        assertEquals(30, entry.durationMinutes)
        assertEquals("Keep my original notes", entry.notes)
        assertEquals(ActivityType.OUTDOOR_WALK, entry.type)
        assertEquals("", entry.location)
        assertNull(entry.effort)
        assertNull(entry.point)
    }

    @Test
    fun migratedEntryCanBeEditedAndDeletedWithoutCreatingAnotherId() {
        val repository = openRoom()
        val original = repository.all().single()
        val edited = original.copy(type = ActivityType.HIKE, location = "Local hill", effort = Effort.MODERATE)
        assertTrue(repository.update(edited))
        assertEquals(edited, repository.find(original.id))
        assertEquals(1, repository.all().size)
        repository.delete(original.id)
        assertFalse(repository.update(edited))
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun duplicateInsertDoesNotOverwriteExistingEntry() {
        val repository = openRoom()
        val original = repository.all().single()
        assertThrows(Exception::class.java) { repository.save(original.copy(title = "Unexpected replacement")) }
        assertEquals(original, repository.find(original.id))
    }
}
