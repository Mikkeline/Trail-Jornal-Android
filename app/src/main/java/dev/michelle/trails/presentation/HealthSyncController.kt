package dev.michelle.trails.presentation

import dev.michelle.trails.domain.HealthSyncEngine
import dev.michelle.trails.domain.HealthSyncReport
import dev.michelle.trails.domain.WalkObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class HealthSyncUiState(
    val report: HealthSyncReport = HealthSyncReport(false, 0, 0),
    val isBusy: Boolean = false,
    val error: String? = null,
)

sealed interface HealthSyncEvent {
    data class ActiveChanged(val active: Boolean) : HealthSyncEvent
    data class EnabledChanged(val enabled: Boolean) : HealthSyncEvent
    data object Refresh : HealthSyncEvent
    data object Retry : HealthSyncEvent
    data class RetryActivity(val id: String) : HealthSyncEvent
}

/** Reconcile committed journal changes while the app is active, and retry on resume. */
class HealthSyncController(
    observer: WalkObserver,
    private val sync: HealthSyncEngine,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutableState = MutableStateFlow(HealthSyncUiState())
    val state = mutableState.asStateFlow()
    private var active = false
    private val operations = Mutex()

    init {
        scope.launch { sync.changes().catch { error -> mutableState.update { it.copy(error = error.message) } }
            .collect { if (active) reconcile() } }
        scope.launch {
            observer.observe().catch { error -> mutableState.update { it.copy(error = error.message ?: "Could not observe activities for health updates.") } }
                .collect { if (active) reconcile() }
        }
    }

    fun dispatch(event: HealthSyncEvent) {
        when (event) {
            is HealthSyncEvent.ActiveChanged -> {
                active = event.active
                if (active) reconcile(forceRetry = true)
            }
            is HealthSyncEvent.EnabledChanged -> reconcile(event.enabled)
            HealthSyncEvent.Refresh -> reconcile(inspectOnly = true)
            HealthSyncEvent.Retry -> reconcile(forceRetry = true)
            is HealthSyncEvent.RetryActivity -> reconcile(forceRetry = true, walkId = event.id)
        }
    }

    private fun reconcile(enabled: Boolean? = null, forceRetry: Boolean = false, walkId: String? = null, inspectOnly: Boolean = false) {
        scope.launch {
            operations.withLock {
                if (!active && enabled == null) return@withLock
                mutableState.update { it.copy(isBusy = true, error = null) }
                try {
                    val report = withContext(ioDispatcher) {
                        if (forceRetry) sync.retry(walkId)
                        if (enabled != null) sync.setEnabled(enabled)
                        if (inspectOnly) sync.inspect() else sync.synchronize()
                    }
                    mutableState.update { it.copy(report = report, error = report.error) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    mutableState.update { it.copy(error = error.message ?: "Automatic health update failed. Retry when available.") }
                } finally {
                    mutableState.update { it.copy(isBusy = false) }
                }
            }
        }
    }
}
