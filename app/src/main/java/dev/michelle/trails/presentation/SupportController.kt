package dev.michelle.trails.presentation

import dev.michelle.trails.domain.DiagnosticsReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SupportUiState(val isBusy: Boolean = false, val preview: String? = null, val error: String? = null, val message: String? = null)
sealed interface SupportEvent {
    data object PreviewRequested : SupportEvent
    data object PreviewDismissed : SupportEvent
    data class ExportRequested(val document: BackupDestination) : SupportEvent
}

class SupportController(private val reader: DiagnosticsReader, private val scope: CoroutineScope, private val io: CoroutineDispatcher) {
    private val mutableState = MutableStateFlow(SupportUiState())
    val state = mutableState.asStateFlow()

    fun dispatch(event: SupportEvent) {
        if (state.value.isBusy) return
        when (event) {
            SupportEvent.PreviewDismissed -> mutableState.value = SupportUiState()
            SupportEvent.PreviewRequested -> operation("Could not prepare diagnostics. Reopen the app and try again.") {
                val report = withContext(io) { reader.prepare() }
                mutableState.update { it.copy(preview = report) }
            }
            is SupportEvent.ExportRequested -> {
                val reviewed = state.value.preview ?: return
                operation("Could not save the report. Choose a writable location and retry.") {
                    withContext(io) { event.document.write(reviewed) }
                    mutableState.update { it.copy(message = "Diagnostic report saved to your selected file.") }
                }
            }
        }
    }

    private fun operation(failureMessage: String, block: suspend () -> Unit) {
        mutableState.update { it.copy(isBusy = true, error = null, message = null) }
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(error = failureMessage) } }
            finally { mutableState.update { it.copy(isBusy = false) } }
        }
    }
}
