package dev.michelle.trails.domain

object BackupLimits {
    const val MAX_BYTES = 10 * 1024 * 1024
    const val MAX_ENTRIES = 10_000
}

interface BackupCodec {
    fun encode(entries: List<Walk>): String
    fun decode(contents: String): List<Walk>
}

interface WalkImporter {
    /** Merge all missing IDs atomically. Preserve existing IDs without replacement. */
    fun mergeMissing(entries: List<Walk>): ImportReport
}

data class ImportReport(val added: Int, val skipped: Int)
data class ImportPlan(val entries: List<Walk>, val additions: Int, val skipped: Int)

class JournalBackups(
    private val reader: WalkReader,
    private val importer: WalkImporter,
    private val codec: BackupCodec,
) {
    fun export(): String = prepareExport().contents

    fun prepareExport(): PreparedBackup {
        val entries = reader.all()
        val result = codec.encode(entries)
        require(result.toByteArray(Charsets.UTF_8).size <= BackupLimits.MAX_BYTES) { "Backup exceeds the 10 MB limit." }
        return PreparedBackup(result, BackupFingerprint.of(entries), entries.size)
    }

    fun preview(contents: String): ImportPlan {
        require(contents.toByteArray(Charsets.UTF_8).size <= BackupLimits.MAX_BYTES) { "Backup exceeds the 10 MB limit." }
        val entries = codec.decode(contents)
        require(entries.size <= BackupLimits.MAX_ENTRIES) { "Backup has more than 10,000 entries." }
        require(entries.map { it.id }.distinct().size == entries.size) { "Backup contains duplicate activity IDs." }
        val existingIds = reader.all().mapTo(mutableSetOf()) { it.id }
        val additions = entries.count { it.id !in existingIds }
        return ImportPlan(entries, additions, entries.size - additions)
    }

    fun restore(plan: ImportPlan): ImportReport = importer.mergeMissing(plan.entries)
}
