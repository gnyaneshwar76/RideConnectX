package com.eshwar.rideconnectx.presentation.components

import com.eshwar.rideconnectx.presentation.theme.innerFill
import com.eshwar.rideconnectx.presentation.theme.isGlass
import androidx.compose.foundation.layout.Spacer
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eshwar.rideconnectx.presentation.theme.Rcx

/**
 * A photograph in one of the app's image slots.
 *
 * The photographs are dark by design so they sit inside the theme, but "dark"
 * is not the same as "safe to put text on" — a headlight or a streetlight in
 * the wrong place will swallow a caption. Every slot that carries text over a
 * photo therefore gets a [scrim]: a vertical gradient from transparent at the
 * top to the screen background at the bottom, which both guarantees contrast
 * and blends the image into the surface below it rather than ending on a hard
 * edge.
 *
 * @param res the WebP in `res/drawable-nodpi/`, produced by
 *   `tools/images/prepare_scenes.py` at the ratio its slot expects.
 * @param ratio width ÷ height. Pass the slot's ratio, not the file's — they
 *   match today, and if a photo is ever regenerated at a different shape the
 *   layout should stay put and the image crop, not the other way round.
 * @param scrim how much of the height the bottom fade covers, 0f for none.
 * @param overlay drawn on top of the photo and the scrim.
 */
@Composable
fun RcxPhoto(
    @DrawableRes res: Int,
    ratio: Float,
    modifier: Modifier = Modifier,
    scrim: Float = 0f,
    scrimColor: Color? = null,
    contentScale: ContentScale = ContentScale.Crop,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val c = Rcx.colors
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            // Shows through while the bitmap decodes, and behind any photo whose
            // ratio does not fill the box exactly.
            .innerFill(),
    ) {
        Image(
            painter = painterResource(res),
            contentDescription = null,          // decorative; the text says it
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
        )
        if (scrim > 0f) {
            val end = scrimColor ?: c.bg
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            (1f - scrim).coerceIn(0f, 1f) to Color.Transparent,
                            1f to end,
                        )
                    )
            )
        }
        overlay()
    }
}

/**
 * The header band that opens a feature screen: a photograph at a fixed height
 * with the screen's own background fading up through its lower third.
 *
 * Fixed height rather than the image's 16:9, because 16:9 across a phone is
 * roughly 200 dp and these screens all have a card immediately underneath. The
 * fade is what matters visually — it stops the band reading as a picture
 * pasted onto the list and makes it the top of the screen instead.
 */
@Composable
fun RcxHeroBanner(
    @DrawableRes res: Int,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    corner: Dp = 20.dp,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val c = Rcx.colors
    // In Glass the page's photo fills the top of the screen behind everything
    // (ScreenBackdrop), so the banner leaves its space open to show it.
    if (isGlass) {
        Spacer(modifier.fillMaxWidth().height(4.dp))
        return
    }
    RcxPhotoFill(
        res = res,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(corner)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to c.bg,
                    )
                )
        )
        overlay()
    }
}

/**
 * A photograph filling whatever box it is given, rather than one sized by its
 * own ratio — for full-bleed backgrounds and for fixed-height bands.
 *
 * [darken] flattens the whole image towards the background colour. The
 * navigation background needs it: white maneuver text sits directly on top of
 * city lights, and dimming the picture is what keeps that readable.
 */
@Composable
fun RcxPhotoFill(
    @DrawableRes res: Int,
    modifier: Modifier = Modifier,
    darken: Float = 0f,
    darkenColor: Color? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val c = Rcx.colors
    Box(modifier.innerFill()) {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (darken > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background((darkenColor ?: c.bg).copy(alpha = darken.coerceIn(0f, 1f)))
            )
        }
        overlay()
    }
}
