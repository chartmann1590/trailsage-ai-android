package com.charles.trailsage.ui.screens.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Warning
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.trailsage.ui.components.DrivingControlButton
import com.charles.trailsage.ui.components.LargeNarrationButton
import com.charles.trailsage.ui.components.MapStatusOverlay
import com.charles.trailsage.ui.theme.TrailSageTheme

/** Night-driving screen: forced-dark, oversized glanceable controls (driving_mode Stitch). */
@Composable
fun DrivingModeScreen(vm: DrivingViewModel = hiltViewModel()) {
    val playing by vm.playing.collectAsStateWithLifecycle()
    val usingFallback by vm.usingFallback.collectAsStateWithLifecycle()
    val noVoice by vm.noVoiceAvailable.collectAsStateWithLifecycle()
    val story by vm.currentStory.collectAsStateWithLifecycle()
    val gps by vm.gps.collectAsStateWithLifecycle()

    // GPS-triggered narration needs foreground location; ask once on entry.
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    TrailSageTheme(forceDark = true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
            ) {
                Text(
                    "PASSENGER CONTROLS RECOMMENDED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("NOW PLAYING", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        story?.title ?: "No story loaded",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(40.dp))
                    LargeNarrationButton(playing = playing, onToggle = vm::toggle)
                    Spacer(Modifier.height(40.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                        DrivingControlButton("Back", Icons.Default.Replay30, onClick = vm::previous)
                        DrivingControlButton("Skip", Icons.Default.SkipNext, onClick = vm::next)
                    }

                    if (usingFallback) {
                        Spacer(Modifier.height(20.dp))
                        FallbackWarning("Using the basic Android system voice because the natural offline voice is unavailable.")
                    }
                    if (noVoice) {
                        Spacer(Modifier.height(20.dp))
                        FallbackWarning("No narration voice is ready. Download a natural voice, or enable the Android fallback in Settings.")
                    }
                }

                val mph = gps?.location?.speed?.let { (it * 2.2369363f).toInt().coerceAtLeast(0) } ?: 0
                MapStatusOverlay(roadName = story?.title ?: "On route", speedLabel = mph.toString())
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun FallbackWarning(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}
