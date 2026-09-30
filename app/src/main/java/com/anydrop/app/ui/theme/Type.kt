package com.anydrop.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

val MonoFont = FontFamily.Monospace

/**
 * Wide "tape label" letter-spacing is part of the look, but Arabic is a joined script:
 * spacing pulls its letters apart. False while the app language is Arabic.
 */
val LocalWideTracking = staticCompositionLocalOf { true }

/** Letter-spacing that turns itself off for Arabic. Use instead of `letterSpacing = N.sp`. */
@Composable
fun tracked(sp: Float): TextUnit = if (LocalWideTracking.current) sp.sp else 0.sp

fun tapeDeckTypography(wideTracking: Boolean): Typography {
    fun t(sp: Float) = if (wideTracking) sp.sp else 0.sp
    return Typography(
        // The wordmark is always Latin ("ANYDROP"), so it keeps its spacing.
        displayLarge = TextStyle(
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            letterSpacing = 4.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = MonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = t(2f)
        ),
        titleLarge = TextStyle(
            fontFamily = MonoFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            letterSpacing = t(1f)
        ),
        bodyLarge = TextStyle(
            fontFamily = MonoFont,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = MonoFont,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp
        ),
        labelSmall = TextStyle(
            fontFamily = MonoFont,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = t(2f)
        )
    )
}

val TapeDeckTypography = tapeDeckTypography(wideTracking = true)
