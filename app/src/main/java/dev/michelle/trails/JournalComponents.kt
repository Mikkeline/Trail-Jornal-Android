package dev.michelle.trails

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.michelle.trails.domain.ActivityType
import dev.michelle.trails.presentation.JournalScreen

@Composable
internal fun ActivityGlyph(type: ActivityType, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.let { if (type == ActivityType.HIKE) it.onTertiaryContainer else it.onSecondaryContainer }
    Surface(modifier.size(44.dp), shape = RoundedCornerShape(14.dp),
        color = if (type == ActivityType.HIKE) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Canvas(Modifier.padding(10.dp)) {
            val w = size.width; val h = size.height; val stroke = 1.8.dp.toPx()
            if (type == ActivityType.HIKE) {
                val path = Path().apply { moveTo(0f, h * .85f); lineTo(w * .43f, h * .13f); lineTo(w * .7f, h * .58f); lineTo(w * .82f, h * .4f); lineTo(w, h * .85f) }
                drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(color, Offset(w * .3f, h * .36f), Offset(w * .44f, h * .45f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .44f, h * .45f), Offset(w * .55f, h * .33f), stroke, StrokeCap.Round)
            } else {
                drawCircle(color, w * .09f, Offset(w * .6f, h * .14f))
                val path = Path().apply { moveTo(w * .4f, h * .35f); lineTo(w * .57f, h * .3f); lineTo(w * .51f, h * .58f); lineTo(w * .25f, h * .85f); moveTo(w * .51f, h * .58f); lineTo(w * .72f, h * .85f); moveTo(w * .54f, h * .4f); lineTo(w * .8f, h * .52f) }
                drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
                if (type == ActivityType.INDOOR_WALK) drawLine(color, Offset(0f, h), Offset(w, h), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun JournalAppBar(screen: JournalScreen, editing: Boolean, enabled: Boolean, onBack: () -> Unit, onTools: () -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    val compact = LocalConfiguration.current.screenHeightDp < 480
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (screen == JournalScreen.EDITOR) {
                IconButton(enabled = enabled, onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Home") }
            } else if (!largeText) {
                Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp).clearAndSetSemantics {}) {
                    val color = MaterialTheme.colorScheme.onPrimary
                    Canvas(Modifier.padding(8.dp)) {
                        val w = size.width; val h = size.height
                        val mark = Path().apply { moveTo(w * .05f, h * .85f); lineTo(w * .43f, h * .2f); lineTo(w * .73f, h * .85f); lineTo(w * .88f, h * .58f); lineTo(w, h * .85f) }
                        drawPath(mark, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(when (screen) { JournalScreen.HOME -> "Trail Journal"; JournalScreen.HISTORY -> "Your journal"; JournalScreen.EDITOR -> if (editing) "Edit activity" else "Log activity" },
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                if (!largeText && !compact) Text(when (screen) { JournalScreen.HOME -> "Walks, hikes & everyday movement."; JournalScreen.HISTORY -> "Your activities, all in one place."; JournalScreen.EDITOR -> "Distance and duration are required." },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(enabled = enabled, onClick = onTools) { Icon(Icons.Default.MoreVert, contentDescription = "Journal tools") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun JournalSectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}, enabled: Boolean = true) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp).semantics { heading() })
        if (action != null) TextButton(enabled = enabled, onClick = onAction) { Text(action) }
    }
}

@Composable
internal fun JournalToolRow(title: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun JournalMessage(title: String, detail: String, error: Boolean = false, actions: @Composable () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = if (error) colors.errorContainer else colors.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            actions()
        }
    }
}
