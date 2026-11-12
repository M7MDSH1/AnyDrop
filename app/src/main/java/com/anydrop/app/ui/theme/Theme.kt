package com.anydrop.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
    // Material's default purple-grey roles, which clash with the palette.
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            primaryContainer = p.accentSoft,
            onPrimaryContainer = p.accentText,
            secondary = p.accentText,
            onSecondary = p.onAccent,
            secondaryContainer = p.surfaceVariant,
            onSecondaryContainer = p.content,
            tertiary = LedBlue,
            background = p.background,
            onBackground = p.content,
            surface = p.surface,
            onSurface = p.content,
            surfaceVariant = p.surfaceVariant,
            onSurfaceVariant = p.content.copy(alpha = 0.72f),
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = Color(0xFF0A0C0F),
            surfaceContainerLow = p.surface,
            surfaceContainer = p.surface,
            surfaceContainerHigh = p.surfaceVariant,
            surfaceContainerHighest = Color(0xFF262B34),
            surfaceBright = Color(0xFF2B313B),
            surfaceDim = p.background,
            inverseSurface = p.content,
            inverseOnSurface = p.background,
            inversePrimary = p.accentDim,
            outline = p.outline,
            outlineVariant = p.hairline,
            error = StatusRed,
            onError = Color(0xFF1A0505),
            errorContainer = Color(0xFF3F1517),
            onErrorContainer = Color(0xFFFFDAD6),
            scrim = Color.Black
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            primaryContainer = p.accentSoft,
            onPrimaryContainer = p.accentText,
            secondary = p.accentText,
            onSecondary = Color.White,
            secondaryContainer = p.surfaceVariant,
            onSecondaryContainer = p.content,
            tertiary = LedBlue,
            background = p.background,
            onBackground = p.content,
            surface = p.surface,
            onSurface = p.content,
            surfaceVariant = p.surfaceVariant,
            onSurfaceVariant = p.content.copy(alpha = 0.72f),
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = p.surface,
            surfaceContainer = p.surface,
            surfaceContainerHigh = p.surfaceVariant,
            surfaceContainerHighest = Color(0xFFE3E8EE),
            surfaceBright = Color.White,
            surfaceDim = Color(0xFFDDE2E9),
            inverseSurface = Color(0xFF22262D),
            inverseOnSurface = Color(0xFFF3F5F8),
            inversePrimary = Color(0xFFFF8A5C),
            outline = p.outline,
            outlineVariant = p.hairline,
            error = StatusRed,
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            scrim = Color.Black
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TapeDeckTypography,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
