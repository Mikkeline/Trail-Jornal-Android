package dev.michelle.trails

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.michelle.trails.domain.WalkDraft
import dev.michelle.trails.presentation.JournalEvent
import dev.michelle.trails.presentation.JournalUiState
import dev.michelle.trails.presentation.JournalScreen as Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JournalRedesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun toolsOpenHealthWithoutNavigatingAwayFromAnUnsavedForm() {
        val events = mutableListOf<JournalEvent>()
        var opened = 0
        val state = JournalUiState(screen = Destination.EDITOR, isLoading = false,
            draft = WalkDraft(title = "Unsaved walk", date = "2026-01-02"), hasUnsavedChanges = true)
        compose.setContent { TrailTheme { JournalScreen(state, { events += it }, onOpenHealth = { opened++ }) } }
        compose.onNodeWithContentDescription("Journal tools").performClick()
        compose.onNodeWithText("Health apps & smartwatches").performClick()
        assertEquals(1, opened)
        assertTrue(events.isEmpty())
    }

    @Test fun saveRemainsReachableAfterScrollingThroughLongOptionalDetails() {
        val events = mutableListOf<JournalEvent>()
        val state = JournalUiState(screen = Destination.EDITOR, isLoading = false,
            draft = WalkDraft(id = "one", title = "Park", date = "2026-01-02", notes = "A longer journal note. ".repeat(35)))
        compose.setContent { TrailTheme { JournalScreen(state, { events += it }) } }
        compose.onNodeWithTag("field-NOTES").performScrollTo()
        compose.onNodeWithTag("save-entry").assertIsDisplayed().performClick()
        assertEquals(listOf(JournalEvent.SaveRequested), events)
    }
}
