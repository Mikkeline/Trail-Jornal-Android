package dev.michelle.trails

import android.content.Context
import dev.michelle.trails.domain.BackupPreferences
import dev.michelle.trails.domain.BackupReceipt
import dev.michelle.trails.domain.BackupStatusStore
import java.time.Instant

class SavedBackupStatusStore(context: Context) : BackupStatusStore {
    private val preferences = context.applicationContext.getSharedPreferences("backup-status", Context.MODE_PRIVATE)

    override fun read(): BackupPreferences {
        val fingerprint = preferences.getString("fingerprint", null)
        val receipt = fingerprint?.takeIf { preferences.contains("created-at") && preferences.contains("entries") && preferences.getInt("entries", -1) >= 0 }?.let {
            BackupReceipt(Instant.ofEpochMilli(preferences.getLong("created-at", 0)), it, preferences.getInt("entries", 0))
        }
        return BackupPreferences(receipt, preferences.getBoolean("reminders", true),
            if (preferences.contains("snoozed-until")) Instant.ofEpochMilli(preferences.getLong("snoozed-until", 0)) else null)
    }

    override fun write(preferences: BackupPreferences) {
        val edit = this.preferences.edit().clear().putBoolean("reminders", preferences.remindersEnabled)
        preferences.receipt?.let {
            edit.putString("fingerprint", it.fingerprint).putLong("created-at", it.createdAt.toEpochMilli()).putInt("entries", it.entries)
        }
        preferences.snoozedUntil?.let { edit.putLong("snoozed-until", it.toEpochMilli()) }
        check(edit.commit()) { "Could not save backup status. Your selected backup file is unchanged." }
    }
}
