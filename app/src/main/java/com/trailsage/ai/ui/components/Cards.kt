package com.charles.trailsage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.charles.trailsage.ui.theme.SunriseGold

/**
 * High-aspect destination card: full-bleed image, bottom gradient scrim, category
 * label in sunrise gold, title + subtitle overlaid in white (explore_dashboard Stitch).
 */
@Composable
fun HeroDestinationCard(
    title: String,
    subtitle: String,
    category: String,
    categoryIcon: ImageVector,
    modifier: Modifier = Modifier,
    image: Any? = null,
    fallbackIcon: ImageVector? = null,
    onClick: () -> Unit = {},
) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        TrailImage(
            model = image,
            contentDescription = title,
            modifier = Modifier.matchParentSize(),
            fallbackIcon = fallbackIcon ?: categoryIcon,
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Black.copy(alpha = 0.30f),
                        1f to Color.Black.copy(alpha = 0.80f),
                    )
                )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(categoryIcon, null, tint = SunriseGold, modifier = Modifier.size(18.dp))
                Text(
                    category.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = SunriseGold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Compact nearby-story row: thumbnail with play overlay, title, duration + offline badge. */
@Composable
fun StoryRow(
    title: String,
    durationLabel: String,
    offline: Boolean,
    modifier: Modifier = Modifier,
    image: Any? = null,
    fallbackIcon: ImageVector? = null,
    onClick: () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(64.dp).clip(MaterialTheme.shapes.small)) {
            TrailImage(image, title, Modifier.matchParentSize(), fallbackIcon)
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, "Play", tint = Color.White)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(durationLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (offline) {
                    Box(Modifier.size(4.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.DownloadDone, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Small attribution credit block (Wikipedia/OSM) per DESIGN.md "Attribution Badges". */
@Composable
fun AttributionCard(title: String, detail: String, modifier: Modifier = Modifier) {
    SurfaceCard(modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
