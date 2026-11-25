package dev.michelle.trails

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import dev.michelle.trails.domain.GeoPoint
import dev.michelle.trails.maps.LocationMap
import dev.michelle.trails.maps.MapLoadState

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MapLocationDialog(
    initialPoint: GeoPoint?,
    readOnly: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (GeoPoint) -> Unit,
) {
    var latitude by rememberSaveable(initialPoint?.latitude) { mutableStateOf(initialPoint?.latitude?.toString().orEmpty()) }
    var longitude by rememberSaveable(initialPoint?.longitude) { mutableStateOf(initialPoint?.longitude?.toString().orEmpty()) }
    var showCoordinates by rememberSaveable { mutableStateOf(false) }
    var focusRequest by rememberSaveable { mutableIntStateOf(0) }
    var reloadRequest by rememberSaveable { mutableIntStateOf(0) }
    var mapStatus by remember { mutableStateOf<MapLoadState>(MapLoadState.Loading) }
    val selectedPoint = GeoPoint.parse(latitude, longitude).getOrNull()
    val hasCoordinateError = (latitude.isNotBlank() || longitude.isNotBlank()) && selectedPoint == null
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val mapHeight = (LocalConfiguration.current.screenHeightDp * .46f).coerceIn(240f, 520f).dp
    LaunchedEffect(mapStatus is MapLoadState.Failed) {
        if (mapStatus is MapLoadState.Failed) showCoordinates = true
    }
    val coordinatesVisible = showCoordinates

    JournalDetailDialog(if (readOnly) "Activity location" else "Choose a location", onDismiss,
        bottomBar = {
            JournalDetailActions {
                Text(if (selectedPoint == null) "Choose a pin or enter coordinates to continue." else "${selectedPoint.latitude}, ${selectedPoint.longitude}",
                    style = MaterialTheme.typography.bodySmall)
                Button(enabled = readOnly || selectedPoint != null, onClick = {
                    focus.clearFocus(); keyboard?.hide()
                    if (readOnly) onDismiss() else selectedPoint?.let(onConfirm)
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("confirm-map-location")) {
                    Text(if (readOnly) "Close map" else "Use this location")
                }
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (readOnly) "Explore your saved location." else "Tap the map to place a pin.", style = MaterialTheme.typography.bodyLarge)
            Text("Online map · no GPS permission needed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LocationMap(point = selectedPoint,
                onPointSelected = { point ->
                    if (!readOnly) {
                        latitude = point.latitude.toString(); longitude = point.longitude.toString()
                        focus.clearFocus(); keyboard?.hide()
                    }
                }, focusRequest = focusRequest, reloadRequest = reloadRequest, onStatusChanged = { mapStatus = it },
                modifier = Modifier.fillMaxWidth().height(mapHeight).semantics { contentDescription = "Map preview. Coordinates are available below the map." })
            when (val status = mapStatus) {
                MapLoadState.Loading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Loading map… Coordinates can also be used without a loaded map.", style = MaterialTheme.typography.bodySmall)
                }
                is MapLoadState.Failed -> JournalMessage("Map couldn’t load", status.message, error = true) {
                    Text(if (readOnly) "Your saved coordinates are still available." else "You can still enter and save coordinates below.")
                }
                MapLoadState.Ready -> Unit
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = selectedPoint != null, onClick = { focusRequest++ }) { Text("Show pin") }
                TextButton(onClick = { reloadRequest++ }) { Text("Reload map") }
            }
            OutlinedButton(onClick = { showCoordinates = !showCoordinates }, modifier = Modifier.fillMaxWidth().semantics {
                stateDescription = if (coordinatesVisible) "Expanded" else "Collapsed"
            }) { Text(if (coordinatesVisible) "Hide coordinates" else if (readOnly) "View coordinates" else "Enter coordinates") }
            if (coordinatesVisible) {
                JournalDetailSection(if (readOnly) "Saved coordinates" else "Manual coordinates") {
                    Text("Decimal degrees: latitude −90 to 90, longitude −180 to 180.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = latitude, onValueChange = { latitude = it }, label = { Text("Latitude") },
                        readOnly = readOnly, singleLine = true, isError = hasCoordinateError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth().semantics {
                            if (hasCoordinateError) error("Enter valid latitude and longitude.")
                        })
                    OutlinedTextField(value = longitude, onValueChange = { longitude = it }, label = { Text("Longitude") },
                        readOnly = readOnly, singleLine = true, isError = hasCoordinateError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth().semantics {
                            if (hasCoordinateError) error("Enter valid latitude and longitude.")
                        })
                    if (hasCoordinateError) Text("Enter latitude -90 to 90 and longitude -180 to 180 using decimal points.", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
