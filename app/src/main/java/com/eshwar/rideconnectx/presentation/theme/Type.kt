package com.eshwar.rideconnectx.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography from the design spec.
 *
 * The design uses three Google Fonts:
 *   - Outfit          → headings, wordmark
 *   - DM Sans         → body text, labels
 *   - JetBrains Mono  → data values, status codes
 *
 * The .ttf files are not bundled yet, so all three currently fall back to the
 * system font. To switch to the real fonts:
 *   1. Download the families from fonts.google.com
 *   2. Drop the .ttf files into `app/src/main/res/font/`
 *      (lowercase, underscores only — e.g. `outfit_bold.ttf`)
 *   3. Replace the three FontFamily declarations below, for example:
 *
 *        val Outfit = FontFamily(
 *            Font(R.font.outfit_regular, FontWeight.Normal),
 *            Font(R.font.outfit_medium,  FontWeight.Medium),
 *            Font(R.font.outfit_bold,    FontWeight.Bold),
 *        )
 *
 * Nothing else needs to change — every style below already points at these.
 */
val Outfit: FontFamily = FontFamily.Default
val DMSans: FontFamily = FontFamily.Default
val JetBrainsMono: FontFamily = FontFamily.Monospace

/**
 * Named text styles used by the screens. Prefer these over building
 * TextStyle inline, so the whole app stays consistent.
 */
object RcxType {

    /** "RideConnectX" wordmark — Outfit 700, 27sp. */
    val Wordmark = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 27.sp,
    )

    /** Big screen headline, e.g. Welcome screen — Outfit 700, 28sp. */
    val Headline = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
    )

    /** Screen title / app bar — Outfit 700, 22sp. */
    val Title = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
    )

    /** Section header inside a screen — Outfit 700, 18sp. */
    val Section = TextStyle(
        fontFamily = Outfit,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    )

    /** Standard body copy — DM Sans 400, 15sp. */
    val Body = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    )

    /** Smaller supporting text — DM Sans 400, 13sp. */
    val BodySmall = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    )

    /** Card label / list item title — DM Sans 600, 14sp. */
    val Label = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    )

    /** Button text — DM Sans 600, 16sp. */
    val Button = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    )

    /** Data readout, e.g. "42 km/h" — JetBrains Mono 500, 11sp. */
    val Mono = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
    )

    /** Tiny tracked caption, e.g. "SMART NAVIGATION" — JetBrains Mono 500, 9sp. */
    val MonoTiny = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        letterSpacing = 1.5.sp,
    )
}

/** Material3 Typography, so built-in M3 components pick up the right fonts. */
val RcxTypography = Typography(
    headlineLarge = RcxType.Headline,
    headlineMedium = RcxType.Title,
    titleLarge = RcxType.Section,
    titleMedium = RcxType.Label,
    bodyLarge = RcxType.Body,
    bodyMedium = RcxType.BodySmall,
    labelLarge = RcxType.Button,
    labelSmall = RcxType.Mono,
)
