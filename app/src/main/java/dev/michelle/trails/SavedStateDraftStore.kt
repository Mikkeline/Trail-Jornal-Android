package dev.michelle.trails

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import dev.michelle.trails.domain.ActivityType
import dev.michelle.trails.domain.Effort
import dev.michelle.trails.domain.GeoPoint
import dev.michelle.trails.domain.WalkDraft
import dev.michelle.trails.presentation.DraftStore

/** Retain both draft and baseline so process restoration does not lose the dirty-state guard. */
internal class SavedStateDraftStore(private val handle: SavedStateHandle) : DraftStore {
    override fun read(): WalkDraft? = readBundle("walk-editor-draft")
    override fun write(draft: WalkDraft) = writeBundle("walk-editor-draft", draft)
    override fun readBaseline(): WalkDraft? = readBundle("walk-editor-baseline")
    override fun writeBaseline(draft: WalkDraft) = writeBundle("walk-editor-baseline", draft)

    private fun readBundle(key: String): WalkDraft? {
        val bundle = handle.get<Bundle>(key) ?: return null
        return WalkDraft(
            id = bundle.getString("id"),
            title = bundle.getString("title").orEmpty(),
            date = bundle.getString("date").orEmpty(),
            distanceKm = bundle.getString("distanceKm").orEmpty(),
            durationMinutes = bundle.getString("durationMinutes").orEmpty(),
            notes = bundle.getString("notes").orEmpty(),
            startTime = bundle.getString("startTime").orEmpty(),
            zoneId = bundle.getString("zoneId").orEmpty(),
            type = ActivityType.entries.firstOrNull { it.name == bundle.getString("type") } ?: ActivityType.OUTDOOR_WALK,
            location = bundle.getString("location").orEmpty(),
            effort = Effort.entries.firstOrNull { it.name == bundle.getString("effort") },
            point = GeoPoint.fromNullable(
                bundle.takeIf { it.containsKey("latitude") }?.getDouble("latitude"),
                bundle.takeIf { it.containsKey("longitude") }?.getDouble("longitude"),
            ),
        )
    }

    private fun writeBundle(key: String, draft: WalkDraft) {
        handle[key] = Bundle().apply {
            putString("id", draft.id)
            putString("title", draft.title)
            putString("date", draft.date)
            putString("distanceKm", draft.distanceKm)
            putString("durationMinutes", draft.durationMinutes)
            putString("notes", draft.notes)
            putString("startTime", draft.startTime)
            putString("zoneId", draft.zoneId)
            putString("type", draft.type.name)
            putString("location", draft.location)
            putString("effort", draft.effort?.name)
            draft.point?.let { point ->
                putDouble("latitude", point.latitude)
                putDouble("longitude", point.longitude)
            }
        }
    }
}
