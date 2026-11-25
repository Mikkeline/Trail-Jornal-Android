package dev.michelle.trails

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.michelle.trails.domain.*
import dev.michelle.trails.presentation.JournalEvent
import dev.michelle.trails.presentation.JournalUiState
import dev.michelle.trails.presentation.JournalScreen as Destination

@Composable
fun JournalScreen(
    state: JournalUiState,
    onEvent: (JournalEvent) -> Unit,
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    onOpenHealth: () -> Unit = {},
    onShareHealth: (Walk) -> Unit = {},
    healthStatuses: Map<String, ActivityHealthStatus> = emptyMap(),
    onRetryHealth: (String) -> Unit = {},
    onOpenSyncHistory: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var viewedLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var showTools by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val openTools = { focus.clearFocus(); keyboard?.hide(); showTools = true }
    val editing = state.screen == Destination.EDITOR
    val error = state.operationError
    val loadError = state.loadError

    BackHandler(enabled = state.screen != Destination.HOME || state.isSaving) {
        onEvent(JournalEvent.NavigateRequested(Destination.HOME))
    }
    LaunchedEffect(state.screen, state.draft.id) { listState.scrollToItem(0) }
    LaunchedEffect(state.fieldErrors.isNotEmpty()) { if (state.fieldErrors.isNotEmpty()) listState.scrollToItem(0) }
    LaunchedEffect(state.noticeId) {
        val notice = state.notice ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message = notice, actionLabel = if (state.undoEntry != null) "Undo" else null,
            withDismissAction = true, duration = SnackbarDuration.Long)
        onEvent(if (result == SnackbarResult.ActionPerformed) JournalEvent.UndoDeleteRequested else JournalEvent.NoticeDismissed)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { JournalAppBar(state.screen, state.draft.id != null, !state.isSaving,
            onBack = { focus.clearFocus(); keyboard?.hide(); onEvent(JournalEvent.NavigateRequested(Destination.HOME)) }, onTools = openTools) },
        bottomBar = { if (editing) EditorActionBar(state, onEvent) else JournalNavigation(state, onEvent) },
        floatingActionButton = {
            if (!editing && !state.isSaving && state.entries.isNotEmpty()) ExtendedFloatingActionButton(
                onClick = { onEvent(JournalEvent.NewEntryRequested) }, icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Log activity") }, containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary)
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(action = {
                    data.visuals.actionLabel?.let { label ->
                        TextButton(enabled = !state.isSaving, onClick = { data.performAction() }) { Text(label) }
                    }
                }, dismissAction = { TextButton(onClick = { data.dismiss() }) { Text("Dismiss") } }) { Text(data.visuals.message) }
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets), contentAlignment = Alignment.TopCenter) {
            LazyColumn(state = listState, modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().imePadding().testTag("journal-content"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (state.isSaving) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (error != null) item {
                    JournalMessage("Something needs attention", error, error = true) {
                        Row {
                            TextButton(onClick = { onEvent(JournalEvent.ErrorDismissed) }) { Text("Dismiss") }
                            if (state.undoEntry != null) TextButton(enabled = !state.isSaving, onClick = { onEvent(JournalEvent.UndoDeleteRequested) }) { Text("Retry undo") }
                        }
                    }
                }
                if (loadError != null) item {
                    JournalMessage("Couldn’t load your journal", loadError, error = true) {
                        TextButton(enabled = !state.isSaving, onClick = { onEvent(JournalEvent.RetryRequested) }) { Text("Try again") }
                    }
                }
                if (state.isLoading) item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text("Loading your journal…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                when (state.screen) {
                    Destination.HOME -> journalHomeItems(state, onEvent, onExport, openTools, onOpenSyncHistory,
                        healthStatuses, onShareHealth, onRetryHealth) { viewedLocation = it }
                    Destination.HISTORY -> journalHistoryItems(state, onEvent, healthStatuses, onShareHealth, onRetryHealth) { viewedLocation = it }
                    Destination.EDITOR -> {
                        if (state.fieldErrors.isNotEmpty()) item {
                            JournalMessage("Check your activity details", "${state.fieldErrors.size} fields need a quick check.", error = true)
                        }
                        item { EntryEditor(state, showActions = false, onEvent = onEvent) }
                    }
                }
                item { Spacer(Modifier.height(if (editing) 8.dp else 76.dp)) }
            }
        }
    }
    if (showTools) JournalToolsSheet(state, onEvent, { showTools = false }, onExport, onImport, onOpenHealth, onOpenSyncHistory)
    viewedLocation?.let { point ->
        MapLocationDialog(point, readOnly = true, onDismiss = { viewedLocation = null }, onConfirm = {})
    }
    if (state.showDiscardPrompt) {
        AlertDialog(
            onDismissRequest = { onEvent(JournalEvent.DiscardCancelled) },
            title = { Text("Discard unsaved changes?") },
            text = { Text("Your changes have not been saved to the journal.") },
            confirmButton = { TextButton(onClick = { onEvent(JournalEvent.DiscardConfirmed) }) { Text("Discard changes") } },
            dismissButton = { TextButton(onClick = { onEvent(JournalEvent.DiscardCancelled) }) { Text("Keep editing") } },
        )
    }
    state.pendingImport?.let { plan ->
        AlertDialog(
            onDismissRequest = { onEvent(JournalEvent.ImportDismissed) },
            title = { Text("Restore backup?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${plan.entries.size} activities found. ${plan.additions} can be added; ${plan.skipped} existing IDs will be kept unchanged. Nothing is replaced.")
                    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            },
            confirmButton = {
                TextButton(enabled = !state.isSaving, onClick = {
                    onEvent(if (plan.additions > 0) JournalEvent.ImportConfirmed else JournalEvent.ImportDismissed)
                }) { Text(if (state.isSaving) "Restoring…" else if (plan.additions > 0) "Restore ${plan.additions} activities" else "Close") }
            },
            dismissButton = { TextButton(enabled = !state.isSaving, onClick = { onEvent(JournalEvent.ImportDismissed) }) { Text("Cancel") } },
        )
    }
}


@Composable
private fun JournalNavigation(state: JournalUiState, onEvent: (JournalEvent) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        listOf(Destination.HOME, Destination.HISTORY).forEach { destination ->
            val label = if (destination == Destination.HOME) "Home" else "Journal"
            NavigationBarItem(selected = state.screen == destination, enabled = !state.isSaving,
                icon = { Icon(if (destination == Destination.HOME) Icons.Default.Home else Icons.AutoMirrored.Filled.List, contentDescription = null) },
                label = { Text(label) }, onClick = { if (state.screen != destination) onEvent(JournalEvent.NavigateRequested(destination)) })
        }
    }
}

@Composable
private fun EditorActionBar(state: JournalUiState, onEvent: (JournalEvent) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = !state.isSaving, onClick = { focus.clearFocus(); keyboard?.hide(); onEvent(JournalEvent.NavigateRequested(Destination.HOME)) }) { Text("Cancel") }
            Button(enabled = !state.isSaving, onClick = { focus.clearFocus(); keyboard?.hide(); onEvent(JournalEvent.SaveRequested) },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("save-entry")) {
                Text(if (state.isSaving) "Saving…" else if (state.draft.id == null) "Save activity" else "Save changes")
            }
        }
    }
}
