package com.charles.trailsage.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

data class MapStop(val name: String, val latitude: Double, val longitude: Double, val storyId: String = "")

/**
 * OpenStreetMap raster base (keyless). Shared with [OfflineMapCache] so that tiles pre-cached
 * for a generated route are reused here without a network connection. Tiles are also cached
 * ambiently as they load, so a route that was viewed once keeps working offline afterward.
 */
private val OSM_RASTER_STYLE = OfflineMapCache.OSM_RASTER_STYLE

/**
 * Renders the active tour: OSM raster base, the route line (road-trip blue), and stop
 * markers (sunrise gold). Works for the AI-generated route tours and any tour with a
 * route.geojson + POIs. Tiles load online once, then render offline from the cache.
 */
@Composable
fun TourMapView(
    routeGeoJson: String?,
    stops: List<MapStop>,
    modifier: Modifier = Modifier,
    userLatitude: Double? = null,
    userLongitude: Double? = null,
    onStopClick: (String) -> Unit = {},
) {
    val currentOnStopClick = androidx.compose.runtime.rememberUpdatedState(onStopClick)
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val mapView = remember { MapLibre.getInstance(context); MapView(context) }
    val mapRef = remember { androidx.compose.runtime.mutableStateOf<MapLibreMap?>(null) }
    val styleRef = remember { androidx.compose.runtime.mutableStateOf<Style?>(null) }
    val centeredOnUser = remember { androidx.compose.runtime.mutableStateOf(false) }

    // As soon as a real location is known, center + zoom on the driver (once).
    androidx.compose.runtime.LaunchedEffect(userLatitude, userLongitude, mapRef.value) {
        val map = mapRef.value
        if (map != null && userLatitude != null && userLongitude != null && !centeredOnUser.value) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(userLatitude, userLongitude), 15.0))
            centeredOnUser.value = true
        }
    }

    // Populate / update the route + stops whenever the data loads (it arrives async, after
    // the style is built — so this must react, not just run once on style-load).
    androidx.compose.runtime.LaunchedEffect(routeGeoJson, stops, styleRef.value) {
        val style = styleRef.value ?: return@LaunchedEffect
        val map = mapRef.value ?: return@LaunchedEffect
        style.getSourceAs<GeoJsonSource>("route")?.setGeoJson(routeGeoJson ?: """{"type":"FeatureCollection","features":[]}""")
        style.getSourceAs<GeoJsonSource>("stops")?.setGeoJson(stopsFeatureCollection(stops))
        if (!centeredOnUser.value && (userLatitude == null || userLongitude == null)) {
            val points = stops.map { LatLng(it.latitude, it.longitude) }
            when {
                points.size >= 2 -> map.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.Builder().apply { points.forEach { include(it) } }.build(), 96
                    )
                )
                points.size == 1 -> map.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 12.0))
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(owner, mapView) {
        // NOTE: do NOT destroy the MapView from the lifecycle observer — onDispose owns
        // teardown. Destroying twice crashes libmaplibre's MapRenderer destructor (native).
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = mapView.onStart()
            override fun onResume(owner: LifecycleOwner) = mapView.onResume()
            override fun onPause(owner: LifecycleOwner) = mapView.onPause()
            override fun onStop(owner: LifecycleOwner) = mapView.onStop()
        }
        owner.lifecycle.addObserver(observer)
        mapView.onCreate(null)
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromJson(OSM_RASTER_STYLE)) { style ->
                // Add empty sources + layers up front; the LaunchedEffect above fills them in
                // as soon as the tour data is loaded (route line + stop markers).
                style.addSource(GeoJsonSource("route"))
                style.addLayer(
                    LineLayer("route-line", "route").withProperties(
                        PropertyFactory.lineColor("#4A90E2"),
                        PropertyFactory.lineWidth(5f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    )
                )
                style.addSource(GeoJsonSource("stops"))
                style.addLayer(
                    CircleLayer("stops-circle", "stops").withProperties(
                        PropertyFactory.circleColor("#FFB84D"),
                        PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(2f),
                    )
                )
                enableLocation(context, map, style)
                styleRef.value = style
                mapRef.value = map
            }
            // Tap a stop marker -> open that stop's page. Query a small box for easy tapping.
            map.addOnMapClickListener { latLng ->
                val p = map.projection.toScreenLocation(latLng)
                val box = android.graphics.RectF(p.x - 28f, p.y - 28f, p.x + 28f, p.y + 28f)
                val id = map.queryRenderedFeatures(box, "stops-circle")
                    .firstOrNull { it.hasProperty("storyId") }
                    ?.getStringProperty("storyId")
                if (!id.isNullOrBlank()) { currentOnStopClick.value(id); true } else false
            }
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            runCatching {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }
    AndroidView({ mapView }, modifier.fillMaxSize())
}

/** Shows the blue current-location dot when location permission is granted. */
@SuppressLint("MissingPermission")
private fun enableLocation(context: Context, map: MapLibreMap, style: Style) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
    runCatching {
        map.locationComponent.apply {
            activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
            isLocationComponentEnabled = true
            // Center on and follow the driver's location whenever the map opens.
            cameraMode = CameraMode.TRACKING
            renderMode = RenderMode.COMPASS
            zoomWhileTracking(15.0)
        }
    }
}

private fun stopsFeatureCollection(stops: List<MapStop>): String {
    val features = stops.joinToString(",") { stop ->
        """{"type":"Feature","properties":{"name":"${stop.name.replace("\"", "'")}","storyId":"${stop.storyId}"},"geometry":{"type":"Point","coordinates":[${stop.longitude},${stop.latitude}]}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

/**
 * Small non-interactive map for a stop's detail page: OSM base centered on the stop with a
 * marker. No location component (it's a static "where is this" view).
 */
@Composable
fun StopLocationMap(latitude: Double, longitude: Double, label: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val mapView = remember { MapLibre.getInstance(context); MapView(context) }

    androidx.compose.runtime.DisposableEffect(owner, mapView) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = mapView.onStart()
            override fun onResume(owner: LifecycleOwner) = mapView.onResume()
            override fun onPause(owner: LifecycleOwner) = mapView.onPause()
            override fun onStop(owner: LifecycleOwner) = mapView.onStop()
        }
        owner.lifecycle.addObserver(observer)
        mapView.onCreate(null)
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromJson(OSM_RASTER_STYLE)) { style ->
                style.addSource(GeoJsonSource("stop", stopsFeatureCollection(listOf(MapStop(label, latitude, longitude)))))
                style.addLayer(
                    CircleLayer("stop-marker", "stop").withProperties(
                        PropertyFactory.circleColor("#FFB84D"),
                        PropertyFactory.circleRadius(9f),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(3f),
                    )
                )
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 13.5))
            }
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            runCatching { mapView.onPause(); mapView.onStop(); mapView.onDestroy() }
        }
    }
    AndroidView({ mapView }, modifier.fillMaxSize())
}
