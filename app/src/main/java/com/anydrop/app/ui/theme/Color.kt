package com.anydrop.app.ui.theme

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * Theme palette. Dark mode keeps the original AnyDrop look; light mode is the
 * warm "paper tape" variant. The exported color vals below are getters backed by
 * [TapeDeckPaletteState], so any composable that reads them during composition
 * subscribes to the active palette and re-renders when the mode switches — no
 * per-screen call-site changes needed.
 */
data class TapeDeckPalette(
    val surface: Color,         // cards, nav bar, containers  (was Black)
    val background: Color,      // page backgrounds            (was DarkGray)
    val surfaceVariant: Color,  // dialogs, secondary surfaces (was MediumGray)
    val outline: Color,         // borders                     (was LightGray)
    val content: Color,         // primary text                (was White)
    val accent: Color,          // (was Amber)
    val accentDim: Color,       // (was AmberDim)
    val accentBright: Color     // (was AmberBright)
)

internal val DarkPalette = TapeDeckPalette(
    surface = Color(0xFF0D0D0D),
    background = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFF2D2D2D),
    outline = Color(0xFF3A3A3A),
    content = Color(0xFFE0E0E0),
    accent = Color(0xFFFFB000),
    accentDim = Color(0xFFCC8800),
    accentBright = Color(0xFFFFD740)
)

internal val LightPalette = TapeDeckPalette(
    surface = Color(0xFFFDFBF7),
    background = Color(0xFFEDE7DC),
    surfaceVariant = Color(0xFFE1D9CB),
    outline = Color(0xFFCFC5B2),
    content = Color(0xFF201D18),
    accent = Color(0xFFB26900),
    accentDim = Color(0xFF9A6400),
    accentBright = Color(0xFFD98E00)
)

object TapeDeckPaletteState {
    val current: MutableState<TapeDeckPalette> = mutableStateOf(DarkPalette)
}

val Amber: Color get() = TapeDeckPaletteState.current.value.accent
val AmberDim: Color get() = TapeDeckPaletteState.current.value.accentDim
val AmberBright: Color get() = TapeDeckPaletteState.current.value.accentBright
val Black: Color get() = TapeDeckPaletteState.current.value.surface
val DarkGray: Color get() = TapeDeckPaletteState.current.value.background
val MediumGray: Color get() = TapeDeckPaletteState.current.value.surfaceVariant
val LightGray: Color get() = TapeDeckPaletteState.current.value.outline
val White: Color get() = TapeDeckPaletteState.current.value.content

/** Dark content drawn ON amber fills (buttons/badges). Constant in both modes. */
val OnAccent = Color(0xFF0D0D0D)

val LedRed = Color(0xFFFF1744)
val LedGreen = Color(0xFF00E676)
val LedBlue = Color(0xFF448AFF)

val VuGreen = Color(0xFF76FF03)
val VuYellow = Color(0xFFFFEA00)
val VuRed = Color(0xFFFF1744)
