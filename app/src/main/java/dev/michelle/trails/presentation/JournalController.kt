package dev.michelle.trails.presentation

import dev.michelle.trails.domain.*
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screen coordination stays testable without Android, Room, or Compose. Dispatch on the host thread. */
class JournalController(
    private val observer: WalkObserver,
    private val addWalk: AddWalk,
    private val editWalk: EditWalk,
    private val deleteWalk: DeleteWalk,
    private val restoreDeletedWalk: RestoreDeletedWalk,
    private val draftStore: DraftStore,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val backups: JournalBackups? = null,
    private val preferences: JournalPreferences = MemoryJournalPreferences(),
    private val validator: ValidateWalkDraft = ValidateWalkDraft(),
    private val today: () -> LocalDate = LocalDate::now,
    private val backupMonitor: BackupMonitor = BackupMonitor(MemoryBackupStatusStore()),
) {
    private var baseline = draftStore.readBaseline() ?: newDraft()
    private val restoredDraft = draftStore.read() ?: baseline
    private val mutableState = MutableStateFlow(
        JournalUiState(
            draft = restoredDraft,
            hasUnsavedChanges = restoredDraft != baseline,
            screen = if (restoredDraft != baseline) JournalScreen.EDITOR else JournalScreen.HOME,
        ),
    )
    val state: StateFlow<JournalUiState> = mutableState.asStateFlow()
    private var observation: Job? = null
    private var pendingAction: EditorAction? = null
    private var backupRefreshVersion = 0L

    init { observeEntries() }

    fun dispatch(event: JournalEvent) {
        if (event == JournalEvent.NoticeDismissed) {
            mutableState.update { it.copy(notice = null, undoEntry = null) }
            return
        }
        if (state.value.isSaving) return
        when (event) {
            is JournalEvent.NavigateRequested -> if (event.screen != state.value.screen) requestAction(EditorAction.Navigate(event.screen))
            JournalEvent.FiltersCleared -> updateFilter(HistoryFilter())
            is JournalEvent.SearchChanged -> updateFilter(state.value.historyFilter.copy(query = event.query))
            is JournalEvent.FilterTypeSelected -> updateFilter(state.value.historyFilter.copy(type = event.type))
            is JournalEvent.FilterPeriodSelected -> updateFilter(state.value.historyFilter.copy(period = event.period))
            is JournalEvent.FieldChanged -> changeField(event)
            is JournalEvent.LocationSelected -> updateDraft(state.value.draft.copy(point = event.point))
            is JournalEvent.TypeSelected -> changeType(event.type)
            is JournalEvent.EffortSelected -> updateDraft(state.value.draft.copy(effort = event.effort))
            is JournalEvent.EditRequested -> requestAction(EditorAction.Edit(event.walk))
            is JournalEvent.DeleteRequested -> deleteEntry(event.walk)
            is JournalEvent.ExportRequested -> exportBackup(event.document)
            is JournalEvent.ImportRequested -> previewImport(event.document)
            is JournalEvent.BackupRemindersChanged -> updateBackupSettings { backupMonitor.reminders(event.enabled) }
            JournalEvent.BackupSnoozed -> updateBackupSettings { backupMonitor.snooze() }
            JournalEvent.BackupStatusRefresh -> scope.launch { refreshBackupStatus(state.value.entries) }
            JournalEvent.ImportConfirmed -> confirmImport()
            JournalEvent.ImportDismissed -> mutableState.update { it.copy(pendingImport = null) }
            JournalEvent.DiscardConfirmed -> discardAndContinue()
            JournalEvent.DiscardCancelled -> {
                pendingAction = null
                mutableState.update { it.copy(showDiscardPrompt = false) }
            }
            JournalEvent.UndoDeleteRequested -> undoDelete()
            JournalEvent.SaveRequested -> saveEntry()
            JournalEvent.NewEntryRequested -> requestAction(EditorAction.New)
            JournalEvent.RetryRequested -> observeEntries()
            JournalEvent.ErrorDismissed -> mutableState.update { it.copy(operationError = null) }
            JournalEvent.NoticeDismissed -> mutableState.update { it.copy(notice = null, undoEntry = null) }
        }
    }

    private fun requestAction(action: EditorAction) {
        if (state.value.hasUnsavedChanges) {
            pendingAction = action
            mutableState.update { it.copy(showDiscardPrompt = true) }
        } else {
            performAction(action)
        }
    }

    private fun discardAndContinue() {
        val action = pendingAction ?: return
        pendingAction = null
        mutableState.update { it.copy(showDiscardPrompt = false) }
        resetEditor(newDraft())
        performAction(action)
    }

    private fun performAction(action: EditorAction) {
        when (action) {
            is EditorAction.Edit -> {
                resetEditor(WalkDraft.from(action.walk))
                mutableState.update { it.copy(screen = JournalScreen.EDITOR) }
            }
            EditorAction.New -> {
                resetEditor(newDraft())
                mutableState.update { it.copy(screen = JournalScreen.EDITOR) }
            }
            is EditorAction.Navigate -> {
                resetEditor(newDraft())
                mutableState.update { it.copy(screen = action.screen) }
            }
        }
    }

    private fun observeEntries() {
        observation?.cancel()
        mutableState.update { it.copy(isLoading = true, loadError = null) }
        observation = scope.launch {
            observer.observe()
                .catch { error -> mutableState.update { it.copy(isLoading = false, loadError = error.message ?: "Could not load your journal.") } }
                .collect { entries ->
                    val sorted = FilterHistory().execute(entries, HistoryFilter(), today())
                    val weekly = FilterHistory().execute(entries, HistoryFilter(period = HistoryPeriod.THIS_WEEK), today())
                    mutableState.update {
                        it.copy(
                            entries = sorted,
                            summary = SummarizeWalks().execute(entries),
                            weekSummary = SummarizeWalks().execute(weekly),
                            filteredEntries = FilterHistory().execute(entries, it.historyFilter, today()),
                            isLoading = false,
                            loadError = null,
                        )
                    }
                    refreshBackupStatus(sorted)
                }
        }
    }

    private fun updateFilter(filter: HistoryFilter) {
        mutableState.update {
            it.copy(historyFilter = filter, filteredEntries = FilterHistory().execute(it.entries, filter, today()))
        }
    }

    private fun changeField(event: JournalEvent.FieldChanged) {
        val draft = state.value.draft
        val updated = when (event.field) {
            FormField.TITLE -> draft.copy(title = event.value)
            FormField.DATE -> draft.copy(date = event.value)
            FormField.DISTANCE -> draft.copy(distanceKm = event.value)
            FormField.DURATION -> draft.copy(durationMinutes = event.value)
            FormField.START_TIME -> draft.copy(startTime = event.value,
                zoneId = if (event.value.isBlank()) "" else draft.zoneId.ifBlank { java.time.ZoneId.systemDefault().id })
            FormField.LOCATION -> draft.copy(location = event.value)
            FormField.NOTES -> draft.copy(notes = event.value)
        }
        updateDraft(updated)
        mutableState.update { it.copy(fieldErrors = it.fieldErrors - event.field) }
    }

    private fun changeType(type: ActivityType) {
        val draft = state.value.draft
        val title = if (draft.title == draft.type.label) type.label else draft.title
        updateDraft(draft.copy(type = type, title = title))
    }

    private fun updateDraft(draft: WalkDraft) {
        draftStore.write(draft)
        mutableState.update {
            it.copy(draft = draft, hasUnsavedChanges = draft != baseline, operationError = null)
        }
    }

    private fun resetEditor(draft: WalkDraft) {
        baseline = draft
        draftStore.writeBaseline(draft)
        draftStore.write(draft)
        mutableState.update {
            it.copy(draft = draft, hasUnsavedChanges = false, fieldErrors = emptyMap(), operationError = null)
        }
    }

    private fun saveEntry() {
        val draft = state.value.draft
        val entryId = draft.id
        val validation = validator.execute(draft)
        val value = validation.value
        if (value == null) {
            mutableState.update { it.copy(fieldErrors = validation.errors, operationError = null) }
            return
        }
        runMutation {
            withContext(ioDispatcher) {
                if (entryId == null) {
                    addWalk.execute(value.title, value.date, value.meters, value.minutes, value.notes, value.type, value.location, value.effort, value.point, value.startTime, value.zoneId)
                } else {
                    editWalk.execute(entryId, value)
                }
            }
            // Preference failure must not turn a committed activity into a failed-save retry.
            runCatching { preferences.rememberActivityType(draft.type) }
            resetEditor(newDraft())
            mutableState.update { it.copy(screen = JournalScreen.HOME) }
            showNotice(if (entryId == null) "Activity saved." else "Activity updated.")
        }
    }

    private fun deleteEntry(walk: Walk) {
        runMutation {
            withContext(ioDispatcher) { deleteWalk.execute(walk.id) }
            if (state.value.draft.id == walk.id) resetEditor(newDraft())
            showNotice("Activity deleted. You can undo this.", undoEntry = walk)
        }
    }

    private fun undoDelete() {
        val entry = state.value.undoEntry ?: return
        runMutation {
            withContext(ioDispatcher) { restoreDeletedWalk.execute(entry) }
            mutableState.update { it.copy(undoEntry = null) }
            showNotice("Activity restored.")
        }
    }

    private fun exportBackup(document: BackupDestination) {
        runMutation {
            val backup = withContext(ioDispatcher) {
                val prepared = requireNotNull(backups) { "Backup is unavailable." }.prepareExport()
                document.write(prepared.contents)
                prepared
            }
            // A completed file must not be reported as failed merely because local status could not be saved.
            val recorded = try {
                withContext(ioDispatcher) { backupMonitor.recorded(backup) }
                true
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
            refreshBackupStatus(state.value.entries)
            showNotice(if (recorded) "Backup saved to your selected file." else "Backup file saved. Could not update local backup status; keep the file you selected.")
        }
    }

    private fun updateBackupSettings(update: () -> Unit) {
        runMutation {
            withContext(ioDispatcher) { update() }
            refreshBackupStatus(state.value.entries)
        }
    }

    private suspend fun refreshBackupStatus(entries: List<Walk>) {
        val version = ++backupRefreshVersion
        val overview = try {
            withContext(ioDispatcher) { backupMonitor.overview(entries) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { BackupOverview(error = error.message ?: "Could not load backup status.") }
        mutableState.update { if (version == backupRefreshVersion && it.entries == entries) it.copy(backup = overview) else it }
    }

    private fun previewImport(document: BackupSource) {
        runMutation {
            val plan = withContext(ioDispatcher) {
                requireNotNull(backups) { "Restore is unavailable." }.preview(document.read())
            }
            mutableState.update { it.copy(pendingImport = plan) }
        }
    }

    private fun confirmImport() {
        val plan = state.value.pendingImport ?: return
        runMutation {
            val report = withContext(ioDispatcher) { requireNotNull(backups).restore(plan) }
            mutableState.update { it.copy(pendingImport = null) }
            showNotice("Restored ${report.added} activities; kept ${report.skipped} existing activities.")
        }
    }

    private fun runMutation(operation: suspend () -> Unit) {
        mutableState.update { it.copy(isSaving = true, operationError = null) }
        scope.launch {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(operationError = error.message ?: "Could not complete this action.") }
            } finally {
                mutableState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun showNotice(message: String, undoEntry: Walk? = null) {
        mutableState.update { it.copy(notice = message, noticeId = it.noticeId + 1, undoEntry = undoEntry) }
    }

    private fun newDraft(): WalkDraft {
        val type = preferences.lastActivityType()
        return WalkDraft(title = type.label, date = today().toString(), type = type)
    }

    private sealed interface EditorAction {
        data object New : EditorAction
        data class Edit(val walk: Walk) : EditorAction
        data class Navigate(val screen: JournalScreen) : EditorAction
    }
}
