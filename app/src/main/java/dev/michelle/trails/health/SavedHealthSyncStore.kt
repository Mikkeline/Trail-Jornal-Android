package dev.michelle.trails.health

import android.content.Context
import com.google.gson.Gson
import dev.michelle.trails.domain.HealthSyncStore
import dev.michelle.trails.domain.HealthSyncTarget
import java.time.LocalTime
import java.time.ZoneId

/** Sync timing survives process death; journal rows and backup format remain compatible. */
class SavedHealthSyncStore(context: Context) : HealthSyncStore {
    private val preferences = context.applicationContext.getSharedPreferences("health-auto-sync", Context.MODE_PRIVATE)
    private val gson = Gson()

    override fun enabled(): Boolean = preferences.getBoolean("enabled", false)

    override fun setEnabled(enabled: Boolean) {
        check(preferences.edit().putBoolean("enabled", enabled).commit()) { "Could not save automatic update preference." }
    }

    override fun targets(): List<HealthSyncTarget> {
        val json = preferences.getString("targets", null) ?: return emptyList()
        val targets = requireNotNull(gson.fromJson(json, Array<HealthSyncTarget>::class.java)) { "Missing health sync settings." }.toList()
        targets.forEach {
            require(it.walkId.isNotBlank()) { "Invalid health sync activity." }
            LocalTime.parse(it.startTime)
            ZoneId.of(it.zoneId)
        }
        return targets
    }

    override fun save(target: HealthSyncTarget) {
        val updated = targets().filterNot { it.walkId == target.walkId } + target
        check(preferences.edit().putString("targets", gson.toJson(updated)).commit()) { "Could not save health sync status. Retry sharing." }
    }
}
