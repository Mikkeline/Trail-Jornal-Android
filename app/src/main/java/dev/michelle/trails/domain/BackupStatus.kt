package dev.michelle.trails.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

data class PreparedBackup(val contents: String, val fingerprint: String, val entries: Int)
data class BackupReceipt(val createdAt: Instant, val fingerprint: String, val entries: Int)
data class BackupPreferences(
    val receipt: BackupReceipt? = null,
    val remindersEnabled: Boolean = true,
    val snoozedUntil: Instant? = null,
)
interface BackupStatusStore {
    fun read(): BackupPreferences
    fun write(preferences: BackupPreferences)
}
class MemoryBackupStatusStore : BackupStatusStore {
    private var preferences = BackupPreferences()
    override fun read() = preferences
    override fun write(preferences: BackupPreferences) { this.preferences = preferences }
}
data class BackupOverview(
    val receipt: BackupReceipt? = null,
    val remindersEnabled: Boolean = true,
    val snoozedUntil: Instant? = null,
    val hasUnbackedChanges: Boolean = false,
    val reminderDue: Boolean = false,
    val error: String? = null,
)

/** In-app reminders only. A receipt records a completed export, not continued access to its file. */
class BackupMonitor(private val store: BackupStatusStore, private val now: () -> Instant = Instant::now) {
    fun overview(entries: List<Walk>): BackupOverview {
        val preferences = store.read()
        val receipt = preferences.receipt
        val changed = receipt?.fingerprint != BackupFingerprint.of(entries) && (entries.isNotEmpty() || receipt != null)
        val current = now()
        val oldEnough = receipt == null || !current.isBefore(receipt.createdAt.plus(REMINDER_INTERVAL))
        val snoozed = preferences.snoozedUntil?.isAfter(current) == true
        return BackupOverview(receipt, preferences.remindersEnabled, preferences.snoozedUntil, changed,
            preferences.remindersEnabled && changed && oldEnough && !snoozed)
    }

    fun recorded(backup: PreparedBackup) {
        store.write(store.read().copy(receipt = BackupReceipt(now(), backup.fingerprint, backup.entries), snoozedUntil = null))
    }
    fun reminders(enabled: Boolean) = store.write(store.read().copy(remindersEnabled = enabled))
    fun snooze() = store.write(store.read().copy(snoozedUntil = now().plus(REMINDER_INTERVAL)))

    companion object { val REMINDER_INTERVAL: Duration = Duration.ofDays(7) }
}

/** Content-based freshness includes private journal fields and is independent of display order. */
object BackupFingerprint {
    fun of(entries: List<Walk>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun field(value: String?) {
            val bytes = value?.toByteArray(StandardCharsets.UTF_8)
            digest.update("${bytes?.size ?: -1}:".toByteArray(StandardCharsets.UTF_8))
            bytes?.let(digest::update)
        }
        entries.sortedBy { it.id }.forEach { walk ->
            listOf(walk.id, walk.title, walk.date.toString(), walk.distanceMeters.toString(), walk.durationMinutes.toString(),
                walk.notes, walk.type.name, walk.location, walk.effort?.name, walk.point?.latitude?.toString(),
                walk.point?.longitude?.toString(), walk.startTime?.toString(), walk.zoneId).forEach(::field)
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
