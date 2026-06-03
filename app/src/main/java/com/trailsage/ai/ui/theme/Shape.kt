package com.charles.trailsage.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Stitch shape language (DESIGN.md "Shapes"):
 * large containers 24dp, interactive elements 12dp, media/badges use full pills.
 */
val TrailSageShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),   // buttons / input fields (rounded-lg)
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),   // cards / bottom sheets (rounded-xl)
    extraLarge = RoundedCornerShape(28.dp),
)
