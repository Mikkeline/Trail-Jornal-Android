package dev.michelle.trails

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.michelle.trails.domain.*
import dev.michelle.trails.presentation.JournalEvent
import dev.michelle.trails.presentation.JournalUiState
import dev.michelle.trails.presentation.JournalScreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

internal fun LazyListScope.journalHomeItems(state: JournalUiState, onEvent: (JournalEvent) -> Unit,
    onExport: () -> Unit, onOpenTools: () -> Unit, onOpenSyncHistory: () -> Unit,
    healthStatuses: Map<String, ActivityHealthStatus>, onShareHealth: (Walk) -> Unit,
    onRetryHealth: (String) -> Unit, onViewLocation: (GeoPoint) -> Unit) {
    if (!state.isLoading && state.entries.isEmpty() && state.loadError == null) {
        item { JournalEmptyState("Make your first entry", "Keep a record of your walks, hikes and everyday movement.", "Log your first activity", !state.isSaving) { onEvent(JournalEvent.NewEntryRequested) } }
    } else if (state.entries.isNotEmpty()) {
        item { WeekOverview(state.weekSummary) }
        item { JourneyOverview(state.summary) }
    }
    if (state.backup.reminderDue && state.loadError == null) item {
        JournalMessage("Keep your latest entries safe", "Your latest changes need a backup.") {
            BackupReminderActions(!state.isSaving && !state.isLoading, onExport, { onEvent(JournalEvent.BackupSnoozed) })
        }
    }
    if (state.entries.isNotEmpty()) {
        item { JournalSectionTitle("Recent activities", "View all", { onEvent(JournalEvent.NavigateRequested(JournalScreen.HISTORY)) }, !state.isSaving) }
        items(state.entries.take(3), key = { "recent-${it.id}" }) { walk ->
            ActivityCard(walk, !state.isSaving, onEvent, onViewLocation, onShareHealth, healthStatuses[walk.id], onRetryHealth)
        }
    }
    item {
        JournalToolRow("Health & sync", if (healthStatuses.values.any { it.state in setOf(ActivityHealthState.FAILED, ActivityHealthState.NEEDS_PERMISSION) })
            "Some shared activities need attention" else "See shared activities, pending updates and removals", !state.isSaving, onOpenSyncHistory)
    }
    item { JournalToolRow("Your data, your choice", "Backups, recovery and connected health apps", !state.isSaving, onOpenTools) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackupReminderActions(enabled: Boolean, onExport: () -> Unit, onSnooze: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = enabled, onClick = onExport) { Text("Back up now") }
        TextButton(enabled = enabled, onClick = onSnooze) { Text("Remind me later") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekOverview(summary: Summary) {
    val colors = MaterialTheme.colorScheme
    val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val date = DateTimeFormatter.ofPattern("d MMM")
    Surface(shape = MaterialTheme.shapes.large, color = colors.primaryContainer) {
        Box {
            Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
                val w = size.width; val h = size.height
                val ridge = Path().apply { moveTo(w * .55f, h); lineTo(w * .75f, h * .16f); lineTo(w * .92f, h * .7f); lineTo(w * 1.1f, h * .3f) }
                drawPath(ridge, colors.onPrimaryContainer.copy(alpha = .07f), style = Stroke(2.dp.toPx()))
                drawCircle(colors.onPrimaryContainer.copy(alpha = .04f), w * .22f, Offset(w * .87f, h * .48f), style = Stroke(1.dp.toPx()))
            }
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("THIS WEEK", style = MaterialTheme.typography.labelSmall)
                    Text("${monday.format(date)} – ${monday.plusDays(6).format(date)}", style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Text("${formatKm(summary.meters)} km", style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.semantics { contentDescription = "This week's distance: ${formatKm(summary.meters)} kilometres" })
                    Text("Every little bit adds up.", style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider(color = colors.onPrimaryContainer.copy(alpha = .12f))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OverviewMetric(ActivityDuration.format(summary.minutes), "Time logged")
                    OverviewMetric(summary.count.toString(), if (summary.count == 1) "Activity logged" else "Activities logged")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JourneyOverview(summary: Summary) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("YOUR JOURNEY SO FAR", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OverviewMetric("${formatKm(summary.meters)} km", "Total distance")
            OverviewMetric(ActivityDuration.format(summary.minutes), "Total time")
            OverviewMetric(summary.count.toString(), "Activities")
        }
    }
}

@Composable
private fun OverviewMetric(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun JournalEmptyState(title: String, description: String, action: String, enabled: Boolean, showJournalHint: Boolean = true, onAction: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ActivityGlyph(ActivityType.HIKE, Modifier.size(56.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = enabled, onClick = onAction, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                Icon(if (showJournalHint) Icons.Default.Add else Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text(action)
            }
            if (showJournalHint) Text("Your journal works offline. Health sharing is optional.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
