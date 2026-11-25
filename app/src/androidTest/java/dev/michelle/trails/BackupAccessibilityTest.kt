package dev.michelle.trails

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.michelle.trails.domain.*
import dev.michelle.trails.presentation.JournalEvent
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BackupAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backupReminderSwitchHasALabelAndDispatchesOneEvent() {
        val events = mutableListOf<JournalEvent>()
        compose.setContent {
            TrailTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BackupRecoveryCard(BackupOverview(), true, { events += it }, {}, {})
                }
            }
        }
        compose.onNodeWithText("Backup reminders").performScrollTo().assertIsOn().performClick()
        assertEquals(listOf(JournalEvent.BackupRemindersChanged(false)), events)
        compose.onNodeWithText("Keep your journal").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test fun recoveryGuideIsReachableAtLargeFontAndExplainsHealthConnectLimits() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                TrailTheme(dark = true) {
                    Column(Modifier.widthIn(max = 320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        BackupRecoveryCard(BackupOverview(reminderDue = true), true, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Recovery guide").performScrollTo().performClick()
        compose.onNodeWithText("Health Connect is not a full journal backup", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed().performClick()
    }

    @Test fun activityActionsHaveContextAndDurationErrorsAreExposedToAccessibility() {
        val walk = Walk("one", "Park", LocalDate.of(2026, 1, 2), 1000, 20, "")
        val events = mutableListOf<JournalEvent>()
        compose.setContent {
            TrailTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ActivityCard(walk, true, { events += it }, {})
                    DurationInput("", true, "Choose a duration.", {})
                }
            }
        }
        compose.onNodeWithContentDescription("More options for Park, 2026-01-02").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Delete Park, 2026-01-02").performClick()
        assertEquals(listOf(JournalEvent.DeleteRequested(walk)), events)
        compose.onNodeWithTag("field-DURATION").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Choose a duration."))
    }

    @Test fun shorterNotesStillOfferExpansionWhenLargeTextActuallyOverflows() {
        val walk = Walk("one", "Park", LocalDate.of(2026, 1, 2), 1000, 20,
            "A quiet walk near the park with a few short stops and some time beside the lake.")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                TrailTheme {
                    Column(Modifier.width(240.dp).verticalScroll(rememberScrollState())) {
                        ActivityCard(walk, true, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Read notes").performScrollTo().performClick()
        compose.onNodeWithText("Show less").performScrollTo().assertIsDisplayed()
    }
}
