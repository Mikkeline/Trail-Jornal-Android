package dev.michelle.trails

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.michelle.trails.domain.FormField
import dev.michelle.trails.presentation.JournalEvent
import dev.michelle.trails.presentation.JournalUiState
import dev.michelle.trails.presentation.JournalScreen as Destination
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JournalScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun fieldErrorsAreRenderedBesideTheInput() {
        val state = JournalUiState(screen = Destination.EDITOR, isLoading = false, fieldErrors = mapOf(FormField.TITLE to "Enter a name."))
        compose.setContent { MaterialTheme { JournalScreen(state, onEvent = {}) } }
        compose.onNodeWithText("Enter a name.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun typingDispatchesAnEventInsteadOfWritingStorageFromTheScreen() {
        val events = mutableListOf<JournalEvent>()
        compose.setContent { MaterialTheme { JournalScreen(JournalUiState(screen = Destination.EDITOR), onEvent = { events += it }) } }
        compose.onNodeWithText("Add details").performScrollTo().performClick()
        compose.onNodeWithTag("field-TITLE").performScrollTo().performTextInput("Morning walk")
        assertTrue(events.contains(JournalEvent.FieldChanged(FormField.TITLE, "Morning walk")))
    }
}
