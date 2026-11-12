package com.anydrop.app.ui.theme

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * Theme palette: cold graphite neutrals with ONE accent, tangerine. Dark mode and light mode
 * share the accent; the light palette's text is deeper so it reads on white. The exported
 * color vals below are getters backed by [TapeDeckPaletteState], so any composable that reads
 * them during composition subscribes to the active palette and re-renders when the mode switches.
 *
 * Names kept from the earlier theme so every screen follows without call-site changes:
 * surface = cards, nav bar, containers; background = the page behind them.
 */
data class TapeDeckPalette(
    val surface: Color,         // cards, nav bar, containers
    val background: Color,      // page backgrounds
    val surfaceVariant: Color,  // fields, chips, secondary surfaces
    val outline: Color,         // borders
    val content: Color,         // primary text
    val accent: Color,          // fills (buttons, progress, switches)
    val accentDim: Color,
    val accentBright: Color,
    val onAccent: Color,        // text/icons drawn on accent fills (dark on tangerine)
    val hairline: Color,        // 1dp lines that separate panels from the page
    val accentText: Color,      // accent used AS text or icon color (passes 4.5:1 on its surface)
    val accentSoft: Color,      // tonal container: selected chips, tonal buttons
    val textMuted: Color,       // secondary text
    val textFaint: Color        // captions, hints
)

internal val DarkPalette = TapeDeckPalette(
    surface = Color(0xFF15181D),
    background = Color(0xFF0D0F12),
    surfaceVariant = Color(0xFF1C2027),
    outline = Color(0xFF3A414D),
    content = Color(0xFFECEFF3),
    accent = Color(0xFFFF6B35),
    accentDim = Color(0xFFD9531F),
    accentBright = Color(0xFFFF8A5C),
    onAccent = Color(0xFF1A0C05),
    hairline = Color(0xFF2A3039),
    accentText = Color(0xFFFF8A5C),
    accentSoft = Color(0xFF2D1B13),
    textMuted = Color(0xFFA5ADB9),
    textFaint = Color(0xFF8A93A1)
)

internal val LightPalette = TapeDeckPalette(
    surface = Color(0xFFFCFDFE),
    background = Color(0xFFF3F5F8),
    surfaceVariant = Color(0xFFEEF1F5),
    outline = Color(0xFFCDD4DD),
    content = Color(0xFF12151A),
    accent = Color(0xFFFF6B35),
    accentDim = Color(0xFFD9531F),
    accentBright = Color(0xFFFF8A5C),
    onAccent = Color(0xFF1A0C05),
    hairline = Color(0xFFDDE2E9),
    accentText = Color(0xFFA8340B),
    accentSoft = Color(0xFFFFE4D9),
    textMuted = Color(0xFF4A5361),
    textFaint = Color(0xFF565F6E)
)

object TapeDeckPaletteState {
    val current: MutableState<TapeDeckPalette> = mutableStateOf(DarkPalette)

    /**
     * Switches the active palette. Called only from outside composition (AnyDropApp
     * follows the saved setting), so the write lands in the global snapshot and no
     * composition pass can drop it or fight over it.
     */
    fun select(dark: Boolean) {
        current.value = paletteFor(dark)
    }
}

internal fun paletteFor(dark: Boolean): TapeDeckPalette = if (dark) DarkPalette else LightPalette

val Amber: Color get() = TapeDeckPaletteState.current.value.accent
val AmberDim: Color get() = TapeDeckPaletteState.current.value.accentDim
val AmberBright: Color get() = TapeDeckPaletteState.current.value.accentBright
val Black: Color get() = TapeDeckPaletteState.current.value.surface
val DarkGray: Color get() = TapeDeckPaletteState.current.value.background
val MediumGray: Color get() = TapeDeckPaletteState.current.value.surfaceVariant
val LightGray: Color get() = TapeDeckPaletteState.current.value.outline
val White: Color get() = TapeDeckPaletteState.current.value.content
val Hairline: Color get() = TapeDeckPaletteState.current.value.hairline

/** The accent where it is drawn as text or an icon: deeper in light mode so it stays readable. */
val AccentText: Color get() = TapeDeckPaletteState.current.value.accentText

/** Secondary text and captions (passing 4.5:1 on the surfaces they sit on). */
val TextMuted: Color get() = TapeDeckPaletteState.current.value.textMuted
val TextFaint: Color get() = TapeDeckPaletteState.current.value.textFaint

/** Tonal container behind accent-colored content (selected chip, tonal button). */
val AccentSoft: Color get() = TapeDeckPaletteState.current.value.accentSoft

/** Content drawn ON accent fills (buttons/badges). */
val OnAccent: Color get() = TapeDeckPaletteState.current.value.onAccent

private val isDarkPalette: Boolean get() = TapeDeckPaletteState.current.value === DarkPalette

/** Status colors that stay readable on both themes. */
val StatusGreen: Color get() = if (isDarkPalette) Color(0xFF5BD38B) else Color(0xFF157A44)
val StatusRed: Color get() = if (isDarkPalette) Color(0xFFF87171) else Color(0xFFC0262D)

/** Delete and other destructive actions. */
val LedRed: Color get() = StatusRed
val LedGreen = Color(0xFF00E676)
val LedBlue = Color(0xFF448AFF)

val VuGreen = Color(0xFF76FF03)
val VuYellow = Color(0xFFFFEA00)
val VuRed = Color(0xFFFF1744)
