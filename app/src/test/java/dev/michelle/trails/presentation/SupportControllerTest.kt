package dev.michelle.trails.presentation

import dev.michelle.trails.domain.DiagnosticsReader
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SupportControllerTest {
    private class Fixture : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var text = "Reviewed report"
        var reads = 0
        var failure: Exception? = null
        val controller = SupportController(object : DiagnosticsReader {
            override suspend fun prepare(): String { reads++; failure?.let { throw it }; return text }
        }, scope, Dispatchers.Unconfined)
        override fun close() = scope.cancel()
    }

    @Test fun `export requires an explicitly prepared preview`() {
        Fixture().use {
            var writes = 0
            it.controller.dispatch(SupportEvent.ExportRequested(object : BackupDestination { override fun write(contents: String) { writes++ } }))
            assertEquals(0, writes); assertEquals(0, it.reads)
        }
    }

    @Test fun `saved report exactly matches the reviewed snapshot`() {
        Fixture().use {
            it.controller.dispatch(SupportEvent.PreviewRequested)
            it.text = "Changed environmental status"
            var saved: String? = null
            it.controller.dispatch(SupportEvent.ExportRequested(object : BackupDestination { override fun write(contents: String) { saved = contents } }))
            assertEquals("Reviewed report", saved); assertEquals(1, it.reads)
            assertNotNull(it.controller.state.value.message)
        }
    }

    @Test fun `failed save retains preview and never leaks a raw exception or reports success`() {
        Fixture().use {
            it.controller.dispatch(SupportEvent.PreviewRequested)
            it.controller.dispatch(SupportEvent.ExportRequested(object : BackupDestination { override fun write(contents: String) { error("secret-document-path") } }))
            assertEquals("Reviewed report", it.controller.state.value.preview)
            assertNull(it.controller.state.value.message)
            assertFalse(it.controller.state.value.error!!.contains("secret-document-path"))
            assertFalse(it.controller.state.value.isBusy)
        }
    }

    @Test fun `dismiss clears preview and failure does not expose provider internals`() {
        Fixture().use {
            it.failure = IllegalStateException("secret-provider-error")
            it.controller.dispatch(SupportEvent.PreviewRequested)
            assertNull(it.controller.state.value.preview)
            assertFalse(it.controller.state.value.error!!.contains("secret-provider-error"))
            it.controller.dispatch(SupportEvent.PreviewDismissed)
            assertEquals(SupportUiState(), it.controller.state.value)
        }
    }
}
