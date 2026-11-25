package dev.michelle.trails

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.michelle.trails.domain.*
import dev.michelle.trails.presentation.JournalUiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HealthSyncUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedStatusExposesPerActivityRetry() {
        var retried: String? = null
        val walk = Walk("one", "Park", LocalDate.of(2026, 1, 2), 2000, 30, "")
        compose.setContent {
            MaterialTheme {
                ActivityCard(walk, true, {}, {}, syncStatus = ActivityHealthStatus(ActivityHealthState.FAILED, error = "Provider unavailable"),
                    onRetryHealth = { retried = it })
            }
        }
        compose.onNodeWithText("Sync failed").assertIsDisplayed()
        compose.onNodeWithText("Retry sharing").performClick()
        assertEquals("one", retried)
    }

    @Test fun startTimeFieldAndItsValidationAreVisibleInTheEditor() {
        val state = JournalUiState(draft = WalkDraft(date = "2026-01-02"),
            fieldErrors = mapOf(FormField.START_TIME to "Choose an earlier start time."))
        compose.setContent { MaterialTheme { EntryEditor(state) {} } }
        compose.onNodeWithTag("field-START_TIME").assertIsDisplayed()
        compose.onNodeWithText("Choose an earlier start time.").assertIsDisplayed()
    }
}
