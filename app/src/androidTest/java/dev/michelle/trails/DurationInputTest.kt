package dev.michelle.trails

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DurationInputTest {
    @get:Rule val compose = createComposeRule()

    @Test fun presetSubmitsCanonicalMinutes() {
        var chosen = ""
        compose.setContent { MaterialTheme { DurationInput("", true, null) { chosen = it } } }
        compose.onNodeWithText("30 min").performClick()
        assertEquals("30", chosen)
    }

    @Test fun customHoursAndMinutesDoNotRequireManualConversion() {
        var chosen = ""
        compose.setContent { MaterialTheme { DurationInput("", true, null) { chosen = it } } }
        compose.onNodeWithTag("field-DURATION").performClick()
        compose.onNodeWithTag("duration-hours").performTextInput("1")
        compose.onNodeWithTag("duration-minutes").performTextInput("30")
        compose.onNodeWithText("Set duration").performClick()
        assertEquals("90", chosen)
    }
}
