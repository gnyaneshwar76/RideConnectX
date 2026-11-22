package com.eshwar.rideconnectx.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.eshwar.rideconnectx.domain.model.AccentColor
import com.eshwar.rideconnectx.domain.model.ButtonStyle
import com.eshwar.rideconnectx.domain.model.ColourMix
import androidx.compose.ui.graphics.toArgb

/**
 * Provides the active [RcxColors] to the whole tree.
 * Read it from any composable with `Rcx.colors`.
 */
val LocalRcxColors = staticCompositionLocalOf { RcxDarkColors }

/**
 * Entry point for design tokens.
 *
 *     Text("Hello", color = Rcx.colors.text, style = RcxType.Body)
 */
object Rcx {
    val colors: RcxColors
        @Composable @ReadOnlyComposable get() = LocalRcxColors.current
}

private fun materialSchemeFrom(c: RcxColors) = if (c.isDark) {
    darkColorScheme(
        primary = c.blue,
        onPrimary = DarkText,
        secondary = c.cyan,
        background = c.bg,
        onBackground = c.text,
        surface = c.card,
        onSurface = c.text,
        surfaceVariant = c.card2,
        onSurfaceVariant = c.muted,
        error = c.red,
        outline = c.border,
    )
} else {
    lightColorScheme(
        primary = c.blue,
        onPrimary = LightCard,
        secondary = c.cyan,
        background = c.bg,
        onBackground = c.text,
        surface = c.card,
        onSurface = c.text,
        surfaceVariant = c.card2,
        onSurfaceVariant = c.muted,
        error = c.red,
        outline = c.border,
    )
}

/**
 * Wraps the app in the RideConnectX design system.
 *
 * @param darkTheme when true the DARK palette is used. Defaults to the system
 *        setting; Screen 22 (Appearance) passes the rider's saved preference.
 * @param fontScale multiplies the *existing* density font scale rather than
 *        replacing it, so the rider's Android accessibility text size is
 *        respected instead of being overridden — the design calls for
 *        "preserve accessibility scaling".
 */
@Composable
fun RcxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontScale: Float = 1f,
    accent: AccentColor = AccentColor.DEFAULT,
    /** Retired: loud gradients are no longer offered, so a mix saved by an old version is ignored. */
    @Suppress("UNUSED_PARAMETER") colourMix: ColourMix? = null,
    buttonStyle: ButtonStyle = ButtonStyle.SOLID,
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) RcxDarkColors else RcxLightColors
    // Accent as small text/icons must reach 4.5:1 on the surface it sits on, so it is nudged
    // only when it falls short. Buttons and swatches keep the exact palette colour.
    val surfaceArgb = (if (darkTheme) base.card else base.bg).toArgb().toLong() and 0xFFFFFFFFL
    val textAccent = Color(readableOn(accent.argb, surfaceArgb))
    val colors = base.copy(
        blue = textAccent,
        cyan = textAccent,
        glow = Color(accent.argb).copy(alpha = if (darkTheme) 0.30f else 0.09f),
    )
    val density = LocalDensity.current
    val scaled = Density(
        density = density.density,
        fontScale = density.fontScale * fontScale,
    )

    // No mix: the TRUE accent, flat (CEO 10 Oct). With one colour readableFill
    // only picks black or white text and never darkens it (any colour gives one
    // of the two 4.5:1; see ColourMathTest). The old dark-blue second stop is
    // what forced amber and lime to be darkened to brown.
    val fill = readableFill(listOf(accent.argb, accent.argb))
    val buttonLook = ButtonLook(fill.colours.map { Color(it) }, Color(fill.text), buttonStyle)

    CompositionLocalProvider(
        LocalRcxColors provides colors,
        LocalButtonLook provides buttonLook,
        LocalDensity provides scaled,
    ) {
        MaterialTheme(
            colorScheme = materialSchemeFrom(colors),
            typography = RcxTypography,
            content = content,
        )
    }
}
