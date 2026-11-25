package dev.michelle.trails.data

import dev.michelle.trails.domain.*
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.Callable
import kotlinx.coroutines.flow.combine

/** Same Room transaction stores the activity mutation and the latest desired remote operation. */
class RoomHealthSyncQueue(
    private val database: TrailDatabase,
    private val legacy: HealthSyncStore,
    private val schedule: () -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
    private val versionFloor: (String) -> Long = { 0 },
) : HealthSyncQueue {
    private val walks get() = database.walkDao()
    private val jobs get() = database.healthSyncDao()

    fun initialize() = transaction {
        if (jobs.settings() == null) {
            jobs.configure(HealthSyncSettingsEntity(enabled = legacy.enabled()))
            for (target in legacy.targets()) {
                val walk = walks.find(target.walkId)?.toDomain() ?: continue
                if (HealthRecordIdentity.imported(walk.id)) continue
                val updated = walk.copy(startTime = walk.startTime ?: LocalTime.parse(target.startTime),
                    zoneId = walk.zoneId ?: target.zoneId)
                walks.update(updated.toEntity())
                enqueue(updated.id, "UPSERT", automatic = true)
            }
        }
    }

    fun <T> commit(id: String, mutation: () -> T): T {
        initialize()
        val result = transaction {
            val before = walks.find(id)?.toDomain()
            val result = mutation()
            changed(id, before)
            result
        }
        kick()
        return result
    }

    fun merge(entries: List<WalkEntity>): Int {
        initialize()
        val added = transaction {
            entries.count { entry ->
                val inserted = walks.insertIfMissing(entry) != -1L
                if (inserted) changed(entry.id, null)
                inserted
            }
        }
        kick()
        return added
    }

    private fun changed(id: String, before: Walk?) {
        if (HealthRecordIdentity.imported(id)) return
        val walk = walks.find(id)?.toDomain()
        val job = jobs.find(id)
        if (walk == null) {
            // Only owned, previously requested exports have a remote copy to remove.
            if (job != null && job.state != "DELETED") enqueue(id, "DELETE", automatic = false, due = now() + DELETE_GRACE_MS)
        } else if (job?.operation == "DELETE") {
            // Undo supersedes a pending delete; if IPC already ran, upsert repairs the remote copy.
            enqueue(id, "UPSERT", automatic = false)
        } else if (exportChanged(before, walk) && (job != null || (jobs.settings()?.enabled == true && walk.startTime != null))) {
            enqueue(id, "UPSERT", automatic = job?.let { it.state == "SYNCED" || it.automatic } ?: true)
        }
    }

    override fun snapshot(): HealthQueueSnapshot {
        initialize()
        return transaction { HealthQueueSnapshot(jobs.settings()?.enabled == true, walks.all().map { it.toDomain() }, jobs.all().map { it.toDomain() }) }
    }

    override fun setEnabled(enabled: Boolean) {
        initialize()
        transaction {
            jobs.configure(HealthSyncSettingsEntity(enabled = enabled))
            if (enabled) walks.all().filter { it.startTime != null && !HealthRecordIdentity.imported(it.id) && jobs.find(it.id) == null }
                .forEach { enqueue(it.id, "UPSERT", automatic = true) }
        }
        kick()
    }

    override fun link(walkId: String, time: LocalTime, zone: ZoneId) {
        initialize()
        transaction {
            require(!HealthRecordIdentity.imported(walkId)) { "Imported workouts cannot be copied back." }
            val walk = requireNotNull(walks.find(walkId)) { "This activity was deleted. Refresh your journal." }.toDomain()
            walks.update(walk.copy(startTime = time, zoneId = zone.id).toEntity())
            enqueue(walkId, "UPSERT", automatic = false)
        }
        kick()
    }

    override fun retry(walkId: String?) {
        initialize()
        transaction {
            jobs.all().filter { (walkId == null || it.walkId == walkId) && it.state in setOf("FAILED", "BLOCKED", "PENDING") }.forEach {
                // Retry never shortens the deletion grace period. Per-entry Retry is an explicit operation request.
                jobs.put(it.copy(state = "PENDING", error = null, attempts = 0,
                    notBefore = it.toDomain().manualRetryNotBefore(),
                    automatic = if (walkId != null) false else it.automatic))
            }
        }
        kick()
    }

    override fun complete(job: HealthSyncJob, at: Long) = jobs.complete(job.walkId, job.revision,
        if (job.operation == HealthJobOperation.DELETE) "DELETED" else "SYNCED", at)

    override fun fail(job: HealthSyncJob, error: String, blocked: Boolean, retryAt: Long) = jobs.fail(job.walkId, job.revision,
        if (blocked) "BLOCKED" else "FAILED", error.take(500), retryAt)

    override fun changes() = combine(jobs.observe(), jobs.observeSettings()) { _, _ -> Unit }

    private fun enqueue(id: String, operation: String, automatic: Boolean, due: Long = 0) {
        val previous = jobs.find(id)
        jobs.put(HealthSyncEntity(id, operation, (previous?.revision ?: 0) + 1,
            maxOf(now(), (previous?.recordVersion ?: 0) + 1, versionFloor(id) + 1), automatic, due, "PENDING", 0, previous?.lastSyncedAt, null))
    }

    private fun exportChanged(a: Walk?, b: Walk) = a == null ||
        listOf(a.title, a.date, a.distanceMeters, a.durationMinutes, a.type, a.startTime, a.zoneId) !=
        listOf(b.title, b.date, b.distanceMeters, b.durationMinutes, b.type, b.startTime, b.zoneId)

    private fun <T> transaction(block: () -> T): T = database.runInTransaction(Callable { block() })
    private fun kick() { runCatching { schedule() } } // Scheduling failure must not turn a committed save into a duplicate-save retry.

    companion object { const val DELETE_GRACE_MS = 30_000L }
}
