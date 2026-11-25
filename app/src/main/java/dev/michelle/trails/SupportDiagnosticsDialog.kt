package dev.michelle.trails

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.michelle.trails.presentation.SupportEvent
import dev.michelle.trails.presentation.SupportUiState

@Composable
internal fun SupportDiagnosticsDialog(state: SupportUiState, onEvent: (SupportEvent) -> Unit, onSave: () -> Unit) {
    val preview = state.preview ?: return
    JournalDetailDialog("Review diagnostic report", { onEvent(SupportEvent.PreviewDismissed) }, closeEnabled = !state.isBusy,
        bottomBar = {
            JournalDetailActions {
                Text("Review before saving. Nothing is sent automatically.", style = MaterialTheme.typography.bodySmall)
                Button(enabled = !state.isBusy, onClick = onSave,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("save-diagnostics")) {
                    Text(if (state.isBusy) "Saving…" else "Save report")
                }
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("This is the exact text that will be saved. It contains operational status, without activity details or raw errors.")
            state.error?.let { JournalMessage("Couldn’t save report", it, error = true) }
            state.message?.let { JournalMessage("Diagnostic report", it) }
            if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            JournalDetailSection("Report preview") {
                SelectionContainer { Text(preview, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}
