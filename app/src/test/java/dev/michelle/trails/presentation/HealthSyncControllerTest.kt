package dev.michelle.trails.presentation

import dev.michelle.trails.domain.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.*
import org.junit.Test

class HealthSyncControllerTest {
    private class Fixture : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val entries = MutableStateFlow(listOf(Walk("one", "Walk", LocalDate.of(2026, 1, 2), 1000, 20, "")))
        var runs = 0
        var inspections = 0
        var retries = 0
        var enableRequest: Boolean? = null
        var retryId: String? = null
        var report = HealthSyncReport(true, 1, 0)
        val notifications = MutableStateFlow(0)
        val sync = object : HealthSyncEngine {
            override suspend fun share(walk: Walk, startTime: LocalTime, zone: ZoneId) = Unit
            override suspend fun setEnabled(enabled: Boolean) { enableRequest = enabled; report = report.copy(enabled = enabled) }
            override suspend fun retry(walkId: String?) { retries++; retryId = walkId }
            override suspend fun inspect(): HealthSyncReport { inspections++; return report }
            override suspend fun synchronize(): HealthSyncReport { runs++; return report }
            override fun changes() = notifications.map { Unit }
        }
        val controller = HealthSyncController(object : WalkObserver { override fun observe() = entries }, sync, scope, Dispatchers.Unconfined)
        fun edit() { entries.value = entries.value.map { it.copy(distanceMeters = 2000) } }
        override fun close() = scope.cancel()
    }

    @Test fun `active journal commits trigger reconciliation`() {
        Fixture().use {
            assertEquals(0, it.runs)
            it.controller.dispatch(HealthSyncEvent.ActiveChanged(true))
            val before = it.runs
            it.edit()
            assertEquals(before + 1, it.runs)
        }
    }

    @Test fun `inactive edits wait for resume and queue notifications refresh active status`() {
        Fixture().use {
            it.edit(); assertEquals(0, it.runs)
            it.controller.dispatch(HealthSyncEvent.ActiveChanged(true))
            val before = it.runs
            it.report = it.report.copy(pending = 1, error = "Needs permission")
            it.notifications.value++
            assertEquals(before + 1, it.runs)
            assertEquals("Needs permission", it.controller.state.value.error)
        }
    }

    @Test fun `settings and per activity retry are delegated through the sync port`() {
        Fixture().use {
            it.controller.dispatch(HealthSyncEvent.ActiveChanged(true))
            it.controller.dispatch(HealthSyncEvent.EnabledChanged(false))
            assertEquals(false, it.enableRequest)
            assertFalse(it.controller.state.value.report.enabled)
            it.controller.dispatch(HealthSyncEvent.RetryActivity("one"))
            assertEquals("one", it.retryId)
        }
    }

    @Test fun `refreshing the status never resets retry backoff or triggers a provider write`() {
        Fixture().use {
            it.controller.dispatch(HealthSyncEvent.ActiveChanged(true))
            val writes = it.runs
            val retries = it.retries
            it.controller.dispatch(HealthSyncEvent.Refresh)
            assertEquals(writes, it.runs)
            assertEquals(retries, it.retries)
            assertEquals(1, it.inspections)
        }
    }
}
