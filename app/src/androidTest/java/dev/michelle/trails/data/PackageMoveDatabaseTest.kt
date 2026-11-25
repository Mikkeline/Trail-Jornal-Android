package dev.michelle.trails.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageMoveDatabaseTest {
    @Test
    fun relocatedRoomClassOpensExistingVersionThreeDataAndIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "package-move-compatibility.db"
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val schema = assets.open("dev.michelle.trails.data.TrailDatabase/3.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.deleteDatabase(name)
        val old = object : SQLiteOpenHelper(context, name, null, 3) {
            override fun onCreate(db: SQLiteDatabase) {
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        old.writableDatabase.insertOrThrow("walks", null, ContentValues().apply {
            put("id", "existing-id")
            put("title", "Existing journal")
            put("date", "2026-01-02")
            put("meters", 2500)
            put("minutes", 30)
            put("notes", "Keep this after the package move")
            put("type", "OUTDOOR_WALK")
            put("location", "Park")
        })
        old.close()
        try {
            val relocated = Room.databaseBuilder(context, TrailDatabase::class.java, name).addMigrations(TrailDatabase.MIGRATION_3_4).build()
            try {
                val entry = RoomWalkRepository(relocated.walkDao()).all().single()
                assertEquals("existing-id", entry.id)
                assertEquals("Keep this after the package move", entry.notes)
                assertEquals(2500, entry.distanceMeters)
            } finally {
                relocated.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
