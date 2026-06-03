package com.charles.trailsage.ui.screens.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.trailsage.map.TourMapView
import com.charles.trailsage.ui.components.MapStatusOverlay

@Composable
fun MapScreen(onOpenStory: (String) -> Unit = {}, vm: MapViewModel = hiltViewModel()) {
    val data by vm.data.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val speed by vm.speedMph.collectAsStateWithLifecycle()
    val next by vm.nextStop.collectAsStateWithLifecycle()
    val gps by vm.gps.collectAsStateWithLifecycle()
    val direction by vm.currentDirection.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Box(Modifier.fillMaxSize()) {
        TourMapView(
            routeGeoJson = data.routeGeoJson,
            stops = data.stops,
            modifier = Modifier.fillMaxSize(),
            userLatitude = gps?.location?.latitude,
            userLongitude = gps?.location?.longitude,
            onStopClick = onOpenStory,
        )

        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Live turn-by-turn banner (spoken in the selected voice as you approach).
            direction?.let { dir ->
                Surface(tonalElevation = 4.dp, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Navigation, null, tint = MaterialTheme.colorScheme.onPrimary)
                        Text(dir, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Surface(
                tonalElevation = 3.dp,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(data.tourName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    val sub = when {
                        data.stops.isEmpty() -> "No stops on this tour yet."
                        gps?.location == null -> "Waiting for GPS — start driving and narration plays automatically at each stop."
                        next != null -> "Next stop: ${next!!.name} • ${formatDistance(next!!.distanceMeters)}"
                        else -> "On route — ${data.stops.size} stops ahead."
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val playingTitle = nowPlaying
            if (playingTitle != null) {
                Surface(tonalElevation = 4.dp, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Column(Modifier.weight(1f)) {
                            Text("Now playing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(playingTitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FilledIconButton(onClick = vm::stopPlayback) { Icon(Icons.Default.Stop, "Stop") }
                    }
                }
            } else {
                MapStatusOverlay(roadName = next?.name ?: "On route", speedLabel = speed.toString())
            }
            Text(
                "Map data © OpenStreetMap contributors (ODbL)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private fun formatDistance(meters: Int): String {
    val miles = meters / 1609.34
    return if (miles >= 0.2) String.format(java.util.Locale.US, "%.1f mi", miles) else "$meters m"
}
