package dev.michelle.trails.maps

import android.content.Context
import android.graphics.Color
import dev.michelle.trails.domain.GeoPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource

/** Native SDK details remain behind the provider-neutral Compose map boundary. */
internal class MapLibreLocationAdapter(
    context: Context,
    private val configuration: MapConfiguration,
    initialPoint: GeoPoint?,
    private val onPointSelected: (GeoPoint) -> Unit,
    private val onStatusChanged: (MapLoadState) -> Unit,
) {
    val view: MapView
    private var map: MapLibreMap? = null
    private var pinSource: GeoJsonSource? = null
    private var selectedPoint: GeoPoint? = initialPoint
    private var lastFocusRequest = 0
    private var lastReloadRequest = 0
    private var isStarted = false
    private var isResumed = false
    private var isDestroyed = false
    private var hasPositionedCamera = false
    private var styleGeneration = 0L

    init {
        MapLibre.getInstance(context.applicationContext)
        view = MapView(context)
        view.onCreate(null)
        view.addOnDidFailLoadingMapListener {
            if (!isDestroyed) {
                onStatusChanged(MapLoadState.Failed("Map unavailable. Check your connection and reload the map."))
            }
        }
        view.getMapAsync { readyMap ->
            if (!isDestroyed) {
                map = readyMap
                readyMap.addOnMapClickListener { position ->
                    val longitude = ((position.longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
                    onPointSelected(GeoPoint(position.latitude.coerceIn(-90.0, 90.0), longitude))
                    true
                }
                loadStyle()
            }
        }
    }

    fun update(point: GeoPoint?, focusRequest: Int, reloadRequest: Int) {
        if (isDestroyed) return
        if (selectedPoint != point) {
            selectedPoint = point
            renderPin()
        }
        if (focusRequest != lastFocusRequest) {
            lastFocusRequest = focusRequest
            focusSelection()
        }
        if (reloadRequest != lastReloadRequest) {
            lastReloadRequest = reloadRequest
            loadStyle()
        }
    }

    private fun loadStyle() {
        val currentMap = map ?: return
        pinSource = null
        val generation = ++styleGeneration
        onStatusChanged(MapLoadState.Loading)
        currentMap.setStyle(configuration.styleUrl) { style ->
            if (!isDestroyed && generation == styleGeneration) {
                val source = GeoJsonSource(PIN_SOURCE, EMPTY_FEATURES)
                style.addSource(source)
                style.addLayer(
                    CircleLayer(PIN_LAYER, PIN_SOURCE).withProperties(
                        circleRadius(9f),
                        circleColor(Color.rgb(49, 92, 67)),
                        circleStrokeColor(Color.WHITE),
                        circleStrokeWidth(3f),
                    ),
                )
                pinSource = source
                renderPin()
                if (!hasPositionedCamera) {
                    focusSelection()
                    hasPositionedCamera = true
                }
                onStatusChanged(MapLoadState.Ready)
            }
        }
    }

    private fun renderPin() {
        val point = selectedPoint
        // Only validated finite numbers enter this GeoJSON; no journal text is sent to the SDK.
        val geoJson = if (point == null) EMPTY_FEATURES else """
            {"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${point.longitude},${point.latitude}]},"properties":{}}]}
        """.trimIndent()
        pinSource?.setGeoJson(geoJson)
    }

    private fun focusSelection() {
        val point = selectedPoint ?: configuration.initialCenter
        // Web Mercator cannot display the poles; retain the original stored coordinates.
        val latitude = point.latitude.coerceIn(-85.05112878, 85.05112878)
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, point.longitude), configuration.zoom))
    }

    fun start() {
        if (!isStarted && !isDestroyed) {
            view.onStart()
            isStarted = true
        }
    }

    fun resume() {
        if (!isResumed && !isDestroyed) {
            start()
            view.onResume()
            isResumed = true
        }
    }

    fun pause() {
        if (isResumed && !isDestroyed) {
            view.onPause()
            isResumed = false
        }
    }

    fun stop() {
        pause()
        if (isStarted && !isDestroyed) {
            view.onStop()
            isStarted = false
        }
    }

    fun lowMemory() {
        if (!isDestroyed) view.onLowMemory()
    }

    fun destroy() {
        if (isDestroyed) return
        stop()
        isDestroyed = true
        pinSource = null
        map = null
        view.onDestroy()
    }

    private companion object {
        const val PIN_SOURCE = "trail-selected-location"
        const val PIN_LAYER = "trail-selected-location-circle"
        const val EMPTY_FEATURES = "{\"type\":\"FeatureCollection\",\"features\":[]}"
    }
}
