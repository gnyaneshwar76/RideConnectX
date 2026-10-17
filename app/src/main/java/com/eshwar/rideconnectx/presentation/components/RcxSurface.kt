package com.eshwar.rideconnectx.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.eshwar.rideconnectx.presentation.theme.GlassSheen
import com.eshwar.rideconnectx.presentation.theme.GlassState
import com.eshwar.rideconnectx.presentation.theme.GlassTier
import com.eshwar.rideconnectx.presentation.theme.LocalStyleMode
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.StyleMode
import com.eshwar.rideconnectx.presentation.theme.glassSurface

/**
 * A card surface that follows the rider's chosen style.
 *
 * One component so the Appearance switch changes the whole app at once, rather
 * than every screen deciding for itself and half of them being missed.
 *
 * **The two styles are the same size.** Glass adds no padding and no extra
 * border width over flat, so switching never reflows a layout — which was the
 * requirement in the brief and the thing that makes the toggle safe to flip
 * while looking at any screen.
 *
 * @param accent tint the surface with the accent colour — for a selected tab or
 *   a primary action.
 */
@Composable
fun RcxSurface(
    modifier: Modifier = Modifier,
    corner: Int = 18,
    accent: Boolean = false,
    pressed: Boolean = false,
    enabled: Boolean = true,
    tier: GlassTier = GlassTier.LIGHT,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(corner.dp)
    val glass = LocalStyleMode.current == StyleMode.GLASS

    val state = when {
        !enabled -> GlassState.Disabled
        pressed -> GlassState.Pressed
        else -> GlassState.Default
    }

    Box(
        modifier.then(
            if (glass) {
                Modifier.glassSurface(shape, state, accent, tier)
            } else {
                Modifier
                    .clip(shape)
                    .background(if (accent) c.blue.copy(alpha = 0.10f) else c.card)
                    .border(
                        1.dp,
                        if (accent) c.blue.copy(alpha = 0.35f) else c.border,
                        shape,
                    )
            }
        )
    ) {
        if (glass) GlassSheen(state, tier)
        content()
    }
}
