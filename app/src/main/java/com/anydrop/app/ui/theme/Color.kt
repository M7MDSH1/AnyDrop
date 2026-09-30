package com.anydrop.app.ui.theme

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/**
 * Theme palette. Dark mode is the original AnyDrop "tape deck at night" look; light mode is
 * the warm "paper tape" variant. The exported color vals below are getters backed by
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
    val accentBright: Color,    // (was AmberBright)
    val onAccent: Color,        // text/icons drawn on accent fills
    val hairline: Color         // 1dp card borders that separate panels from the page
)

// Warm charcoal instead of flat grey; panels stay "recessed" (darker than the page) like a deck.
internal val DarkPalette = TapeDeckPalette(
    surface = Color(0xFF12110F),
    background = Color(0xFF1B1916),
    surfaceVariant = Color(0xFF2B2722),
    outline = Color(0xFF3D372F),
    content = Color(0xFFEDE6DA),
    accent = Color(0xFFFFB000),
    accentDim = Color(0xFFCC8800),
    accentBright = Color(0xFFFFD740),
    onAccent = Color(0xFF14110C),
    hairline = Color(0xFF2A2621)
)

// Paper page with raised white cards. The accent is a deeper amber so amber text still reads
// on white (4.8:1), and text on amber fills is light for the same reason.
internal val LightPalette = TapeDeckPalette(
    surface = Color(0xFFFFFDF9),
    background = Color(0xFFF3EEE5),
    surfaceVariant = Color(0xFFE7DED0),
    outline = Color(0xFFD3C7B4),
    content = Color(0xFF1E1A15),
    accent = Color(0xFFA86200),
    accentDim = Color(0xFF8A5200),
    accentBright = Color(0xFFC97A00),
    onAccent = Color(0xFFFFFDF9),
    hairline = Color(0xFFE4DACA)
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

/** Content drawn ON amber fills (buttons/badges): dark on bright amber, light on deep amber. */
val OnAccent: Color get() = TapeDeckPaletteState.current.value.onAccent

val LedRed = Color(0xFFFF1744)
val LedGreen = Color(0xFF00E676)
val LedBlue = Color(0xFF448AFF)

val VuGreen = Color(0xFF76FF03)
val VuYellow = Color(0xFFFFEA00)
val VuRed = Color(0xFFFF1744)

private val isDarkPalette: Boolean get() = TapeDeckPaletteState.current.value === DarkPalette

/** Status colors that stay readable on both the dark deck and light paper. */
val StatusGreen: Color get() = if (isDarkPalette) Color(0xFF4ADE80) else Color(0xFF1E7B3E)
val StatusRed: Color get() = if (isDarkPalette) Color(0xFFFF6B6B) else Color(0xFFC62828)
