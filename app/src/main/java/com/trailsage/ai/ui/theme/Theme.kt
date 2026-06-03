package com.charles.trailsage.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Root theme. Driving Mode passes [forceDark] = true so the night-driving UI stays
 * charcoal regardless of the system setting (DESIGN.md: "preserve night vision").
 */
@Composable
fun TrailSageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme || forceDark) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = TrailSageTypography,
        shapes = TrailSageShapes,
        content = content,
    )
}
