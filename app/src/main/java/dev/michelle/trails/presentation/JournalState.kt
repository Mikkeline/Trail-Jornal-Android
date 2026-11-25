package dev.michelle.trails.presentation

import dev.michelle.trails.domain.*

enum class JournalScreen { HOME, HISTORY, EDITOR }

data class JournalUiState(
    val screen: JournalScreen = JournalScreen.HOME,
    val entries: List<Walk> = emptyList(),
    val summary: Summary = Summary(0, 0, 0),
    val weekSummary: Summary = Summary(0, 0, 0),
    val historyFilter: HistoryFilter = HistoryFilter(),
    val filteredEntries: List<Walk> = emptyList(),
    val draft: WalkDraft = WalkDraft(),
    val hasUnsavedChanges: Boolean = false,
    val showDiscardPrompt: Boolean = false,
    val fieldErrors: Map<FormField, String> = emptyMap(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: String? = null,
    val operationError: String? = null,
    val notice: String? = null,
    val noticeId: Long = 0,
    val undoEntry: Walk? = null,
    val pendingImport: ImportPlan? = null,
    val backup: BackupOverview = BackupOverview(),
)

interface DraftStore {
    fun read(): WalkDraft?
    fun write(draft: WalkDraft)
    fun readBaseline(): WalkDraft? = null
    fun writeBaseline(draft: WalkDraft) = Unit
}

interface JournalPreferences {
    fun lastActivityType(): ActivityType
    fun rememberActivityType(type: ActivityType)
}

class MemoryJournalPreferences : JournalPreferences {
    private var type = ActivityType.OUTDOOR_WALK
    override fun lastActivityType() = type
    override fun rememberActivityType(type: ActivityType) { this.type = type }
}

/** The app adapter owns bounded document IO. No Android URI leaks into presentation. */
interface BackupSource {
    fun read(): String
}

interface BackupDestination {
    fun write(contents: String)
}

interface BackupDocument : BackupSource, BackupDestination

sealed interface JournalEvent {
    data class NavigateRequested(val screen: JournalScreen) : JournalEvent
    data object FiltersCleared : JournalEvent
    data class SearchChanged(val query: String) : JournalEvent
    data class FilterTypeSelected(val type: ActivityType?) : JournalEvent
    data class FilterPeriodSelected(val period: HistoryPeriod) : JournalEvent
    data class FieldChanged(val field: FormField, val value: String) : JournalEvent
    data class TypeSelected(val type: ActivityType) : JournalEvent
    data class LocationSelected(val point: GeoPoint?) : JournalEvent
    data class EffortSelected(val effort: Effort?) : JournalEvent
    data class EditRequested(val walk: Walk) : JournalEvent
    data class DeleteRequested(val walk: Walk) : JournalEvent
    data class ExportRequested(val document: BackupDestination) : JournalEvent
    data class ImportRequested(val document: BackupSource) : JournalEvent
    data class BackupRemindersChanged(val enabled: Boolean) : JournalEvent
    data object BackupSnoozed : JournalEvent
    data object BackupStatusRefresh : JournalEvent
    data object ImportConfirmed : JournalEvent
    data object ImportDismissed : JournalEvent
    data object DiscardConfirmed : JournalEvent
    data object DiscardCancelled : JournalEvent
    data object UndoDeleteRequested : JournalEvent
    data object SaveRequested : JournalEvent
    data object NewEntryRequested : JournalEvent
    data object RetryRequested : JournalEvent
    data object ErrorDismissed : JournalEvent
    data object NoticeDismissed : JournalEvent
}
