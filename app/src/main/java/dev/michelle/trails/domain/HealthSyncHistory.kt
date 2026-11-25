package dev.michelle.trails.domain

enum class SyncHistoryState(val label: String) {
    PENDING("Pending share or update"), REMOVAL_PENDING("Removal pending"),
    UNDO_GRACE("Waiting for Undo grace period"), NEEDS_TIME("Needs a start time"),
    NEEDS_PERMISSION("Needs permission"), UNAVAILABLE("Health Connect unavailable"),
    PAUSED("Automatic sharing off"), FAILED("Retry needed"), BLOCKED("Needs attention"),
    SYNCED("Synced to Health Connect"), REMOVED("Removed from Health Connect"),
}

data class SyncHistoryEntry(
    val walkId: String,
    val title: String?,
    val operation: HealthJobOperation,
    val state: SyncHistoryState,
    val lastSyncedAt: Long?,
    val retryAt: Long?,
    val failedAttempts: Int,
    val error: String?,
    val canRetry: Boolean,
) {
    val complete: Boolean get() = state == SyncHistoryState.SYNCED || state == SyncHistoryState.REMOVED
}

enum class SyncHistoryFilter(val label: String) {
    ALL("All"), PENDING("Pending & attention"), COMPLETED("Completed");
    fun matches(entry: SyncHistoryEntry): Boolean = when (this) {
        ALL -> true
        PENDING -> !entry.complete
        COMPLETED -> entry.complete
    }
}

/** A projection of the latest desired operation for each activity, including retained deletion jobs. */
object HealthSyncHistory {
    fun project(snapshot: HealthQueueSnapshot, access: HealthAccess, now: Long): List<SyncHistoryEntry> {
        val walks = snapshot.walks.associateBy { it.id }
        return snapshot.jobs.filterNot { HealthRecordIdentity.imported(it.walkId) }.map { job ->
            val walk = walks[job.walkId]
            val state = when {
                job.state == HealthJobState.DELETED -> SyncHistoryState.REMOVED
                job.state == HealthJobState.SYNCED -> SyncHistoryState.SYNCED
                job.automatic && !snapshot.enabled -> SyncHistoryState.PAUSED
                access.availability != HealthAvailability.AVAILABLE -> SyncHistoryState.UNAVAILABLE
                !access.canWrite -> SyncHistoryState.NEEDS_PERMISSION
                job.operation == HealthJobOperation.UPSERT && walk?.startTime == null -> SyncHistoryState.NEEDS_TIME
                job.state == HealthJobState.BLOCKED -> SyncHistoryState.BLOCKED
                job.state == HealthJobState.FAILED -> SyncHistoryState.FAILED
                job.operation == HealthJobOperation.DELETE && job.notBefore > now -> SyncHistoryState.UNDO_GRACE
                job.operation == HealthJobOperation.DELETE -> SyncHistoryState.REMOVAL_PENDING
                else -> SyncHistoryState.PENDING
            }
            val canRetry = state in setOf(SyncHistoryState.FAILED, SyncHistoryState.PENDING,
                SyncHistoryState.REMOVAL_PENDING, SyncHistoryState.PAUSED, SyncHistoryState.BLOCKED) && access.canWrite
            SyncHistoryEntry(job.walkId, walk?.title, job.operation, state, job.lastSyncedAt,
                job.notBefore.takeIf { it > now && state in setOf(SyncHistoryState.FAILED, SyncHistoryState.UNDO_GRACE) },
                job.attempts, job.error, canRetry)
        }.sortedWith(compareBy<SyncHistoryEntry> { it.complete }.thenByDescending { it.lastSyncedAt ?: Long.MIN_VALUE }.thenBy { it.walkId })
    }
}
