package dev.michelle.trails.domain

/** Legacy preference records are read once into the Room queue on upgrade. */
data class HealthSyncTarget(
    val walkId: String,
    val startTime: String,
    val zoneId: String,
    val lastSignature: String? = null,
)

interface HealthSyncStore {
    fun enabled(): Boolean
    fun setEnabled(enabled: Boolean)
    fun targets(): List<HealthSyncTarget>
    fun save(target: HealthSyncTarget)
}

data class HealthSyncReport(
    val enabled: Boolean,
    val linked: Int,
    val needsStartTime: Int,
    val updated: Int = 0,
    val pending: Int = 0,
    val error: String? = null,
    val needsStartTimeIds: Set<String> = emptySet(),
    val activities: Map<String, ActivityHealthStatus> = emptyMap(),
    val retryNeeded: Boolean = false,
    val history: List<SyncHistoryEntry> = emptyList(),
    val access: HealthAccess? = null,
)
