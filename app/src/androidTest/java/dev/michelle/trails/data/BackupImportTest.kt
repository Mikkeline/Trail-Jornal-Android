package dev.michelle.trails.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.michelle.trails.domain.ImportReport
import dev.michelle.trails.domain.Walk
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupImportTest {
    private lateinit var database: TrailDatabase
    private lateinit var repository: RoomWalkRepository
    private val first = Walk("existing", "Original", LocalDate.of(2026, 1, 2), 1000, 10, "")

    @Before fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TrailDatabase::class.java).build()
        repository = RoomWalkRepository(database.walkDao())
        repository.save(first)
    }

    @After fun closeDatabase() = database.close()

    @Test fun mergeKeepsExistingIdsAndAddsOnlyMissingEntries() {
        val imported = listOf(first.copy(title = "Do not overwrite"), first.copy(id = "new", title = "New activity"))
        assertEquals(ImportReport(1, 1), repository.mergeMissing(imported))
        assertEquals(first, repository.find(first.id))
        assertEquals(2, repository.all().size)
        assertEquals(ImportReport(0, 2), repository.mergeMissing(imported))
    }

    @Test fun failedImportRollsBackEarlierInsertsInTheSameTransaction() {
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_import BEFORE INSERT ON walks WHEN NEW.id = 'fail' BEGIN SELECT RAISE(ABORT, 'Injected failure'); END")
        val imported = listOf(first.copy(id = "new"), first.copy(id = "fail"))
        assertThrows(Exception::class.java) { repository.mergeMissing(imported) }
        assertEquals(listOf(first), repository.all())
    }
}
