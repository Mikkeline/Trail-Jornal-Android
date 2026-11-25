package dev.michelle.trails.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [WalkEntity::class, HealthSyncEntity::class, HealthSyncSettingsEntity::class], version = 4, exportSchema = true)
abstract class TrailDatabase : RoomDatabase() {
    abstract fun walkDao(): WalkDao
    abstract fun healthSyncDao(): HealthSyncDao

    companion object {
        const val NAME = "walks.db"

        fun open(context: Context): TrailDatabase {
            return Room.databaseBuilder(context.applicationContext, TrailDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rebuild because the original TEXT PRIMARY KEY did not declare NOT NULL.
                // Room validates that constraint as well as the new activity columns.
                db.execSQL(CREATE_VERSION_2)
                db.execSQL(COPY_VERSION_1)
                db.execSQL("DROP TABLE walks")
                db.execSQL("ALTER TABLE walks_new RENAME TO walks")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(ADD_LATITUDE)
                db.execSQL(ADD_LONGITUDE)
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(ADD_START_TIME)
                db.execSQL(ADD_ZONE_ID)
                db.execSQL(CREATE_HEALTH_SYNC)
                db.execSQL(CREATE_HEALTH_SETTINGS)
            }
        }

        internal const val ADD_START_TIME = "ALTER TABLE walks ADD COLUMN startTime TEXT"
        internal const val ADD_ZONE_ID = "ALTER TABLE walks ADD COLUMN zoneId TEXT"
        internal const val CREATE_HEALTH_SYNC = "CREATE TABLE IF NOT EXISTS health_sync (walkId TEXT NOT NULL PRIMARY KEY, operation TEXT NOT NULL, revision INTEGER NOT NULL, recordVersion INTEGER NOT NULL, automatic INTEGER NOT NULL, notBefore INTEGER NOT NULL, state TEXT NOT NULL, attempts INTEGER NOT NULL, lastSyncedAt INTEGER, error TEXT)"
        internal const val CREATE_HEALTH_SETTINGS = "CREATE TABLE IF NOT EXISTS health_sync_settings (id INTEGER NOT NULL PRIMARY KEY, enabled INTEGER NOT NULL)"

        internal const val ADD_LATITUDE = "ALTER TABLE walks ADD COLUMN latitude REAL"
        internal const val ADD_LONGITUDE = "ALTER TABLE walks ADD COLUMN longitude REAL"

        internal const val CREATE_VERSION_2 = """
            CREATE TABLE walks_new (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                date TEXT NOT NULL,
                meters INTEGER NOT NULL,
                minutes INTEGER NOT NULL,
                notes TEXT NOT NULL,
                type TEXT NOT NULL DEFAULT 'OUTDOOR_WALK',
                location TEXT NOT NULL DEFAULT '',
                effort TEXT
            )
        """
        internal const val COPY_VERSION_1 = """
            INSERT INTO walks_new (id, title, date, meters, minutes, notes, type, location, effort)
            SELECT id, title, date, meters, minutes, notes, 'OUTDOOR_WALK', '', NULL FROM walks
        """
    }
}
