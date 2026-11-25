package dev.michelle.trails

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.michelle.trails.domain.*
import dev.michelle.trails.presentation.*
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HealthLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun preview(): HealthUiState {
        val start = Instant.parse("2026-01-02T08:00:00Z")
        val candidates = (1..30).map { number ->
            HealthCandidate(Walk("external-$number", "Workout $number", LocalDate.of(2026, 1, 2), 2000, 30, ""),
                "Example source", start, start.plusSeconds(1800))
        }
        return HealthUiState(preview = HealthImportPlan(candidates, 0, 0, false))
    }

    @Test fun choosingTheLastWorkoutDoesNotImportUntilTheFixedButtonIsPressed() {
        val events = mutableListOf<HealthEvent>()
        compose.setContent {
            var state by remember { mutableStateOf(preview()) }
            TrailTheme {
                WorkoutImportDialog(state) { event ->
                    events += event
                    if (event is HealthEvent.SelectionChanged) state = state.copy(selectedIds = setOf(event.id))
                }
            }
        }
        compose.onNodeWithTag("import-selected").assertIsNotEnabled()
        compose.onNodeWithTag("workout-import-list").performScrollToNode(hasText("Workout 30"))
        compose.onNodeWithText("Workout 30").performClick()
        assertEquals(listOf(HealthEvent.SelectionChanged("external-30")), events)
        compose.onNodeWithTag("import-selected").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(HealthEvent.ImportConfirmed, events.last())
    }

    @Test fun leavingPreviewCancelsSelectionWithoutRequestingAnImport() {
        val events = mutableListOf<HealthEvent>()
        compose.setContent { TrailTheme { WorkoutImportDialog(preview(), { events += it }) } }
        compose.onNodeWithContentDescription("Close Import workouts").performClick()
        assertEquals(listOf(HealthEvent.PreviewDismissed), events)
    }

    @Test fun busyImportDisablesBothBackAndSubmit() {
        compose.setContent {
            TrailTheme { WorkoutImportDialog(preview().copy(isBusy = true, selectedIds = setOf("external-1")), {}) }
        }
        compose.onNodeWithContentDescription("Close Import workouts").assertIsNotEnabled()
        compose.onNodeWithTag("import-selected").assertIsNotEnabled()
    }

    @Test fun diagnosticsAreASecondaryExplicitAction() {
        val events = mutableListOf<SupportEvent>()
        compose.setContent {
            TrailTheme { SyncHistoryDialog(HealthSyncUiState(), SupportUiState(), {}, { events += it }, {}, {}, {}) }
        }
        assertEquals(emptyList<SupportEvent>(), events)
        compose.onNodeWithContentDescription("Sync history options").performClick()
        compose.onNodeWithText("Preview diagnostics").performClick()
        assertEquals(listOf(SupportEvent.PreviewRequested), events)
    }
}
