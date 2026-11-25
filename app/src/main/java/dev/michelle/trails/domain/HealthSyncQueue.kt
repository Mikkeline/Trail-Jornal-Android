package dev.michelle.trails.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class HealthJobOperation { UPSERT, DELETE }
enum class HealthJobState { PENDING, FAILED, BLOCKED, SYNCED, DELETED }
data class HealthSyncJob(
    val walkId: String,
    val operation: HealthJobOperation,
    val revision: Long,
    val recordVersion: Long,
    val automatic: Boolean,
    val notBefore: Long,
    val state: HealthJobState = HealthJobState.PENDING,
    val attempts: Int = 0,
    val lastSyncedAt: Long? = null,
    val error: String? = null,
) {
    /** A failed removal has already passed its grace period; explicit Retry can bypass its backoff. */
    fun manualRetryNotBefore(): Long = if (operation == HealthJobOperation.DELETE && state == HealthJobState.PENDING) notBefore else 0
}
data class HealthQueueSnapshot(val enabled: Boolean, val walks: List<Walk>, val jobs: List<HealthSyncJob>)
enum class ActivityHealthState(val label: String) {
    NOT_SHARED("Not shared"), NEEDS_TIME("Needs start time"), PENDING("Pending"),
    PAUSED("Automatic updates off"), NEEDS_PERMISSION("Needs permission"), FAILED("Sync failed"), SYNCED("Synced to Health Connect"),
}
data class ActivityHealthStatus(val state: ActivityHealthState, val lastSyncedAt: Long? = null, val error: String? = null)

/** All mutation methods are local transactions. Acknowledgements must match the job revision. */
interface HealthSyncQueue {
    fun snapshot(): HealthQueueSnapshot
    fun setEnabled(enabled: Boolean)
    fun link(walkId: String, time: LocalTime, zone: ZoneId)
    fun retry(walkId: String?)
    fun complete(job: HealthSyncJob, at: Long)
    fun fail(job: HealthSyncJob, error: String, blocked: Boolean, retryAt: Long)
    fun changes(): Flow<Unit>
}

interface HealthSyncEngine : HealthActivitySharer {
    suspend fun setEnabled(enabled: Boolean)
    suspend fun inspect(): HealthSyncReport
    suspend fun synchronize(): HealthSyncReport
    suspend fun retry(walkId: String? = null) {}
    fun changes(): Flow<Unit> = emptyFlow()
}

