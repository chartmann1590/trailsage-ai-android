package com.charles.trailsage.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Stitch "Modern Naturalist" palette.
 *
 * Token values are taken verbatim from the Stitch export design spec
 * (stitch_trailsage_ai_offline_tour_guide/.../trailsage_ai/DESIGN.md). The light
 * scheme mirrors the documented Material 3 tokens; the dark scheme is the
 * charcoal night-driving variant required by prompt.txt and the Driving Mode screen.
 */

// --- Brand anchors (DESIGN.md "Colors") ---
val ForestDeep = Color(0xFF061B0E)        // primary text / heavy actions
val ForestContainer = Color(0xFF1B3022)   // grounded brand container
val ForestSoft = Color(0xFFB4CDB8)         // inverse-primary, dark-mode primary
val ForestFixed = Color(0xFFD0E9D4)
val Sandstone = Color(0xFFD9C5B2)          // organic secondary / dividers
val SandstoneContainer = Color(0xFFF4DFCB)
val SunriseGold = Color(0xFFFFB84D)        // highlights / active state
val SunriseGoldDim = Color(0xFFFFB951)
val RoadTripBlue = Color(0xFF4A90E2)       // GPS paths / interactive map cues
val WarmOffWhite = Color(0xFFFBF9F4)       // primary light background
val Charcoal = Color(0xFF121212)           // dark mode / night driving

// --- Neutrals & state ---
private val OnSurfaceLight = Color(0xFF1B1C19)
private val OnSurfaceVariantLight = Color(0xFF434843)
private val OutlineLight = Color(0xFF737973)
private val OutlineVariantLight = Color(0xFFC3C8C1)
private val ErrorLight = Color(0xFFBA1A1A)
private val ErrorContainerLight = Color(0xFFFFDAD6)
private val OnErrorContainerLight = Color(0xFF93000A)

val LightColors = lightColorScheme(
    primary = ForestDeep,
    onPrimary = Color.White,
    primaryContainer = ForestContainer,
    onPrimaryContainer = Color(0xFF819986),
    inversePrimary = ForestSoft,
    secondary = Color(0xFF6B5C4C),
    onSecondary = Color.White,
    secondaryContainer = SandstoneContainer,
    onSecondaryContainer = Color(0xFF716252),
    tertiary = SunriseGold,
    onTertiary = Color(0xFF241400),
    tertiaryContainer = SunriseGoldDim,
    onTertiaryContainer = Color(0xFF3F2700),
    background = WarmOffWhite,
    onBackground = OnSurfaceLight,
    surface = WarmOffWhite,
    onSurface = OnSurfaceLight,
    surfaceVariant = Color(0xFFE4E2DD),
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceTint = Color(0xFF4D6453),
    inverseSurface = Color(0xFF30312E),
    inverseOnSurface = Color(0xFFF2F1EC),
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F3EE),
    surfaceContainer = Color(0xFFF0EEE9),
    surfaceContainerHigh = Color(0xFFEAE8E3),
    surfaceContainerHighest = Color(0xFFE4E2DD),
    surfaceBright = WarmOffWhite,
    surfaceDim = Color(0xFFDBDAD5),
)

val DarkColors = darkColorScheme(
    primary = ForestSoft,
    onPrimary = Color(0xFF0B2013),
    primaryContainer = ForestContainer,
    onPrimaryContainer = ForestFixed,
    inversePrimary = ForestDeep,
    secondary = Color(0xFFD7C3B0),
    onSecondary = Color(0xFF3A2E1F),
    secondaryContainer = Color(0xFF524436),
    onSecondaryContainer = SandstoneContainer,
    tertiary = SunriseGoldDim,
    onTertiary = Color(0xFF291800),
    tertiaryContainer = Color(0xFF633F00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Charcoal,
    onBackground = Color(0xFFF2F1EC),
    surface = Color(0xFF1A1C19),
    onSurface = Color(0xFFF2F1EC),
    surfaceVariant = Color(0xFF434843),
    onSurfaceVariant = Color(0xFFC3C8C1),
    surfaceTint = ForestSoft,
    inverseSurface = Color(0xFFF2F1EC),
    inverseOnSurface = Color(0xFF30312E),
    outline = Color(0xFF8D938C),
    outlineVariant = Color(0xFF434843),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF0E0F0D),
    surfaceContainerLow = Color(0xFF1A1C19),
    surfaceContainer = Color(0xFF1E201D),
    surfaceContainerHigh = Color(0xFF282A27),
    surfaceContainerHighest = Color(0xFF333531),
    surfaceBright = Color(0xFF35372F),
    surfaceDim = Charcoal,
)

/** Non–Material-slot accents that screens reference directly. */
object TrailSageAccents {
    val gold = SunriseGold
    val goldDim = SunriseGoldDim
    val blue = RoadTripBlue
    val sandstone = Sandstone
    val forest = ForestContainer
    /** Level-3 "glass" overlay fill (DESIGN.md elevation system). */
    val glassLight = Color(0xB3FFFFFF)
    val glassDark = Color(0x1AFBF9F4)
}
