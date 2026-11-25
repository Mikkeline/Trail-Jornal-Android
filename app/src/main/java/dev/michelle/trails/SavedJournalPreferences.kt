package dev.michelle.trails

import android.content.Context
import dev.michelle.trails.domain.ActivityType
import dev.michelle.trails.presentation.JournalPreferences

class SavedJournalPreferences(context: Context) : JournalPreferences {
    private val preferences = context.applicationContext.getSharedPreferences("journal-preferences", Context.MODE_PRIVATE)

    override fun lastActivityType(): ActivityType {
        val name = preferences.getString("last-activity-type", null)
        return ActivityType.entries.firstOrNull { it.name == name } ?: ActivityType.OUTDOOR_WALK
    }

    override fun rememberActivityType(type: ActivityType) {
        preferences.edit().putString("last-activity-type", type.name).apply()
    }
}
