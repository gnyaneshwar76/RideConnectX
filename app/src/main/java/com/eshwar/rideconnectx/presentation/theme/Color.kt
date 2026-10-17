package com.eshwar.rideconnectx.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Colour tokens taken directly from the Figma design (`App.tsx`, DARK / LIGHT palettes).
 * Do not hardcode colours in screens — always go through [RcxColors] via `Rcx.colors`.
 */

// ── Dark palette ───────────────────────────────────────────────────
val DarkBg = Color(0xFF070D1B)
val DarkCard = Color(0xFF0D1526)
val DarkCard2 = Color(0xFF111D35)
val DarkText = Color(0xFFE4ECFF)
val DarkMuted = Color(0xFF6B7FA0)
val DarkCyan = Color(0xFF00D4FF)
val DarkGreen = Color(0xFF00D9A3)
val DarkAmber = Color(0xFFFFB547)
val DarkRed = Color(0xFFFF5A6A)
val DarkBorder = Color(0x262B7FFF)   // rgba(43,127,255,0.15)
val DarkGlow = Color(0x4D2B7FFF)     // rgba(43,127,255,0.30)
val DarkOuterBg = Color(0xFF030810)

// ── Light palette ──────────────────────────────────────────────────
val LightBg = Color(0xFFEEF2FF)
val LightCard = Color(0xFFFFFFFF)
val LightCard2 = Color(0xFFF4F7FF)
val LightText = Color(0xFF0A1628)
val LightMuted = Color(0xFF5B6E88)
val LightCyan = Color(0xFF0095BB)
val LightGreen = Color(0xFF009E74)
val LightAmber = Color(0xFFC87D00)
val LightRed = Color(0xFFCC2238)
val LightBorder = Color(0x292B7FFF)  // rgba(43,127,255,0.16)
val LightGlow = Color(0x172B7FFF)    // rgba(43,127,255,0.09)
val LightOuterBg = Color(0xFFC8D8F4)

/** Primary accent — identical in both themes. */
val RcxBlue = Color(0xFF2B7FFF)

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
    cyan = DarkCyan,
    text = DarkText,
    muted = DarkMuted,
    green = DarkGreen,
    amber = DarkAmber,
    red = DarkRed,
    border = DarkBorder,
    glow = DarkGlow,
    outerBg = DarkOuterBg,
    isDark = true,
)

val RcxLightColors = RcxColors(
    bg = LightBg,
    card = LightCard,
    card2 = LightCard2,
    blue = RcxBlue,
    cyan = LightCyan,
    text = LightText,
    muted = LightMuted,
    green = LightGreen,
    amber = LightAmber,
    red = LightRed,
    border = LightBorder,
    glow = LightGlow,
    outerBg = LightOuterBg,
    isDark = false,
)
