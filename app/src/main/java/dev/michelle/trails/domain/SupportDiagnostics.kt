package dev.michelle.trails.domain

import java.time.Instant
import kotlinx.coroutines.CancellationException

data class AppEnvironment(val versionName: String, val versionCode: Long, val androidApi: Int)
interface AppEnvironmentReader { fun read(): AppEnvironment }
interface DiagnosticsReader { suspend fun prepare(): String }

/** Explicit allowlist: never serialize the queue, exceptions, titles, IDs or journal values. */
class SupportDiagnostics(
    private val environment: AppEnvironmentReader,
    private val sync: HealthSyncEngine,
    private val now: () -> Instant = Instant::now,
) : DiagnosticsReader {
    override suspend fun prepare(): String {
        val app = environment.read()
        val report = try { sync.inspect() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        return buildString {
            appendLine("Trail Journal support diagnostics")
            appendLine("format_version: 1")
            appendLine("generated_utc: ${now()}")
            appendLine("app_version: ${app.versionName.replace(Regex("[\\r\\n\\t]"), " ").take(64)}")
            appendLine("app_version_code: ${app.versionCode}")
            appendLine("android_api: ${app.androidApi}")
            appendLine("sync_inspection: ${if (report == null) "unavailable" else "available"}")
            appendLine("health_connect: ${report?.access?.availability?.name ?: "UNKNOWN"}")
            appendLine("exercise_distance_read: ${permission(report?.access?.canRead)}")
            appendLine("exercise_distance_write: ${permission(report?.access?.canWrite)}")
            appendLine("automatic_sharing: ${report?.enabled?.let { if (it) "on" else "off" } ?: "unknown"}")
            appendLine("pending_work: ${report?.let { if (it.pending > 0) "yes" else "no" } ?: "unknown"}")
            appendLine("needs_permission: ${flag(report, SyncHistoryState.NEEDS_PERMISSION)}")
            appendLine("needs_start_time: ${flag(report, SyncHistoryState.NEEDS_TIME)}")
            appendLine("provider_retry_needed: ${flag(report, SyncHistoryState.FAILED)}")
            appendLine("user_attention_needed: ${flag(report, SyncHistoryState.BLOCKED)}")
            appendLine("undo_grace_pending: ${flag(report, SyncHistoryState.UNDO_GRACE)}")
            appendLine("raw_errors_included: no")
            appendLine("activity_details_included: no")
        }
    }

    private fun permission(value: Boolean?) = when (value) { true -> "allowed"; false -> "not_allowed"; null -> "unknown" }
    private fun flag(report: HealthSyncReport?, state: SyncHistoryState): String =
        report?.let { if (it.history.any { entry -> entry.state == state }) "yes" else "no" } ?: "unknown"
}
