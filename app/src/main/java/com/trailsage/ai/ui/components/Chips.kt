package com.charles.trailsage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.charles.trailsage.ui.theme.RoadTripBlue

@Composable
fun OfflineStatusChip(ready: Boolean = true, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = {},
        modifier = modifier,
        label = { Text(if (ready) "Offline ready" else "Needs download", style = MaterialTheme.typography.labelLarge) },
        leadingIcon = { Icon(Icons.Default.CloudDone, null, Modifier.size(18.dp)) },
    )
}

@Composable
fun GpsStatusChip(active: Boolean = true, weak: Boolean = false, modifier: Modifier = Modifier) {
    val label = when {
        !active -> "GPS off"
        weak -> "Weak GPS signal"
        else -> "GPS monitoring"
    }
    AssistChip(
        onClick = {},
        modifier = modifier,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = {
            Icon(
                if (active) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                null,
                Modifier.size(18.dp),
                tint = if (weak || !active) MaterialTheme.colorScheme.error else RoadTripBlue,
            )
        },
    )
}

@Composable
fun SourceChip(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    AssistChip(
        onClick = onClick ?: {},
        modifier = modifier,
        label = { Text(text, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(16.dp)) },
    )
}

/** AI / Voice / Offline readiness pills from the Explore hero (explore_dashboard Stitch). */
@Composable
fun SystemStatusRow(aiReady: Boolean, voiceReady: Boolean, offlineReady: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot("AI Ready", aiReady)
        Divider()
        StatusDot("Voice Ready", voiceReady)
        Divider()
        StatusDot("Offline Mode", offlineReady)
    }
}

@Composable
private fun StatusDot(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (ok) MaterialTheme.colorScheme.inversePrimary else MaterialTheme.colorScheme.outlineVariant)
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .height(12.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}
