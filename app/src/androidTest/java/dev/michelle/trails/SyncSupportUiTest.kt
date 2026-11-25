package dev.michelle.trails

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.michelle.trails.domain.*
import dev.michelle.trails.presentation.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SyncSupportUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deletedActivitiesRemainVisibleAndRefreshUsesTheReadOnlyEvent() {
        val events = mutableListOf<HealthSyncEvent>()
        val entry = SyncHistoryEntry("deleted", null, HealthJobOperation.DELETE, SyncHistoryState.REMOVED,
            1_000, null, 0, null, false)
        compose.setContent {
            TrailTheme {
                SyncHistoryDialog(HealthSyncUiState(HealthSyncReport(true, 0, 0, history = listOf(entry))), SupportUiState(),
                    { events += it }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Refresh status").performClick()
        assertEquals(listOf(HealthSyncEvent.Refresh), events)
        compose.onNodeWithText("Completed").performScrollTo().performClick()
        compose.onNodeWithTag("sync-history-list").performScrollToNode(hasText("Removed from Health Connect"))
        compose.onNodeWithText("Removed from Health Connect").assertIsDisplayed()
    }

    @Test fun diagnosticsPreviewRequiresAnExplicitSaveAction() {
        var saves = 0
        compose.setContent {
            TrailTheme {
                SupportDiagnosticsDialog(SupportUiState(preview = "format_version: 1\nraw_errors_included: no\n"), {}, { saves++ })
            }
        }
        assertEquals(0, saves)
        compose.onNodeWithText("Save report").performClick()
        assertEquals(1, saves)
    }
}
