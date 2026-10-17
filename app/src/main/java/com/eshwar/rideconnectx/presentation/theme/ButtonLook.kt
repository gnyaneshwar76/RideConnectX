package com.eshwar.rideconnectx.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.eshwar.rideconnectx.domain.model.ButtonStyle

/**
 * How every main (filled) button looks: the gradient stops (the chosen colour
 * mix, softened, or the accent as before), the text colour that reads on them
 * at 4.5:1, and the rider's button style. Set once in [RcxTheme].
 */
data class ButtonLook(val fill: List<Color>, val text: Color, val style: ButtonStyle)

val LocalButtonLook = staticCompositionLocalOf {
    ButtonLook(listOf(RcxBlue, Color(0xFF1A56CC)), Color.White, ButtonStyle.SOLID)
}

/**
 * Fill for a main button in the rider's style. Solid: the soft gradient.
 * Glass: the mix's colours as a translucent tint with an accent rim (preview
 * `.glass`). Outline: accent border, clear fill.
 */
@Composable
fun Modifier.mainButtonFill(shape: Shape, pressed: Boolean = false): Modifier {
    val look = LocalButtonLook.current
    val c = Rcx.colors
    val base = this.clip(shape)
    return when (look.style) {
        ButtonStyle.SOLID -> base.background(Brush.linearGradient(look.fill))
            .then(if (isGlass) Modifier.glassSheen(glassTokens(GlassTier.LIGHT), pressed) else Modifier)
        ButtonStyle.GLASS -> base
            .background(
                Brush.linearGradient(
                    listOf(
                        look.fill.first().copy(alpha = if (pressed) 0.34f else 0.24f),
                        look.fill.last().copy(alpha = if (pressed) 0.22f else 0.14f),
                    ),
                ),
            )
            .glassSheen(glassTokens(GlassTier.LIGHT), pressed)
            .border(1.dp, c.blue.copy(alpha = 0.5f), shape)
        ButtonStyle.OUTLINE -> base
            .then(if (pressed) Modifier.background(c.blue.copy(alpha = 0.08f)) else Modifier)
            .border(1.5.dp, c.blue, shape)
    }
}

/** Text and icon colour on [mainButtonFill]. */
val mainButtonText: Color
    @Composable get() {
        val look = LocalButtonLook.current
        return when (look.style) {
            ButtonStyle.SOLID -> look.text
            ButtonStyle.GLASS -> Rcx.colors.text
            ButtonStyle.OUTLINE -> Rcx.colors.blue
        }
    }
