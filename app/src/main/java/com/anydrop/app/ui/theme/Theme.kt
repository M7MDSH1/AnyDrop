package com.anydrop.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TapeDeckColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Black,
    primaryContainer = AmberDim,
    secondary = AmberBright,
    tertiary = LedBlue,
    background = Black,
    onBackground = White,
    surface = DarkGray,
    onSurface = White,
    surfaceVariant = MediumGray,
    onSurfaceVariant = White.copy(alpha = 0.7f),
    outline = LightGray,
    error = LedRed
)

@Composable
fun AnyDropTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TapeDeckColorScheme,
        typography = TapeDeckTypography,
        content = content
    )
}
