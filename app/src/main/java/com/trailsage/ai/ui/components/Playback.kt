package com.charles.trailsage.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Oversized play/pause control for driving mode, with a pulsing ring when playing. */
@Composable
fun LargeNarrationButton(playing: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "narrationRing")
    val ringScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (playing) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ringScale",
    )
    Box(modifier.size(200.dp), contentAlignment = Alignment.Center) {
        if (playing) {
            Box(
                Modifier
                    .matchParentSize()
                    .scale(ringScale)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
            )
        }
        FilledIconButton(
            onClick = onToggle,
            modifier = Modifier.size(168.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (playing) "Pause narration" else "Play narration",
                modifier = Modifier.size(84.dp),
            )
        }
    }
}

/** Secondary driving control (back / skip), large touch target with label. */
@Composable
fun DrivingControlButton(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, label, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Voice pack list item with neural preview + selection (natural_voice_setup / voice settings). */
@Composable
fun VoicePreviewCard(
    name: String,
    style: String,
    selected: Boolean,
    previewing: Boolean = false,
    modifier: Modifier = Modifier,
    onPreview: () -> Unit,
    onSelect: () -> Unit,
) {
    SurfaceCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(style, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onPreview) {
                Icon(
                    if (previewing) Icons.Default.GraphicEq else Icons.Default.VolumeUp,
                    "Preview voice",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(4.dp))
            if (selected) {
                AssistChip(onClick = {}, label = { Text("Selected") }, leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) })
            } else {
                FilledTonalButton(onSelect) { Text("Select") }
            }
        }
    }
}

/** Glass overlay chips shown over the driving-mode map strip (road name + speed). */
@Composable
fun MapStatusOverlay(roadName: String, speedLabel: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Navigation, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            }
            Text(roadName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(speedLabel, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("MPH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
