package dev.michelle.trails

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.michelle.trails.domain.ActivityType
import dev.michelle.trails.domain.GeoPoint
import dev.michelle.trails.domain.Effort
import dev.michelle.trails.domain.WalkDraft
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DraftRestoreTest {
    @Test
    fun bundleRoundTripRetainsTheCompleteEditorDraft() {
        val original = WalkDraft("existing-id", "Hill", "2026-01-02", "1.201", "40", "Notes", ActivityType.HIKE, "Local hill", Effort.MODERATE, GeoPoint(0.0, 0.0))
        val handle = SavedStateHandle()
        SavedStateDraftStore(handle).write(original)
        val baseline = original.copy(title = "Original title")
        SavedStateDraftStore(handle).writeBaseline(baseline)
        val bundle = checkNotNull(handle.get<Bundle>("walk-editor-draft"))
        val restoredHandle = SavedStateHandle(mapOf(
            "walk-editor-draft" to Bundle(bundle),
            "walk-editor-baseline" to Bundle(checkNotNull(handle.get<Bundle>("walk-editor-baseline"))),
        ))
        assertEquals(original, SavedStateDraftStore(restoredHandle).read())
        assertEquals(baseline, SavedStateDraftStore(restoredHandle).readBaseline())
    }
}
