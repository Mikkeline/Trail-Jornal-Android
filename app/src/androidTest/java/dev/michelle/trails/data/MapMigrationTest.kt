package dev.michelle.trails.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.michelle.trails.domain.ActivityType
import dev.michelle.trails.domain.GeoPoint
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "map-migration-test.db"
    private var room: TrailDatabase? = null

    @Before
    fun createVersionTwoDatabase() {
        context.deleteDatabase(databaseName)
        val helper = object : SQLiteOpenHelper(context, databaseName, null, 2) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(TrailDatabase.CREATE_VERSION_2.replace("walks_new", "walks"))
                db.execSQL("INSERT INTO walks VALUES ('existing-id', 'Hill', '2026-01-02', 2500, 40, 'Notes', 'HIKE', 'Local hill', 'MODERATE')")
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase
        helper.close()
    }

    @After
    fun closeDatabase() {
        room?.close()
        context.deleteDatabase(databaseName)
    }

    private fun openRepository(): RoomWalkRepository {
        val database = Room.databaseBuilder(context, TrailDatabase::class.java, databaseName)
            .addMigrations(TrailDatabase.MIGRATION_1_2, TrailDatabase.MIGRATION_2_3, TrailDatabase.MIGRATION_3_4)
            .build()
        room = database
        return RoomWalkRepository(database.walkDao())
    }

    @Test
    fun versionTwoEntryKeepsItsMetadataAndStartsWithoutAPin() {
        val entry = openRepository().all().single()
        assertEquals("existing-id", entry.id)
        assertEquals(ActivityType.HIKE, entry.type)
        assertEquals("Local hill", entry.location)
        assertNull(entry.point)
    }

    @Test
    fun pinCanBeAddedReopenedAndRemovedWithoutChangingId() {
        val repository = openRepository()
        val entry = repository.all().single()
        assertTrue(repository.update(entry.copy(point = GeoPoint(0.0, 0.0))))
        room?.close()
        val reopened = openRepository()
        val saved = reopened.all().single()
        assertEquals(entry.id, saved.id)
        assertEquals(GeoPoint(0.0, 0.0), saved.point)
        assertTrue(reopened.update(saved.copy(point = null)))
        assertNull(reopened.find(entry.id)?.point)
    }

    @Test
    fun incompleteStoredCoordinatePairIsReportedAsAnError() {
        val repository = openRepository()
        repository.all() // Open and migrate before inserting an intentionally corrupt pair.
        room!!.openHelper.writableDatabase.execSQL("UPDATE walks SET latitude = 1.0, longitude = NULL")
        assertThrows(IllegalArgumentException::class.java) { repository.all() }
    }
}
