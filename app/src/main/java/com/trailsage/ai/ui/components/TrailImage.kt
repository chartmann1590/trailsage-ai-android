package com.charles.trailsage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.charles.trailsage.ui.theme.ForestContainer
import com.charles.trailsage.ui.theme.SunriseGold
import java.io.File

/**
 * Offline-first image surface. Renders [model] (a bundled drawable res id, a local
 * [File]/path, or a Uri) through Coil; when the model is null/missing or fails to
 * load it falls back to a forest→gold gradient with an icon, so card layouts stay
 * identical with no network-image dependency (prompt.txt: offline-first, no paid APIs).
 */
@Composable
fun TrailImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector? = null,
) {
    val normalized = when (model) {
        is String -> model.takeIf { it.isNotBlank() }?.let { if (it.startsWith("http") || it.startsWith("content")) it else File(it) }
        else -> model
    }
    if (normalized == null) {
        GradientPlaceholder(modifier, fallbackIcon)
        return
    }
    SubcomposeAsyncImage(
        model = normalized,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = { GradientPlaceholder(Modifier.fillMaxSize(), fallbackIcon) },
        error = { GradientPlaceholder(Modifier.fillMaxSize(), fallbackIcon) },
    )
}

@Composable
private fun GradientPlaceholder(modifier: Modifier, icon: ImageVector?) {
    Box(
        modifier.background(
            Brush.linearGradient(listOf(ForestContainer, SunriseGold.copy(alpha = 0.85f)))
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(44.dp))
        }
    }
}
