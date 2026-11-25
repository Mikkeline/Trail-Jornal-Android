package dev.michelle.trails.presentation

import dev.michelle.trails.domain.*
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Test

class JournalControllerTest {
    private val today = LocalDate.of(2026, 1, 2)
    private val validDraft = WalkDraft(title = "Park", date = today.toString(), distanceKm = "2.5", durationMinutes = "30")

    private class MemoryDraftStore(var draft: WalkDraft? = null) : DraftStore {
        var baseline = draft
        override fun readBaseline() = baseline
        override fun writeBaseline(draft: WalkDraft) { baseline = draft }
        override fun read() = draft
        override fun write(draft: WalkDraft) { this.draft = draft }
    }

    private class MemoryRepository : WalkObserver, WalkWriter, WalkFinder, WalkUpdater, WalkReader, WalkImporter {
        val entries = MutableStateFlow<List<Walk>>(emptyList())
        var failWrites = false
        var failReads = false
        var inserts = 0
        override fun all() = entries.value.toList()
        override fun mergeMissing(entries: List<Walk>): ImportReport {
            if (failWrites) error("Disk full")
            val ids = this.entries.value.map { it.id }.toSet()
            val additions = entries.filter { it.id !in ids }
            this.entries.value += additions
            return ImportReport(additions.size, entries.size - additions.size)
        }
        override fun observe(): Flow<List<Walk>> = if (failReads) flow { error("Read failed") } else entries
        override fun save(walk: Walk) {
            if (failWrites) error("Disk full")
            check(find(walk.id) == null) { "Duplicate entry ID" }
            inserts++
            entries.value += walk
        }
        override fun delete(id: String) { entries.value = entries.value.filterNot { it.id == id } }
        override fun find(id: String) = entries.value.find { it.id == id }
        override fun update(walk: Walk): Boolean {
            if (failWrites) error("Disk full")
            if (find(walk.id) == null) return false
            entries.value = entries.value.map { if (it.id == walk.id) walk else it }
            return true
        }
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val pending = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { pending.addLast(block) }
        fun runAll() { while (pending.isNotEmpty()) pending.removeFirst().run() }
    }

