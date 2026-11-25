package dev.michelle.trails

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.tooling.preview.Preview
import dev.michelle.trails.domain.*
import dev.michelle.trails.presentation.*
import dev.michelle.trails.presentation.JournalScreen as Destination
import java.time.LocalDate
import java.time.LocalTime

/** Synthetic fixtures for Android Studio; excluded from release builds. */
private fun previewJournal(screen: Destination = Destination.HOME): JournalUiState {
    val today = LocalDate.now()
    val walks = listOf(
        Walk("preview-park", "Evening around the park", today, 4200, 52, "A quiet loop before sunset.", location = "Neighbourhood park", startTime = LocalTime.of(18, 0), zoneId = "Asia/Kuala_Lumpur"),
        Walk("preview-indoor", "A little walking break", today.minusDays(1), 1800, 25, "", ActivityType.INDOOR_WALK),
        Walk("preview-hike", "A morning on the hill", today.minusDays(8), 7100, 145, "A slower climb, with time for the view.", ActivityType.HIKE, "Local trail", Effort.MODERATE),
    )
    val sorted = FilterHistory().execute(walks, HistoryFilter(), today)
    return JournalUiState(screen = screen, entries = sorted, filteredEntries = sorted,
        summary = SummarizeWalks().execute(walks), weekSummary = SummarizeWalks().execute(FilterHistory().execute(walks, HistoryFilter(period = HistoryPeriod.THIS_WEEK), today)),
        draft = WalkDraft(title = "Morning loop", date = today.toString(), distanceKm = "3.2", durationMinutes = "45"), isLoading = false)
}

@Preview(name = "Home • Light", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun HomeLightPreview() { TrailTheme(dark = false) { JournalScreen(previewJournal(), onEvent = {}) } }

@Preview(name = "Home • Dark", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun HomeDarkPreview() { TrailTheme(dark = true) { JournalScreen(previewJournal(), onEvent = {}) } }

@Preview(name = "Journal", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun HistoryPreview() { TrailTheme(dark = false) { JournalScreen(previewJournal(Destination.HISTORY), onEvent = {}) } }

@Preview(name = "Entry form", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun EditorPreview() { TrailTheme(dark = false) { JournalScreen(previewJournal(Destination.EDITOR), onEvent = {}) } }

@Preview(name = "First use", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun EmptyPreview() { TrailTheme(dark = false) { JournalScreen(JournalUiState(isLoading = false), onEvent = {}) } }

@Preview(name = "Large text", widthDp = 360, heightDp = 800, fontScale = 2f, showBackground = true)
@Composable private fun LargeTextPreview() { TrailTheme(dark = false) { JournalScreen(previewJournal(), onEvent = {}) } }


@Preview(name = "Health apps", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun HealthAppsPreview() {
    TrailTheme(dark = false) {
        HealthConnectionsDialog(HealthUiState(access = HealthAccess(HealthAvailability.AVAILABLE, canRead = true, canWrite = true)),
            {}, {}, {}, {}, HealthSyncUiState(), {}, emptyList(), {}, {})
    }
}

private fun previewImports(): HealthUiState {
    val start = java.time.Instant.parse("2026-01-02T08:00:00Z")
    val candidates = (1..12).map { index ->
        HealthCandidate(Walk("preview-import-$index", "Park walk $index", LocalDate.of(2026, 1, 2), 3200, 45, ""),
            "Example watch companion", start, start.plusSeconds(2700))
    }
    return HealthUiState(preview = HealthImportPlan(candidates, 2, 1, false), selectedIds = setOf(candidates.first().walk.id))
}

@Preview(name = "Workout selection", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun WorkoutSelectionPreview() { TrailTheme(dark = false) { WorkoutImportDialog(previewImports(), {}) } }

@Preview(name = "Import • Large text", widthDp = 360, heightDp = 800, fontScale = 2f, showBackground = true)
@Composable private fun ImportLargeTextPreview() { TrailTheme(dark = true) { WorkoutImportDialog(previewImports(), {}) } }

@Preview(name = "Sync history", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun SyncHistoryPreview() {
    val entry = SyncHistoryEntry("preview-only", "Evening loop", HealthJobOperation.UPSERT, SyncHistoryState.FAILED,
        null, null, 1, "Health Connect is temporarily unavailable. Retry when it is ready.", true)
    TrailTheme(dark = true) {
        SyncHistoryDialog(HealthSyncUiState(HealthSyncReport(true, 1, 0, history = listOf(entry))), SupportUiState(), {}, {}, {}, {}, {})
    }
}

@Preview(name = "Backup tools", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun BackupToolsPreview() {
    TrailTheme(dark = false) {
        JournalDetailPage("Your data", {}) { padding ->
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(padding)) {
                BackupRecoveryCard(BackupOverview(reminderDue = true), true, {}, {}, {})
            }
        }
    }
}

@Preview(name = "Diagnostic report", widthDp = 390, heightDp = 844, showBackground = true)
@Composable private fun DiagnosticPreview() {
    TrailTheme(dark = false) {
        SupportDiagnosticsDialog(SupportUiState(preview = "format_version: 1\nraw_errors_included: no\n"), {}, {})
    }
}
