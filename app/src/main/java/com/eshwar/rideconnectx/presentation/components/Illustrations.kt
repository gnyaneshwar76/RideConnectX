package com.eshwar.rideconnectx.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxColors
import com.eshwar.rideconnectx.presentation.theme.RcxType

/**
 * Hero illustrations from the design. In the Figma export these are inline SVGs
 * on a 355 × 260 viewBox with `preserveAspectRatio="xMidYMid slice"`, so the
 * helpers below map that same coordinate space onto the Compose canvas and
 * scale-to-cover, matching the web output.
 *
 * [WelcomeHero] and [ObIllustration] draw the photographs from
 * `res/drawable-nodpi/`. The vector versions they replaced are in git history.
 */

// ── 02 Welcome hero ────────────────────────────────────────────────

/**
 * The welcome photograph, with the design's fade and live-status chip on top.
 *
 * The image is drawn to fill rather than at its own 4:5 ratio: this slot takes
 * whatever height is left after the header and the bottom block, which is a
 * different shape on every phone. Cropping a scene is invisible; letterboxing
 * it inside a rounded card is not.
 */
@Composable
fun WelcomeHero(modifier: Modifier = Modifier) {
    val c = Rcx.colors
    val dark = c.isDark

    RcxPhotoFill(
        res = R.drawable.img_welcome_hero,
        modifier = modifier,
    ) {
        HeroFadeAndStatusChip(dark = dark, c = c)
    }
}

/**
 * The furniture that sits on the welcome hero, shared by the photograph and the
 * vector version so the two cannot drift apart: a fade into the screen
 * background, and the design's live-status chip.
 */
@Composable
private fun BoxScope.HeroFadeAndStatusChip(dark: Boolean, c: RcxColors) {
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(90.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, if (dark) Color(0xFF070D1B) else Color(0xFFEEF2FF))
                )
            )
    )

    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) Color(0xFF060C18).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f))
            .border(1.dp, c.blue.copy(alpha = 0.133f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(c.green)
        )
        Text(
            text = "Ride Connect · Access 125 · BLE Paired",
            style = RcxType.BodySmall.copy(fontSize = 11.sp),
            color = c.text,
            modifier = Modifier.weight(1f),
        )
        Text("LIVE", style = RcxType.Mono.copy(fontSize = 9.sp), color = c.green)
    }
}

// ── 03-05 Onboarding illustrations ─────────────────────────────────

enum class ObArt { Nav, Ble, Stats }

@DrawableRes
private fun ObArt.photo(): Int = when (this) {
    ObArt.Nav -> R.drawable.img_onboarding_navigation
    ObArt.Ble -> R.drawable.img_onboarding_bluetooth
    ObArt.Stats -> R.drawable.img_onboarding_intelligence
}

/**
 * Per-step onboarding photograph, fading into the screen background exactly as
 * the vector version did — the fade is what stops the image ending on a hard
 * line above the title.
 */
@Composable
fun ObIllustration(type: ObArt, modifier: Modifier = Modifier) {
    val dark = Rcx.colors.isDark

    RcxPhotoFill(
        res = type.photo(),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to if (dark) Color(0xFF070D1B) else Color(0xFFEEF2FF),
                    )
                )
        )
    }
}
