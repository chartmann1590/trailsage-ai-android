package com.charles.trailsage.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Stitch typography scale (DESIGN.md "typography"), mapped onto the Material 3
 * type system. The Stitch export specifies the Inter family throughout; to keep
 * the build fully offline with no network-font dependency we render the scale
 * with the platform default family. Drop Inter `.ttf` files into res/font and
 * swap [TrailSageFont] to enable the exact face — every size/weight already matches.
 */
private val TrailSageFont = FontFamily.Default

val TrailSageTypography = Typography(
    // display-lg: 48 / 56 / -0.02em / 700
    displayLarge = TextStyle(
        fontFamily = TrailSageFont, fontWeight = FontWeight.Bold,
        fontSize = 48.sp, lineHeight = 56.sp, letterSpacing = (-0.02).em,
    ),
    displayMedium = TextStyle(
        fontFamily = TrailSageFont, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.02).em,
    ),
    // headline-lg: 32 / 40 / 700
    headlineLarge = TextStyle(
        fontFamily = TrailSageFont, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 40.sp,
    ),
    // headline-md: 24 / 32 / 600
    headlineMedium = TextStyle(
        fontFamily = TrailSageFont, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = TrailSageFont, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    // title-lg: 20 / 28 / 600
    titleLarge = TextStyle(
        fontFamily = TrailSageFont, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = TrailSageFont, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = TrailSageFont, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    // body-lg: 18 / 28 / 400
    bodyLarge = TextStyle(
        fontFamily = TrailSageFont, fontWeight = FontWeight.Normal,
        fontSize = 18.sp, lineHeight = 28.sp,
    ),
    // body-md: 16 / 24 / 400
    bodyMedium = TextStyle(
        fontFamily = TrailSageFont, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = TrailSageFont, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
    // label-lg: 14 / 20 / 600 / 0.1px
    labelLarge = TextStyle(
        fontFamily = TrailSageFont, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = TrailSageFont, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
    // label-sm: 11 / 16 / 500 / 0.5px
    labelSmall = TextStyle(
        fontFamily = TrailSageFont, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
)