    private inner class Fixture(
        val repository: MemoryRepository = MemoryRepository(),
        val drafts: MemoryDraftStore = MemoryDraftStore(validDraft),
        io: CoroutineDispatcher = Dispatchers.Unconfined,
        backups: JournalBackups? = null,
        backupMonitor: BackupMonitor = BackupMonitor(MemoryBackupStatusStore()),
    ) : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = JournalController(
            observer = repository,
            addWalk = AddWalk(repository) { "new-id" },
            editWalk = EditWalk(repository, repository),
            deleteWalk = DeleteWalk(repository),
            restoreDeletedWalk = RestoreDeletedWalk(repository),
            backups = backups,
            backupMonitor = backupMonitor,
            draftStore = drafts,
            scope = scope,
            ioDispatcher = io,
            validator = ValidateWalkDraft { today },
            today = { today },
        )
        override fun close() = scope.cancel()
    }

    @Test fun `valid save persists an activity and resets the editor`() {
        Fixture().use { fixture ->
            fixture.controller.dispatch(JournalEvent.SaveRequested)
            assertEquals(1, fixture.repository.inserts)
            assertEquals(ActivityType.OUTDOOR_WALK.label, fixture.controller.state.value.draft.title)
            assertEquals("Activity saved.", fixture.controller.state.value.notice)
            assertEquals(2500L, fixture.controller.state.value.summary.meters)
        }
    }

    @Test fun `saving and editing retain actual start time and original zone`() {
        val timed = validDraft.copy(startTime = "09:15", zoneId = "Asia/Kuala_Lumpur")
        Fixture(drafts = MemoryDraftStore(timed)).use { fixture ->
            fixture.controller.dispatch(JournalEvent.SaveRequested)
            val saved = fixture.repository.all().single()
            assertEquals(java.time.LocalTime.of(9, 15), saved.startTime)
            assertEquals("Asia/Kuala_Lumpur", saved.zoneId)
            fixture.controller.dispatch(JournalEvent.EditRequested(saved))
            fixture.controller.dispatch(JournalEvent.FieldChanged(FormField.DISTANCE, "4"))
            fixture.controller.dispatch(JournalEvent.SaveRequested)
            assertEquals(saved.startTime, fixture.repository.all().single().startTime)
            assertEquals(saved.zoneId, fixture.repository.all().single().zoneId)
        }
    }

    @Test fun `invalid form returns inline errors without writing`() {
        Fixture(drafts = MemoryDraftStore(validDraft.copy(title = "", distanceKm = ""))).use { fixture ->
            fixture.controller.dispatch(JournalEvent.SaveRequested)
            assertEquals(0, fixture.repository.inserts)
            assertEquals(setOf(FormField.TITLE, FormField.DISTANCE), fixture.controller.state.value.fieldErrors.keys)
        }
    }

    @Test fun `failed save retains all draft fields for retry`() {
        Fixture().use { fixture ->
            fixture.repository.failWrites = true
            fixture.controller.dispatch(JournalEvent.SaveRequested)
            assertEquals(validDraft, fixture.controller.state.value.draft)
            assertEquals("Disk full", fixture.controller.state.value.operationError)
            assertFalse(fixture.controller.state.value.isSaving)
        }
    }

    @Test fun `restored draft retains edit id and activity metadata`() {
        val original = Walk("saved-id", "Hill", today, 1201, 40, "notes", ActivityType.HIKE, "Hill", Effort.MODERATE)
        val store = MemoryDraftStore(WalkDraft.from(original))
        Fixture(drafts = store).use { first ->
            first.controller.dispatch(JournalEvent.FieldChanged(FormField.TITLE, "Edited hill"))
        }
        Fixture(drafts = store).use { restored ->
            assertEquals("saved-id", restored.controller.state.value.draft.id)
            assertEquals("Edited hill", restored.controller.state.value.draft.title)
            assertEquals(ActivityType.HIKE, restored.controller.state.value.draft.type)
            assertEquals("1.201", restored.controller.state.value.draft.distanceKm)
        }
    }

    @Test fun `editing updates one existing entry without inserting`() {
        val original = Walk("saved-id", "Old", today, 1000, 10, "")
        val repository = MemoryRepository().apply { entries.value = listOf(original) }
        Fixture(repository).use { fixture ->
            fixture.controller.dispatch(JournalEvent.EditRequested(original))
            fixture.controller.dispatch(JournalEvent.FieldChanged(FormField.TITLE, "New title"))
            fixture.controller.dispatch(JournalEvent.SaveRequested)
            assertEquals("saved-id", repository.entries.value.single().id)
            assertEquals("New title", repository.entries.value.single().title)
            assertEquals(0, repository.inserts)
        }
    }

    @Test fun `duplicate save and draft changes are blocked during a write`() {
        val io = QueuedDispatcher()
        Fixture(io = io).use { fixture ->
            fixture.controller.dispatch(JournalEvent.SaveRequested)
            assertTrue(fixture.controller.state.value.isSaving)
            fixture.controller.dispatch(JournalEvent.SaveRequested)
            fixture.controller.dispatch(JournalEvent.NewEntryRequested)
            assertEquals(validDraft, fixture.controller.state.value.draft)
            io.runAll()
            assertEquals(1, fixture.repository.inserts)
            assertFalse(fixture.controller.state.value.isSaving)
        }
    }

    @Test fun `delete can be undone with the same id and clears the matching editor`() {
        val original = Walk("saved-id", "Old", today, 1000, 10, "")
        val repository = MemoryRepository().apply { entries.value = listOf(original) }
        Fixture(repository).use { fixture ->
            fixture.controller.dispatch(JournalEvent.EditRequested(original))
            fixture.controller.dispatch(JournalEvent.DeleteRequested(original))
            assertTrue(repository.entries.value.isEmpty())
            assertNull(fixture.controller.state.value.draft.id)
            assertEquals(original, fixture.controller.state.value.undoEntry)
            fixture.controller.dispatch(JournalEvent.UndoDeleteRequested)
            assertEquals(listOf(original), repository.entries.value)
            assertNull(fixture.controller.state.value.undoEntry)
        }
    }

    @Test fun `read failure can be retried without clearing the draft`() {
        val repository = MemoryRepository().apply { failReads = true }
        Fixture(repository).use { fixture ->
            assertEquals("Read failed", fixture.controller.state.value.loadError)
            repository.failReads = false
            fixture.controller.dispatch(JournalEvent.RetryRequested)
            assertNull(fixture.controller.state.value.loadError)
            assertEquals(validDraft, fixture.controller.state.value.draft)
        }
    }

    @Test fun `editing a deleted entry retains the draft and reports an error`() {
        val original = Walk("gone-id", "Old", today, 1000, 10, "")
        Fixture().use { fixture ->
            fixture.controller.dispatch(JournalEvent.EditRequested(original))
            fixture.controller.dispatch(JournalEvent.SaveRequested)
            assertEquals("gone-id", fixture.controller.state.value.draft.id)
            assertNotNull(fixture.controller.state.value.operationError)
            assertEquals(0, fixture.repository.inserts)
        }
    }

    @Test fun `selected location is saved with the activity`() {
        Fixture().use { fixture ->
            val point = GeoPoint(3.139, 101.6869)
            fixture.controller.dispatch(JournalEvent.LocationSelected(point))
            fixture.controller.dispatch(JournalEvent.SaveRequested)
            assertEquals(point, fixture.repository.entries.value.single().point)
        }
    }

    @Test fun `editing a location preserves the activity id`() {
        val original = Walk("saved-id", "Park", today, 1000, 20, "", point = GeoPoint(1.0, 100.0))
        val repository = MemoryRepository().apply { entries.value = listOf(original) }
        Fixture(repository).use { fixture ->
            fixture.controller.dispatch(JournalEvent.EditRequested(original))
            fixture.controller.dispatch(JournalEvent.LocationSelected(GeoPoint(2.0, 101.0)))
            fixture.controller.dispatch(JournalEvent.SaveRequested)
            assertEquals("saved-id", repository.entries.value.single().id)
            assertEquals(GeoPoint(2.0, 101.0), repository.entries.value.single().point)
            assertEquals(0, repository.inserts)
        }
    }

    @Test fun `location draft restores and removing a pin is saved as null`() {
        val drafts = MemoryDraftStore(validDraft)
        val point = GeoPoint(0.0, 0.0)
        Fixture(drafts = drafts).use { first ->
            first.controller.dispatch(JournalEvent.LocationSelected(point))
        }
        Fixture(drafts = drafts).use { restored ->
            assertEquals(point, restored.controller.state.value.draft.point)
            restored.controller.dispatch(JournalEvent.LocationSelected(null))
            restored.controller.dispatch(JournalEvent.SaveRequested)
            assertNull(restored.repository.entries.value.single().point)
        }
    }

    @Test fun `navigation protects changed input until discard is confirmed`() {
        Fixture().use { fixture ->
            fixture.controller.dispatch(JournalEvent.FieldChanged(FormField.TITLE, "Unsaved title"))
            fixture.controller.dispatch(JournalEvent.NavigateRequested(JournalScreen.HISTORY))
            assertTrue(fixture.controller.state.value.showDiscardPrompt)
            assertEquals("Unsaved title", fixture.controller.state.value.draft.title)
            fixture.controller.dispatch(JournalEvent.DiscardCancelled)
            assertFalse(fixture.controller.state.value.showDiscardPrompt)
            fixture.controller.dispatch(JournalEvent.NavigateRequested(JournalScreen.HISTORY))
            fixture.controller.dispatch(JournalEvent.DiscardConfirmed)
            assertEquals(JournalScreen.HISTORY, fixture.controller.state.value.screen)
            assertFalse(fixture.controller.state.value.hasUnsavedChanges)
        }
    }

    @Test fun `restoration compares the saved baseline with the changed draft`() {
        val drafts = MemoryDraftStore(validDraft)
        Fixture(drafts = drafts).use { first ->
            first.controller.dispatch(JournalEvent.FieldChanged(FormField.NOTES, "Unsaved note"))
        }
        Fixture(drafts = drafts).use { restored ->
            assertTrue(restored.controller.state.value.hasUnsavedChanges)
            assertEquals(JournalScreen.EDITOR, restored.controller.state.value.screen)
        }
    }

    @Test fun `filtering changes the history without mutating records`() {
        val repository = MemoryRepository().apply {
            entries.value = listOf(Walk("1", "Hill", today, 1000, 10, "", ActivityType.HIKE), Walk("2", "Park", today, 2000, 20, ""))
        }
        Fixture(repository).use { fixture ->
            fixture.controller.dispatch(JournalEvent.FilterTypeSelected(ActivityType.HIKE))
            fixture.controller.dispatch(JournalEvent.SearchChanged("HILL"))
            assertEquals(listOf("1"), fixture.controller.state.value.filteredEntries.map { it.id })
            assertEquals(2, repository.entries.value.size)
        }
    }

    @Test fun `new entry uses today and the last successfully saved type`() {
        Fixture().use { fixture ->
            fixture.controller.dispatch(JournalEvent.TypeSelected(ActivityType.HIKE))
            fixture.controller.dispatch(JournalEvent.SaveRequested)
            assertEquals(ActivityType.HIKE, fixture.controller.state.value.draft.type)
            assertEquals(today.toString(), fixture.controller.state.value.draft.date)
            assertEquals(ActivityType.HIKE.label, fixture.controller.state.value.draft.title)
            assertEquals(JournalScreen.HOME, fixture.controller.state.value.screen)
        }
    }

    private class MemoryDocument(var contents: String = "backup") : BackupDocument {
        override fun read() = contents
        override fun write(contents: String) { this.contents = contents }
    }

    private fun backups(repository: MemoryRepository, imported: List<Walk>): JournalBackups {
        val codec = object : BackupCodec {
            override fun encode(entries: List<Walk>) = "encoded-${entries.size}"
            override fun decode(contents: String) = imported
        }
        return JournalBackups(repository, repository, codec)
    }

    @Test fun `restore preview and cancellation do not write the journal`() {
        val repository = MemoryRepository()
        val entry = Walk("imported-id", "Imported", today, 1000, 10, "")
        Fixture(repository, backups = backups(repository, listOf(entry))).use { fixture ->
            fixture.controller.dispatch(JournalEvent.ImportRequested(MemoryDocument()))
            assertEquals(1, fixture.controller.state.value.pendingImport?.additions)
            assertTrue(repository.entries.value.isEmpty())
            fixture.controller.dispatch(JournalEvent.ImportDismissed)
            assertNull(fixture.controller.state.value.pendingImport)
            assertTrue(repository.entries.value.isEmpty())
        }
    }

    @Test fun `confirmed restore updates the journal and closes the preview`() {
        val repository = MemoryRepository()
        val entry = Walk("imported-id", "Imported", today, 1000, 10, "")
        Fixture(repository, backups = backups(repository, listOf(entry))).use { fixture ->
            fixture.controller.dispatch(JournalEvent.ImportRequested(MemoryDocument()))
            fixture.controller.dispatch(JournalEvent.ImportConfirmed)
            assertEquals(listOf(entry), repository.entries.value)
            assertNull(fixture.controller.state.value.pendingImport)
            assertEquals(1, fixture.controller.state.value.summary.count)
        }
    }

    @Test fun `failed restore preserves the preview for retry`() {
        val repository = MemoryRepository()
        val entry = Walk("imported-id", "Imported", today, 1000, 10, "")
        Fixture(repository, backups = backups(repository, listOf(entry))).use { fixture ->
            fixture.controller.dispatch(JournalEvent.ImportRequested(MemoryDocument()))
            repository.failWrites = true
            fixture.controller.dispatch(JournalEvent.ImportConfirmed)
            assertTrue(repository.entries.value.isEmpty())
            assertNotNull(fixture.controller.state.value.pendingImport)
            assertEquals("Disk full", fixture.controller.state.value.operationError)
        }
    }

    @Test fun `export writes the chosen document only after encoding`() {
        val repository = MemoryRepository()
        Fixture(repository, backups = backups(repository, emptyList())).use { fixture ->
            val document = MemoryDocument()
            fixture.controller.dispatch(JournalEvent.ExportRequested(document))
            assertEquals("encoded-0", document.contents)
            assertEquals("Backup saved to your selected file.", fixture.controller.state.value.notice)
        }
    }
    @Test fun `failed file export does not mark journal as backed up`() {
        val repository = MemoryRepository()
        repository.save(Walk("one", "Park", today, 2000, 30, ""))
        val store = MemoryBackupStatusStore()
        val monitor = BackupMonitor(store)
        val codec = object : BackupCodec {
            override fun encode(entries: List<Walk>) = "file"
            override fun decode(contents: String) = emptyList<Walk>()
        }
        Fixture(repository = repository, backups = JournalBackups(repository, repository, codec), backupMonitor = monitor).use {
            it.controller.dispatch(JournalEvent.ExportRequested(object : BackupDestination { override fun write(contents: String) { error("Storage unavailable") } }))
            assertNull(store.read().receipt)
            assertNotNull(it.controller.state.value.operationError)
            assertTrue(it.controller.state.value.backup.hasUnbackedChanges)
        }
    }

    @Test fun `export status records the snapshot actually written when journal changes during export`() {
        val repository = MemoryRepository()
        val first = Walk("one", "Park", today, 2000, 30, "")
        repository.save(first)
        val store = MemoryBackupStatusStore()
        val monitor = BackupMonitor(store)
        val codec = object : BackupCodec {
            override fun encode(entries: List<Walk>) = "snapshot:${entries.size}"
            override fun decode(contents: String) = emptyList<Walk>()
        }
        Fixture(repository = repository, backups = JournalBackups(repository, repository, codec), backupMonitor = monitor).use {
            it.controller.dispatch(JournalEvent.ExportRequested(object : BackupDestination {
                override fun write(contents: String) {
                    assertEquals("snapshot:1", contents)
                    repository.save(first.copy(id = "two"))
                }
            }))
            assertEquals(1, store.read().receipt?.entries)
            assertTrue(it.controller.state.value.backup.hasUnbackedChanges)
            assertNull(it.controller.state.value.operationError)
        }
    }

    @Test fun `receipt failure after completed file write is not a failed export`() {
        val repository = MemoryRepository()
        repository.save(Walk("one", "Park", today, 2000, 30, ""))
        var writes = 0
        val store = object : BackupStatusStore {
            override fun read() = BackupPreferences()
            override fun write(preferences: BackupPreferences) { error("Preferences unavailable") }
        }
        val codec = object : BackupCodec {
            override fun encode(entries: List<Walk>) = "file"
            override fun decode(contents: String) = emptyList<Walk>()
        }
        Fixture(repository = repository, backups = JournalBackups(repository, repository, codec), backupMonitor = BackupMonitor(store)).use {
            it.controller.dispatch(JournalEvent.ExportRequested(object : BackupDestination { override fun write(contents: String) { writes++ } }))
            assertEquals(1, writes)
            assertNull(it.controller.state.value.operationError)
            assertTrue(it.controller.state.value.notice!!.startsWith("Backup file saved."))
            assertNull(store.read().receipt)
        }
    }

    @Test fun `reset filters clears search type and period without changing journal entries`() {
        Fixture().use { fixture ->
            fixture.controller.dispatch(JournalEvent.SearchChanged("Park"))
            fixture.controller.dispatch(JournalEvent.FilterTypeSelected(ActivityType.HIKE))
            fixture.controller.dispatch(JournalEvent.FilterPeriodSelected(HistoryPeriod.THIS_MONTH))
            fixture.controller.dispatch(JournalEvent.FiltersCleared)
            assertEquals(HistoryFilter(), fixture.controller.state.value.historyFilter)
            assertEquals(0, fixture.repository.inserts)
        }
    }

}
