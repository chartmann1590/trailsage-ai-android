package com.charles.trailsage.ui.screens.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.trailsage.ui.AppViewModel
import com.charles.trailsage.ui.components.*

@Composable
fun ActiveTourScreen(vm: AppViewModel, onBack: () -> Unit, onOpenDriving: () -> Unit, onOpenMap: () -> Unit) =
    DetailScaffold("High Peaks Loop", onBack) {
        val stories by vm.stories.collectAsStateWithLifecycle()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GpsStatusChip()
            OfflineStatusChip()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Map, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Text("Offline map ready", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Route and POI overlay", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val next = stories.firstOrNull()
        InfoCard(
            "Next story: ${next?.title ?: "Loading"}",
            "Narration starts automatically when your vehicle enters the trigger radius.",
        )
        PrimaryButton("Open driving mode", onClick = onOpenDriving)
        SecondaryButton("Open map", onClick = onOpenMap)
    }
