package dev.michelle.trails.maps

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.michelle.trails.domain.GeoPoint

data class MapConfiguration(
    val styleUrl: String = "https://tiles.openfreemap.org/styles/liberty",
    val initialCenter: GeoPoint = GeoPoint(3.139, 101.6869),
    val zoom: Double = 13.0,
)

sealed interface MapLoadState {
    data object Loading : MapLoadState
    data object Ready : MapLoadState
    data class Failed(val message: String) : MapLoadState
}

/** The app consumes domain coordinates, not MapLibre camera, layer, or geometry types. */
@Composable
fun LocationMap(
    point: GeoPoint?,
    onPointSelected: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
    focusRequest: Int = 0,
    reloadRequest: Int = 0,
    onStatusChanged: (MapLoadState) -> Unit = {},
    configuration: MapConfiguration = MapConfiguration(),
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pointCallback = rememberUpdatedState(onPointSelected)
    val statusCallback = rememberUpdatedState(onStatusChanged)
    val adapter = remember(context, configuration) {
        MapLibreLocationAdapter(context, configuration, point, { pointCallback.value(it) }, { statusCallback.value(it) })
    }

    DisposableEffect(adapter, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> adapter.start()
                Lifecycle.Event.ON_RESUME -> adapter.resume()
                Lifecycle.Event.ON_PAUSE -> adapter.pause()
                Lifecycle.Event.ON_STOP -> adapter.stop()
                Lifecycle.Event.ON_DESTROY -> adapter.destroy()
                else -> Unit
            }
        }
        val callbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = adapter.lowMemory()
            override fun onTrimMemory(level: Int) = adapter.lowMemory()
        }
        // Lifecycle registration catches up to an already resumed Activity/dialog host.
        lifecycle.addObserver(observer)
        context.applicationContext.registerComponentCallbacks(callbacks)
        onDispose {
            lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(callbacks)
            adapter.destroy()
        }
    }

    Column(modifier) {
        AndroidView(
            factory = { adapter.view },
            update = { adapter.update(point, focusRequest, reloadRequest) },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        MapAttribution()
    }
}

@Composable
private fun MapAttribution() {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(6.dp)) {
        Text(
            "Map tiles: OpenFreeMap",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clickable { runCatching { uriHandler.openUri("https://openfreemap.org/") } },
        )
        Text(
            "© OpenMapTiles",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clickable { runCatching { uriHandler.openUri("https://openmaptiles.org/") } },
        )
        Text(
            "© OpenStreetMap contributors · data licence",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clickable { runCatching { uriHandler.openUri("https://www.openstreetmap.org/copyright") } },
        )
    }
}
