package com.charles.trailsage.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.trailsage.ui.components.*
import com.charles.trailsage.ui.theme.ForestContainer
import com.charles.trailsage.ui.theme.SunriseGold

@Composable
fun ExploreScreen(
    onOpenRouteBuilder: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenStory: (String) -> Unit,
    onOpenItinerary: () -> Unit,
    vm: ExploreViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val saved by vm.savedTours.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("Where are you driving?", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Plan a road trip and TrailSage narrates the stops with on-device AI as you reach them.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { PlanTripCard(onOpenRouteBuilder) }

        item { SystemStatusRow(aiReady = ui.aiReady, voiceReady = ui.voiceReady, offlineReady = true) }

        item {
            SectionHeader(
                title = if (ui.isGenerated) "Your AI road trip" else "Featured tour",
                actionLabel = "Open map",
                onAction = onOpenMap,
            )
        }
        item { ActiveTourCard(ui, onOpenItinerary) }

        item { SectionHeader("Stops on this route") }
        if (ui.stops.isEmpty()) {
            item { EmptyStateCard("No stops yet", "Build an adventure above to generate AI-narrated stops, or install the sample tour.") }
        } else {
            items(ui.stops, key = { it.storyId }) { stop ->
                StopNarrativeCard(stop, onClick = { onOpenStory(stop.storyId) })
            }
        }

        if (saved.isNotEmpty()) {
            item { SectionHeader("Your adventures") }
            items(saved, key = { it.id }) { tour ->
                SavedTourCard(tour, onClick = { vm.selectTour(tour.id); onOpenMap() })
            }
        }
    }
}

@Composable
private fun SavedTourCard(tour: ExploreViewModel.SavedTour, onClick: () -> Unit) {
    SurfaceCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                if (tour.isGenerated) Icons.Default.AutoAwesome else Icons.Default.Map,
                null,
                tint = if (tour.isGenerated) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(tour.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tour.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (tour.isActive) {
                AssistChip(onClick = onClick, label = { Text("Active") })
            }
        }
    }
}

@Composable
private fun PlanTripCard(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(ForestContainer, MaterialTheme.colorScheme.primary)))
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Route, null, tint = SunriseGold)
                Text("PLAN AN ADVENTURE", style = MaterialTheme.typography.labelSmall, color = SunriseGold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Build an adventure", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Enter a start and destination. We find the driving route and real attractions along it from Wikipedia, then the on-device AI writes the narration.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Start planning", style = MaterialTheme.typography.labelLarge, color = SunriseGold, fontWeight = FontWeight.Bold)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = SunriseGold, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ActiveTourCard(ui: ExploreViewModel.ExploreUi, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        TrailImage(model = ui.heroImage, contentDescription = ui.tourName, modifier = Modifier.matchParentSize(), fallbackIcon = Icons.Default.Map)
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(ui.tourName, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val meta = buildString {
                append("${ui.stops.size} stops")
                if (ui.driveMinutes > 0) append(" • ~${ui.driveMinutes} min")
                append(if (ui.isGenerated) " • AI-narrated" else " • offline sample")
            }
            Text(meta, style = MaterialTheme.typography.labelLarge, color = SunriseGold)
        }
    }
}

@Composable
private fun StopNarrativeCard(stop: ExploreViewModel.StopUi, onClick: () -> Unit) {
    SurfaceCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.small)) {
                TrailImage(stop.imageUrl, stop.title, Modifier.matchParentSize(), Icons.Default.Place)
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, "Play", tint = Color.White)
                }
            }
            Text(stop.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (stop.byAi) Icon(Icons.Default.AutoAwesome, "AI narrated", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
        }
        if (stop.preview.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stop.preview + if (stop.preview.length >= 200) "…" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
