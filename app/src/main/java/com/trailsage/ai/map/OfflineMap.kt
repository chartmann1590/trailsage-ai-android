package com.charles.trailsage.map

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import java.io.File

class OfflineMapManager(private val context: Context) {
    fun validPmTiles(file: File): Boolean = file.isFile && file.inputStream().use { input ->
        String(input.readNBytes(7), Charsets.US_ASCII) == "PMTiles"
    }
    fun style(file: File): String = """{"version":8,"sources":{"offline":{"type":"vector","url":"pmtiles://${file.toURI()}"}},"layers":[{"id":"background","type":"background","paint":{"background-color":"#dde7dd"}}]}"""
}

@Composable
fun ActiveTourMap(file: File, modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = LocalLifecycleOwner.current
    val manager = remember { OfflineMapManager(context) }
    if (!manager.validPmTiles(file)) { fallback(); return }
    val mapView = remember { MapLibre.getInstance(context); MapView(context) }
    DisposableEffect(owner, mapView) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = mapView.onStart()
            override fun onResume(owner: LifecycleOwner) = mapView.onResume()
            override fun onPause(owner: LifecycleOwner) = mapView.onPause()
            override fun onStop(owner: LifecycleOwner) = mapView.onStop()
            override fun onDestroy(owner: LifecycleOwner) = mapView.onDestroy()
        }
        owner.lifecycle.addObserver(observer)
        mapView.onCreate(null)
        mapView.getMapAsync { it.setStyle(manager.style(file)) }
        onDispose { owner.lifecycle.removeObserver(observer); mapView.onDestroy() }
    }
    AndroidView({ mapView }, modifier.fillMaxSize())
}
