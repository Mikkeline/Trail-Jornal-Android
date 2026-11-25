package dev.michelle.trails.data

import dev.michelle.trails.domain.Walk
import dev.michelle.trails.domain.WalkImporter
import dev.michelle.trails.domain.ImportReport
import dev.michelle.trails.domain.WalkFinder
import dev.michelle.trails.domain.WalkObserver
import dev.michelle.trails.domain.WalkReader
import dev.michelle.trails.domain.WalkUpdater
import dev.michelle.trails.domain.WalkWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Synchronous methods run on the caller's IO dispatcher. Room owns observable queries. */
class RoomWalkRepository(private val dao: WalkDao, private val outbox: RoomHealthSyncQueue? = null) : WalkReader, WalkWriter, WalkFinder, WalkUpdater, WalkObserver, WalkImporter {
    override fun all(): List<Walk> = dao.all().map { it.toDomain() }

    override fun find(id: String): Walk? = dao.find(id)?.toDomain()

    override fun observe(): Flow<List<Walk>> {
        return dao.observe().map { entries -> entries.map { it.toDomain() } }
    }

    override fun save(walk: Walk) {
        if (outbox == null) dao.insert(walk.toEntity()) else outbox.commit(walk.id) { dao.insert(walk.toEntity()) }
    }

    override fun update(walk: Walk): Boolean = if (outbox == null) dao.update(walk.toEntity()) == 1
        else outbox.commit(walk.id) { dao.update(walk.toEntity()) == 1 }

    override fun delete(id: String) {
        if (outbox == null) dao.delete(id) else outbox.commit(id) { dao.delete(id) }
    }

    override fun mergeMissing(entries: List<Walk>): ImportReport {
        val entities = entries.map { it.toEntity() }
        val added = outbox?.merge(entities) ?: dao.mergeMissing(entities)
        return ImportReport(added, entries.size - added)
    }
}
