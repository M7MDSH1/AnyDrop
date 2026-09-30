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
    // Activate the palette BEFORE anything reads the color getters below, and
    // make the system bars follow the mode. The palette write also triggers
    // recomposition of every composable that read a color getter.
    TapeDeckPaletteState.current.value = if (darkTheme) DarkPalette else LightPalette

    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        window.statusBarColor = DarkGray.toArgb()
        window.navigationBarColor = Black.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // Schemes are built inside the composable (not as file-level vals) so the
    // color getters resolve against the palette just activated above. Every surface
    // role is set: menus, dialogs and sheets otherwise fall back to Material's
    // purple-grey defaults, which clash with the warm palette.
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Amber,
            onPrimary = OnAccent,
            primaryContainer = Color(0xFF3E2C08),
            onPrimaryContainer = AmberBright,
            secondary = AmberBright,
            onSecondary = OnAccent,
            secondaryContainer = MediumGray,
            onSecondaryContainer = White,
            tertiary = LedBlue,
            background = Black,
            onBackground = White,
            surface = DarkGray,
            onSurface = White,
            surfaceVariant = MediumGray,
            onSurfaceVariant = White.copy(alpha = 0.7f),
            surfaceTint = Amber,
            surfaceContainerLowest = Color(0xFF0B0A09),
            surfaceContainerLow = Black,
            surfaceContainer = Color(0xFF1F1D19),
            surfaceContainerHigh = Color(0xFF26231F),
            surfaceContainerHighest = MediumGray,
            surfaceBright = Color(0xFF34302A),
            surfaceDim = Black,
            inverseSurface = White,
            inverseOnSurface = Black,
            inversePrimary = AmberDim,
            outline = LightGray,
            outlineVariant = Hairline,
            error = StatusRed,
            onError = Color.Black,
            errorContainer = Color(0xFF4A1515),
            onErrorContainer = Color(0xFFFFDAD6),
            scrim = Color.Black
        )
    } else {
        lightColorScheme(
            primary = Amber,
            onPrimary = OnAccent,
            primaryContainer = Color(0xFFFFE2B3),
            onPrimaryContainer = Color(0xFF3A2200),
            secondary = AmberBright,
            onSecondary = OnAccent,
            secondaryContainer = MediumGray,
            onSecondaryContainer = White,
            tertiary = LedBlue,
            background = Black,
            onBackground = White,
            surface = DarkGray,
            onSurface = White,
            surfaceVariant = MediumGray,
            onSurfaceVariant = White.copy(alpha = 0.7f),
            surfaceTint = Amber,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Black,
            surfaceContainer = Color(0xFFF8F4EE),
            surfaceContainerHigh = Color(0xFFF1EBE2),
            surfaceContainerHighest = MediumGray,
            surfaceBright = Color.White,
            surfaceDim = Color(0xFFE2D9CC),
            inverseSurface = Color(0xFF34302A),
            inverseOnSurface = Color(0xFFF8F4EE),
            inversePrimary = Color(0xFFFFB000),
            outline = LightGray,
            outlineVariant = Hairline,
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
