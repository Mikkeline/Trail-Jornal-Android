package dev.michelle.trails

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.michelle.trails.domain.*
import dev.michelle.trails.presentation.JournalEvent
import dev.michelle.trails.presentation.JournalUiState
import java.time.YearMonth
import java.time.format.DateTimeFormatter

internal fun LazyListScope.journalHistoryItems(state: JournalUiState, onEvent: (JournalEvent) -> Unit,
    healthStatuses: Map<String, ActivityHealthStatus>, onShareHealth: (Walk) -> Unit,
    onRetryHealth: (String) -> Unit, onViewLocation: (GeoPoint) -> Unit) {
    item { JournalHistoryFilters(state, onEvent) }
    if (!state.isLoading && state.loadError == null && state.entries.isEmpty()) item {
        JournalEmptyState("A place for your progress", "Your saved activities will appear here, ready to revisit.", "Log your first activity", !state.isSaving) { onEvent(JournalEvent.NewEntryRequested) }
    } else if (!state.isLoading && state.loadError == null && state.filteredEntries.isEmpty()) item {
        JournalEmptyState("No matching activities", "Try another search or reset your filters to see more entries.", "Reset filters", !state.isSaving, showJournalHint = false) { onEvent(JournalEvent.FiltersCleared) }
    }
    state.filteredEntries.groupBy { YearMonth.from(it.date) }.forEach { (month, walks) ->
        item(key = "month-$month") {
            JournalSectionTitle("${month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))} · ${walks.size}")
        }
        items(walks, key = { "walk-${it.id}" }) { walk ->
            ActivityCard(walk, !state.isSaving, onEvent, onViewLocation, onShareHealth, healthStatuses[walk.id], onRetryHealth)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JournalHistoryFilters(state: JournalUiState, onEvent: (JournalEvent) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    var showPeriods by remember { mutableStateOf(false) }
    val hasFilters = state.historyFilter != HistoryFilter()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = state.historyFilter.query, onValueChange = { onEvent(JournalEvent.SearchChanged(it)) },
            modifier = Modifier.fillMaxWidth().testTag("history-search"), singleLine = true, enabled = !state.isSaving,
            placeholder = { Text("Search your activities") }, label = { Text("Search name, location, or notes") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = { if (state.historyFilter.query.isNotBlank()) IconButton(onClick = { onEvent(JournalEvent.SearchChanged("")) }) { Icon(Icons.Default.Close, "Clear search") } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            shape = MaterialTheme.shapes.medium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.historyFilter.type == null, enabled = !state.isSaving,
                onClick = { onEvent(JournalEvent.FilterTypeSelected(null)) }, label = { Text("All activities") })
            ActivityType.entries.forEach { type ->
                FilterChip(selected = state.historyFilter.type == type, enabled = !state.isSaving,
                    onClick = { onEvent(JournalEvent.FilterTypeSelected(type)) },
                    modifier = Modifier.semantics { contentDescription = "Filter by ${type.label}" },
                    label = { Text(when (type) { ActivityType.OUTDOOR_WALK -> "Walking"; ActivityType.HIKE -> "Hiking"; ActivityType.INDOOR_WALK -> "Indoor" }) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Box {
                OutlinedButton(enabled = !state.isSaving, onClick = { showPeriods = true }) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text(state.historyFilter.period.label)
                }
                DropdownMenu(expanded = showPeriods, onDismissRequest = { showPeriods = false }) {
                    HistoryPeriod.entries.forEach { period ->
                        DropdownMenuItem(text = { Text(period.label) }, onClick = { showPeriods = false; onEvent(JournalEvent.FilterPeriodSelected(period)) })
                    }
                }
            }
            if (hasFilters) TextButton(enabled = !state.isSaving, onClick = { onEvent(JournalEvent.FiltersCleared) }) { Text("Reset filters") }
        }
        Text("${state.filteredEntries.size} ${if (state.filteredEntries.size == 1) "activity" else "activities"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
