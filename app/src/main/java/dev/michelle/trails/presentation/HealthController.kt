package dev.michelle.trails.presentation

import dev.michelle.trails.domain.*
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HealthUiState(
    val access: HealthAccess = HealthAccess(HealthAvailability.UNAVAILABLE),
    val isBusy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val preview: HealthImportPlan? = null,
    val selectedIds: Set<String> = emptySet(),
    val shareWalk: Walk? = null,
    val shareStartTime: String = "",
    val sharedActivityTitle: String? = null,
)

sealed interface HealthEvent {
    data object Refresh : HealthEvent
    data object PreviewRequested : HealthEvent
    data class SelectionChanged(val id: String) : HealthEvent
    data object SelectAll : HealthEvent
    data object ClearSelection : HealthEvent
    data object ImportConfirmed : HealthEvent
    data object PreviewDismissed : HealthEvent
    data class ShareRequested(val walk: Walk) : HealthEvent
    data class StartTimeSelected(val time: String) : HealthEvent
    data object ShareConfirmed : HealthEvent
    data object ShareDismissed : HealthEvent
    data object ShareResultDismissed : HealthEvent
}

class HealthController(
    private val accessReader: HealthAccessReader,
    private val health: JournalHealth,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val sharer: HealthActivitySharer = health,
) {
    private val mutableState = MutableStateFlow(HealthUiState())
    val state = mutableState.asStateFlow()

    fun dispatch(event: HealthEvent) {
        if (state.value.isBusy) return
        when (event) {
            HealthEvent.Refresh -> operation(clearMessage = false) { mutableState.update { it.copy(access = accessReader.access()) } }
            HealthEvent.PreviewRequested -> operation {
                val access = accessReader.access()
                mutableState.update { it.copy(access = access) }
                check(access.canRead) { "Allow workout and distance reading in Health Connect first." }
                val plan = health.preview()
                mutableState.update { it.copy(preview = plan, selectedIds = plan.candidates.map { candidate -> candidate.walk.id }.toSet()) }
            }
            is HealthEvent.SelectionChanged -> mutableState.update {
                val selected = if (event.id in it.selectedIds) it.selectedIds - event.id else it.selectedIds + event.id
                it.copy(selectedIds = selected)
            }
            HealthEvent.SelectAll -> mutableState.update { it.copy(selectedIds = it.preview?.candidates?.map { item -> item.walk.id }?.toSet().orEmpty()) }
            HealthEvent.ClearSelection -> mutableState.update { it.copy(selectedIds = emptySet()) }
            HealthEvent.ImportConfirmed -> {
                val plan = state.value.preview ?: return
                val selected = state.value.selectedIds
                operation {
                    val access = accessReader.access()
                    mutableState.update { it.copy(access = access) }
                    check(access.canRead) { "Workout reading was revoked. Allow access again before importing." }
                    val result = health.import(plan, selected)
                    mutableState.update { it.copy(preview = null, selectedIds = emptySet(), message = "Imported ${result.added} workouts; kept ${result.skipped} existing entries.") }
                }
            }
            HealthEvent.PreviewDismissed -> mutableState.update { it.copy(preview = null, selectedIds = emptySet()) }
            is HealthEvent.ShareRequested -> mutableState.update { it.copy(shareWalk = event.walk, shareStartTime = event.walk.startTime?.toString().orEmpty(), error = null, message = null) }
            is HealthEvent.StartTimeSelected -> mutableState.update { it.copy(shareStartTime = event.time, error = null) }
            HealthEvent.ShareDismissed -> mutableState.update { it.copy(shareWalk = null, shareStartTime = "", error = null) }
            HealthEvent.ShareResultDismissed -> mutableState.update { it.copy(sharedActivityTitle = null) }
            HealthEvent.ShareConfirmed -> share()
        }
    }

    private fun share() {
        val walk = state.value.shareWalk ?: return
        val selectedTime = state.value.shareStartTime
        operation {
            val access = accessReader.access()
            mutableState.update { it.copy(access = access) }
            check(access.canWrite) { "Allow workout and distance sharing in Health Connect first." }
            val time = try { LocalTime.parse(selectedTime) } catch (error: Exception) { throw IllegalArgumentException("Choose the actual activity start time.") }
            sharer.share(walk, time, walk.zoneId?.let(ZoneId::of) ?: zone())
            mutableState.update { it.copy(shareWalk = null, sharedActivityTitle = walk.title, message = "Workout and distance saved to Health Connect. The receiving app must support reading these data types and have its own permission.") }
        }
    }

    private fun operation(clearMessage: Boolean = true, block: suspend () -> Unit) {
        mutableState.update { it.copy(isBusy = true, error = null, message = if (clearMessage) null else it.message) }
        scope.launch {
            try {
                withContext(ioDispatcher) { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.message ?: "Could not complete the Health Connect action.") }
            } finally {
                mutableState.update { it.copy(isBusy = false) }
            }
        }
    }
}
