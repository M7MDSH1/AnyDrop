package com.anydrop.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
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
    // color getters resolve against the palette just activated above.
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Amber,
            onPrimary = OnAccent,
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
    } else {
        lightColorScheme(
            primary = Amber,
            onPrimary = OnAccent,
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