/** Provider IPC is separated from atomic queue storage. Stale completions cannot erase newer edits. */
class QueuedHealthSync(
    private val queue: HealthSyncQueue,
    private val access: HealthAccessReader,
    private val health: JournalHealth,
    private val deleter: HealthWorkoutDeleter,
    private val now: () -> Instant = Instant::now,
) : HealthSyncEngine {
    private val mutex = Mutex()
    override fun changes() = queue.changes()

    override suspend fun setEnabled(enabled: Boolean) = mutex.withLock {
        if (enabled) check(access.access().canWrite) { "Allow exercise and distance sharing before enabling automatic updates." }
        queue.setEnabled(enabled)
    }

    override suspend fun retry(walkId: String?) = mutex.withLock { queue.retry(walkId) }

    override suspend fun share(walk: Walk, startTime: LocalTime, zone: ZoneId) {
        mutex.withLock {
            // Validate before recording the user's explicit share request.
            health.prepareShare(walk, startTime, zone)
            queue.link(walk.id, startTime, zone)
        }
        val report = synchronize()
        val result = report.activities[walk.id]
        check(result?.state == ActivityHealthState.SYNCED) { result?.error ?: "Activity saved; Health Connect sharing is pending. Check sync status and retry." }
    }

    override suspend fun inspect(): HealthSyncReport = mutex.withLock {
        buildReport(queue.snapshot(), access.access(), 0)
    }

    override suspend fun synchronize(): HealthSyncReport = mutex.withLock {
        val initial = queue.snapshot()
        val canWrite = access.access().canWrite
        var updated = 0
        if (canWrite) {
            for (candidate in initial.jobs) {
                // Fetch again after every IPC: an edit/Undo may have replaced this revision.
                val snapshot = queue.snapshot()
                val job = snapshot.jobs.find { it.walkId == candidate.walkId } ?: continue
                if (!eligible(job, snapshot.enabled) || job.notBefore > now().toEpochMilli()) continue
                try {
                    if (job.operation == HealthJobOperation.DELETE) {
                        deleter.delete(job.walkId)
                    } else {
                        val walk = requireNotNull(snapshot.walks.find { it.id == job.walkId }) { "Activity no longer exists." }
                        val time = requireNotNull(walk.startTime) { "Choose a start time in Edit activity." }
                        val zone = ZoneId.of(requireNotNull(walk.zoneId) { "Choose a time zone in Edit activity." })
                        health.share(health.prepareShare(walk, time, zone).copy(recordVersion = job.recordVersion))
                    }
                    queue.complete(job, now().toEpochMilli())
                    updated++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    val blocked = error is IllegalArgumentException || error is SecurityException
                    queue.fail(job, error.message ?: "Could not update Health Connect.", blocked,
                        now().toEpochMilli() + retryDelay(job.attempts))
                }
            }
        }
        buildReport(queue.snapshot(), access.access(), updated)
    }

    private fun buildReport(snapshot: HealthQueueSnapshot, currentAccess: HealthAccess, updated: Int): HealthSyncReport {
        val canWrite = currentAccess.canWrite
        val pending = snapshot.jobs.filter { it.state !in setOf(HealthJobState.SYNCED, HealthJobState.DELETED) }
        val jobsById = snapshot.jobs.associateBy { it.walkId }
        val activities = snapshot.walks.filterNot { HealthRecordIdentity.imported(it.id) }.associate { walk ->
            val job = jobsById[walk.id]
            val status = when {
                walk.startTime == null -> ActivityHealthState.NEEDS_TIME
                job == null -> ActivityHealthState.NOT_SHARED
                job.state == HealthJobState.SYNCED -> ActivityHealthState.SYNCED
                job.automatic && !snapshot.enabled -> ActivityHealthState.PAUSED
                !canWrite -> ActivityHealthState.NEEDS_PERMISSION
                job.state in setOf(HealthJobState.FAILED, HealthJobState.BLOCKED) -> ActivityHealthState.FAILED
                else -> ActivityHealthState.PENDING
            }
            walk.id to ActivityHealthStatus(status, job?.lastSyncedAt, job?.error)
        }
        val needsTime = snapshot.walks.filter { !HealthRecordIdentity.imported(it.id) && it.startTime == null }.map { it.id }.toSet()
        val retryable = pending.filter { eligible(it, snapshot.enabled) }
        return HealthSyncReport(snapshot.enabled, activities.values.count { it.state != ActivityHealthState.NEEDS_TIME && it.state != ActivityHealthState.NOT_SHARED },
            needsTime.size, updated, pending.size,
            error = if (!canWrite && pending.isNotEmpty()) "Health Connect updates paused. Allow exercise and distance sharing." else pending.firstOrNull { it.error != null }?.error,
            needsStartTimeIds = needsTime, activities = activities,
            retryNeeded = canWrite && retryable.isNotEmpty(),
            history = HealthSyncHistory.project(snapshot, currentAccess, now().toEpochMilli()), access = currentAccess,
        )
    }

    private fun eligible(job: HealthSyncJob, enabled: Boolean): Boolean =
        job.state in setOf(HealthJobState.PENDING, HealthJobState.FAILED) && (!job.automatic || enabled)

    companion object {
        fun retryDelay(attempts: Int): Long = minOf(21_600_000L, 30_000L * (1L shl attempts.coerceIn(0, 10)))
    }
}
