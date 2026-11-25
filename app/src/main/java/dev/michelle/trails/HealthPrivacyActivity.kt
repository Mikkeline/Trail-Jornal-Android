package dev.michelle.trails

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/** Permission-rationale entry point required by Health Connect on supported Android versions. */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrailTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Your workout data", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        Text("Trail Journal reads workouts and distance only when you ask to preview them. You choose what to import. Imported workouts stay in the local journal and can be included in a backup you create.")
                        Text("Sharing is optional and writes only workouts you choose, with title, activity type, distance, duration, and chosen start time. If you enable automatic sharing, saved journal activities with a start time and later edits are queued for Health Connect. Android may run retries in the background. Notes and map pins are not included. There is no app-owned health-data server.")
                        Text("Permissions can be changed or revoked in Health Connect. Removing a shared journal entry queues deletion of its Health Connect records after a short Undo grace period. Undo cancels the pending removal or queues restoration if it already ran. Other apps control their own copies.")
                        TextButton(onClick = { finish() }) { Text("Done") }
                    }
                }
            }
        }
    }
}
