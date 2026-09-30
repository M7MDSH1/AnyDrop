package com.anydrop.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun AnyDropTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // The global palette behind the color getters is switched by AnyDropApp, outside
    // composition. Writing it from here raced other windows' compositions and could
    // leave the app stuck on the old palette. This function only reads the palette
    // for [darkTheme], so it is correct in the same frame the mode flips.
    val p = paletteFor(darkTheme)

    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        window.statusBarColor = p.background.toArgb()
        window.navigationBarColor = p.surface.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // Every surface role is set: menus, dialogs and sheets otherwise fall back to
    // Material's purple-grey defaults, which clash with the warm palette.
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            primaryContainer = Color(0xFF3E2C08),
            onPrimaryContainer = p.accentBright,
            secondary = p.accentBright,
            onSecondary = p.onAccent,
            secondaryContainer = p.surfaceVariant,
            onSecondaryContainer = p.content,
            tertiary = LedBlue,
            background = p.surface,
            onBackground = p.content,
            surface = p.background,
            onSurface = p.content,
            surfaceVariant = p.surfaceVariant,
            onSurfaceVariant = p.content.copy(alpha = 0.7f),
            surfaceTint = p.accent,
            surfaceContainerLowest = Color(0xFF0B0A09),
            surfaceContainerLow = p.surface,
            surfaceContainer = Color(0xFF1F1D19),
            surfaceContainerHigh = Color(0xFF26231F),
            surfaceContainerHighest = p.surfaceVariant,
            surfaceBright = Color(0xFF34302A),
            surfaceDim = p.surface,
            inverseSurface = p.content,
            inverseOnSurface = p.surface,
            inversePrimary = p.accentDim,
            outline = p.outline,
            outlineVariant = p.hairline,
            error = StatusRed,
            onError = Color.Black,
            errorContainer = Color(0xFF4A1515),
            onErrorContainer = Color(0xFFFFDAD6),
            scrim = Color.Black
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            primaryContainer = Color(0xFFFFE2B3),
            onPrimaryContainer = Color(0xFF3A2200),
            secondary = p.accentBright,
            onSecondary = p.onAccent,
            secondaryContainer = p.surfaceVariant,
            onSecondaryContainer = p.content,
            tertiary = LedBlue,
            background = p.surface,
            onBackground = p.content,
            surface = p.background,
            onSurface = p.content,
            surfaceVariant = p.surfaceVariant,
            onSurfaceVariant = p.content.copy(alpha = 0.7f),
            surfaceTint = p.accent,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = p.surface,
            surfaceContainer = Color(0xFFF8F4EE),
            surfaceContainerHigh = Color(0xFFF1EBE2),
            surfaceContainerHighest = p.surfaceVariant,
            surfaceBright = Color.White,
            surfaceDim = Color(0xFFE2D9CC),
            inverseSurface = Color(0xFF34302A),
            inverseOnSurface = Color(0xFFF8F4EE),
            inversePrimary = Color(0xFFFFB000),
            outline = p.outline,
            outlineVariant = p.hairline,
            error = StatusRed,
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            scrim = Color.Black
        )
    }

    val wideTracking = LocalConfiguration.current.locales[0]?.language != "ar"
    val typography = remember(wideTracking) { tapeDeckTypography(wideTracking) }

    CompositionLocalProvider(LocalWideTracking provides wideTracking) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
