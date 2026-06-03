package com.charles.trailsage.ui.screens.destination

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.charles.trailsage.R
import com.charles.trailsage.ui.components.*

@Composable
fun DestinationDetailScreen(onBack: () -> Unit, onStartTour: () -> Unit) = DetailScaffold("Adirondack High Peaks Loop", onBack) {
    HeroDestinationCard(
        title = "High Peaks Loop",
        subtitle = "95-minute scenic drive through Lake Placid, Keene Valley, and the Cascade Lakes corridor.",
        category = "Scenic Drive",
        categoryIcon = Icons.Default.Forest,
        modifier = Modifier.fillMaxWidth().height(240.dp).clip(MaterialTheme.shapes.large),
        image = R.drawable.hero_forest,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = {}, label = { Text("95 min") }, leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)) })
        OfflineStatusChip()
    }
    InfoCard(
        "Offline scenic drive",
        "A loop through Adirondack mountain communities and a High Peaks Wilderness gateway, narrated by your on-device guide.",
    )
    InfoCard(
        "Sources included",
        "Wikipedia contributors and OpenStreetMap contributors are retained inside the downloaded pack for full attribution.",
    )
    PrimaryButton("Start tour", onClick = onStartTour)
}
