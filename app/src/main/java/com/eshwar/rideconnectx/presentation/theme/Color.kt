package com.eshwar.rideconnectx.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Colour tokens taken directly from the Figma design (`App.tsx`, DARK / LIGHT palettes).
 * Do not hardcode colours in screens — always go through [RcxColors] via `Rcx.colors`.
 */

// Calm palette: neutral surfaces, ONE accent (the rider's choice), soft status colours.
// ── Dark palette ───────────────────────────────────────────────────
val DarkBg = Color(0xFF121315)
val DarkCard = Color(0xFF1C1E21)
val DarkCard2 = Color(0xFF232629)
val DarkText = Color(0xFFE8E9EB)
val DarkMuted = Color(0xFF9AA0A8)
val DarkGreen = Color(0xFF469F71)
val DarkAmber = Color(0xFFD9A441)
val DarkRed = Color(0xFFD9716B)
val DarkBorder = Color(0xFF2A2D31)
val DarkOuterBg = Color(0xFF0C0D0E)

// ── Light palette ──────────────────────────────────────────────────
val LightBg = Color(0xFFF5F6F8)
val LightCard = Color(0xFFFFFFFF)
val LightCard2 = Color(0xFFEEF0F3)
val LightText = Color(0xFF1C1E21)
val LightMuted = Color(0xFF6B7078)
val LightGreen = Color(0xFF327853)
val LightAmber = Color(0xFF866629)
val LightRed = Color(0xFFB44946)
val LightBorder = Color(0xFFE3E5E9)
val LightOuterBg = Color(0xFFE9EBEE)

/** Default accent (Calm blue). Theme.kt swaps in the rider's palette. */
val RcxBlue = Color(0xFF3B6FD4)

/**
 * The full set of colours a screen can use. Mirrors the `CT` type in the design.
 */
data class RcxColors(
    val bg: Color,
    val card: Color,
    val card2: Color,
    val blue: Color,
    val cyan: Color,
    val text: Color,
    val muted: Color,
    val green: Color,
    val amber: Color,
    val red: Color,
    val border: Color,
    val glow: Color,
    val outerBg: Color,
    val isDark: Boolean,
)

val RcxDarkColors = RcxColors(
    bg = DarkBg,
    card = DarkCard,
    card2 = DarkCard2,
    blue = RcxBlue,
    cyan = RcxBlue,
    text = DarkText,
    muted = DarkMuted,
    green = DarkGreen,
    amber = DarkAmber,
    red = DarkRed,
    border = DarkBorder,
    glow = RcxBlue.copy(alpha = 0.30f),
    outerBg = DarkOuterBg,
    isDark = true,
)

val RcxLightColors = RcxColors(
    bg = LightBg,
    card = LightCard,
    card2 = LightCard2,
    blue = RcxBlue,
    cyan = RcxBlue,
    text = LightText,
    muted = LightMuted,
    green = LightGreen,
    amber = LightAmber,
    red = LightRed,
    border = LightBorder,
    glow = RcxBlue.copy(alpha = 0.09f),
    outerBg = LightOuterBg,
    isDark = false,
)
