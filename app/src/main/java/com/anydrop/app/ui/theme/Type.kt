package com.anydrop.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.anydrop.app.R

/** Geist, one variable font file read at four weights (needs API 26, the app's minimum). */
@OptIn(ExperimentalTextApi::class)
val AppFont = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.geist, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

/** Numbers that change while you look at them (speeds, times, sizes): Geist Mono, tabular. */
@OptIn(ExperimentalTextApi::class)
val NumberFont = FontFamily(
    listOf(400, 500).map { w ->
        Font(R.font.geist_mono, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

/** Kept for call sites written for the earlier monospace look; it is Geist now. */
val MonoFont = AppFont

/** The design has no letter-spaced labels any more; kept so call sites need no change. */
val LocalWideTracking = staticCompositionLocalOf { false }

/** Letter-spacing is off everywhere now. Use instead of `letterSpacing = N.sp`. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun tracked(sp: Float): TextUnit = 0.sp

@Suppress("UNUSED_PARAMETER")
fun tapeDeckTypography(wideTracking: Boolean): Typography {
    fun s(size: Float, weight: FontWeight, ls: Float = 0f, lh: Float = 0f) = TextStyle(
        fontFamily = AppFont,
        fontWeight = weight,
        fontSize = size.sp,
        letterSpacing = ls.sp,
        lineHeight = if (lh > 0f) lh.sp else TextUnit.Unspecified
    )
    return Typography(
        displayLarge = s(32f, FontWeight.SemiBold, -1f),
        displayMedium = s(28f, FontWeight.SemiBold, -0.8f),
        displaySmall = s(24f, FontWeight.SemiBold, -0.6f),
        headlineLarge = s(30f, FontWeight.SemiBold, -1f),
        headlineMedium = s(30f, FontWeight.SemiBold, -1f),
        headlineSmall = s(22f, FontWeight.SemiBold, -0.5f),
        titleLarge = s(20f, FontWeight.SemiBold, -0.4f),
        titleMedium = s(16f, FontWeight.SemiBold),
        titleSmall = s(14f, FontWeight.SemiBold),
        bodyLarge = s(15f, FontWeight.Normal, lh = 21f),
        bodyMedium = s(13f, FontWeight.Normal, lh = 18f),
        bodySmall = s(12f, FontWeight.Normal, lh = 16f),
        labelLarge = s(14f, FontWeight.Medium),
        labelMedium = s(12f, FontWeight.Medium),
        labelSmall = s(11f, FontWeight.Medium)
    )
}

val TapeDeckTypography = tapeDeckTypography(wideTracking = false)
