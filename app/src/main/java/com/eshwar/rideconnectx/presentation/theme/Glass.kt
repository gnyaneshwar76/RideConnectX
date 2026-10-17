package com.eshwar.rideconnectx.presentation.theme

import androidx.compose.ui.draw.blur
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The "Liquid Glass" surface, ported from the Figma export of 16 August 2026.
 *
 * Every number here comes from `GDK` / `GLT` in that file — Figma delivered the
 * token set as explicit values precisely so it could be typed in rather than
 * eyeballed. Do not adjust these by feel; change them in Figma and re-port.
 *
 * ## About the blur
 *
 * **Never use `Modifier.blur` for this.** It blurs the node's *own* content —
 * its text and icons — not what is behind it. Reaching for it produced exactly
 * that: frosted panels with their labels smeared away to nothing.
 *
 * No live backdrop blur either: per-surface blur cost too many frames. The
 * frosting is a smoked fill over a pre-blurred backdrop ([GlassLightField]).
 */

/** Which surface style the rider has chosen. Mirrors `StyleMode` in the export. */
enum class StyleMode { FLAT, GLASS }

/** Interaction state of a glass surface. Mirrors `GMode`. */
enum class GlassState { Default, Pressed, Disabled }

data class GlassTokens(
    val blur: Dp,
    val fill: Color,
    val fillAccent: Color,
    val fillPressed: Color,
    val fillDisabled: Color,
    /** Opaque stand-in for the blur on Android 11 and below. */
    val fallback: Color,
    val border: Color,
    val borderAccent: Color,
    val borderDisabled: Color,
    /** The bright hairline along the top edge — what actually sells glass. */
    val highlight: Color,
    /** Soft light pooling at the top of the surface. */
    val specular: Color,
    /** Hairline inner rim - `innerGlow` in the export. */
    val innerGlow: Color,
    /**
     * Lowest alpha [fill] may reach at any slider setting, so body text keeps
     * 4.5:1 on the brightest backdrop this tier sits on (N38, 10 Oct 2026).
     */
    val fillFloor: Float = 0f,
)

/**
 * Which glass tier a surface uses. Added in the export of 18 August 2026.
 *
 * One fill could not serve both jobs: a panel over a photograph has to stay
 * legible against anything, while a decorative panel over a controlled colour
 * field should let that colour through. Trying to do both with a single 58%
 * fill is what made the whole app look uniformly dim.
 */
enum class GlassTier { HEAVY, LIGHT }

/**
 * `GDK_H` - dark, **heavy**. Panels that must stay legible over photographs or
 * arbitrary backgrounds: Statistics tiles and period tabs, the navigation
 * overlay, sheet bodies.
 */
val GlassDarkHeavy = GlassTokens(
    blur = 20.dp,
    // N38 (owner: more transparency): 0.62 -> 0.45. Floor 0.40 = DarkText 4.5:1
    // over a white photo pixel under the lightest photo dim (bg 0.38).
    fill = Color(0x73070D1B),           // rgba(7,13,27,0.45), was 0.62
    fillAccent = Color(0xB82B7FFF),     // rgba(43,127,255,0.72)
    fillPressed = Color(0xB8070D1B),    // rgba(7,13,27,0.72)
    fillDisabled = Color(0x66070D1B),   // rgba(7,13,27,0.40)
    fallback = Color(0xFF0D1A30),
    border = Color(0x2EFFFFFF),         // rgba(255,255,255,0.18)
    borderAccent = Color(0xB32B7FFF),   // rgba(43,127,255,0.70)
    borderDisabled = Color(0x0FFFFFFF), // rgba(255,255,255,0.06)
    highlight = Color(0x4DFFFFFF),      // rgba(255,255,255,0.30)
    specular = Color(0x17FFFFFF),       // rgba(255,255,255,0.09)
    innerGlow = Color(0x17FFFFFF),      // rgba(255,255,255,0.09)
    fillFloor = 0.40f,
)

/**
 * `GDK_L` - dark, **light**. Decorative panels over controlled colour: the
 * Dashboard quick-action tiles, icon chips, the Appearance card. The colour
 * behind bleeds through and lights the tile from within, which is the point.
 */
val GlassDarkLight = GlassTokens(
    blur = 20.dp,
    // Smoked, not milky: a dark tint keeps small labels readable over the
    // coloured light, and the lit rim + gloss supply the "glass".
    // N38: 0.36 -> 0.22. Floor 0.18 = DarkText 4.5:1 over two overlapping
    // light-field pools (accent at 0.5 over the navy background).
    fill = Color(0x38070D1B),           // rgba(7,13,27,0.22), was 0.36 (before that white 0.09, read as grey slabs)
    fillAccent = Color(0x382B7FFF),     // rgba(43,127,255,0.22)
    fillPressed = Color(0x70070D1B),    // rgba(7,13,27,0.44)
    fillDisabled = Color(0x0AFFFFFF),   // rgba(255,255,255,0.04)
    fallback = Color(0xFF111E38),
    border = Color(0x3DFFFFFF),         // rgba(255,255,255,0.24)
    borderAccent = Color(0xA62B7FFF),   // rgba(43,127,255,0.65)
    borderDisabled = Color(0x0FFFFFFF), // rgba(255,255,255,0.06)
    highlight = Color(0x75FFFFFF),      // rgba(255,255,255,0.46)
    specular = Color(0x26FFFFFF),       // rgba(255,255,255,0.15)
    innerGlow = Color(0x21FFFFFF),      // rgba(255,255,255,0.13)
    fillFloor = 0.18f,
)

/** Back-compat alias - the export keeps `GDK` pointing at the light tier. */
val GlassDark = GlassDarkLight

/** `GLT` - light theme. Single tier: light theme always has controlled backgrounds. */
val GlassLight = GlassTokens(
    blur = 20.dp,
    // N38: 0.30 -> 0.18, but the floor holds it at 0.22 by default: LightText
    // 4.5:1 over a black photo pixel under the lightest photo dim (bg 0.38).
    fill = Color(0x2EFFFFFF),           // rgba(255,255,255,0.18), was 0.30
    fillAccent = Color(0x292B7FFF),     // rgba(43,127,255,0.16)
    fillPressed = Color(0x70FFFFFF),    // rgba(255,255,255,0.44)
    fillDisabled = Color(0x14FFFFFF),   // rgba(255,255,255,0.08)
    fallback = Color(0xFFD8E6F8),
    border = Color(0x94FFFFFF),         // rgba(255,255,255,0.58)
    borderAccent = Color(0x9E2B7FFF),   // rgba(43,127,255,0.62)
    borderDisabled = Color(0x29FFFFFF), // rgba(255,255,255,0.16)
    highlight = Color(0xB8FFFFFF),      // rgba(255,255,255,0.72)
    specular = Color(0x4DFFFFFF),       // rgba(255,255,255,0.30)
    innerGlow = Color(0x3DFFFFFF),      // rgba(255,255,255,0.24)
    fillFloor = 0.22f,
)

/**
 * The minimum scrim for white text over a photograph.
 *
 * From §2C of the brief: Figma checked this at 4.5:1 (WCAG AA) for 13sp/500
 * white text. It is the floor, not a suggestion — going lighter fails contrast
 * on the bright patches of a photo.
 */
val ScrimMin = Color(0x70070D1B)                    // rgba(7,13,27,0.44)
val ScrimGradientTop = Color(0x33070D1B)            // rgba(7,13,27,0.20)
val ScrimGradientBottom = Color(0x8A070D1B)         // rgba(7,13,27,0.54)

/** True where the platform can actually blur. */
val canBlur: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

val LocalStyleMode = staticCompositionLocalOf { StyleMode.FLAT }

/**
 * The rider's glass strength from Appearance, 0..1. Scales blur, tint, rim and
 * the background light together, so one slider moves the whole look.
 */
val LocalGlassIntensity = staticCompositionLocalOf { 0.6f }

/** [v] at the default intensity, scaled by the rider's choice (x0.35 .. x1.6). */
private fun Float.byIntensity(i: Float): Float = this * (0.35f + 0.65f * i / 0.6f)

val Rcx.glass: GlassTokens
    @Composable get() = glassTokens(GlassTier.LIGHT)

/**
 * The token set for a tier. Light theme has one tier, so the parameter only
 * matters in dark — which is where photographs and colour fields live.
 */
@Composable
fun glassTokens(tier: GlassTier): GlassTokens = when {
    !Rcx.colors.isDark -> GlassLight
    tier == GlassTier.HEAVY -> GlassDarkHeavy
    else -> GlassDarkLight
}

/**
 * Applies the glass surface to a [Modifier].
 *
 * Order matters and is deliberate: blur first (it samples what is already
 * behind), then the tinted fill on top of it, then the border. Drawing the
 * fill before the blur would blur the fill instead of the background.
 *
 * @param accent use the accent-tinted fill and border — for a selected segment
 *   or a primary action.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    state: GlassState = GlassState.Default,
    accent: Boolean = false,
    tier: GlassTier = GlassTier.LIGHT,
): Modifier {
    val g = glassTokens(tier)
    val k = LocalGlassIntensity.current

    val fill0 = when {
        state == GlassState.Disabled -> g.fillDisabled
        state == GlassState.Pressed -> g.fillPressed
        accent -> g.fillAccent
        else -> g.fill
    }
    val floor = if (fill0 == g.fill) g.fillFloor else 0f
    val fill = fill0.copy(alpha = fill0.alpha.byIntensity(k).coerceIn(floor, 0.9f))
    val borderColor0 = when {
        state == GlassState.Disabled -> g.borderDisabled
        accent -> g.borderAccent
        else -> g.border
    }
    val borderColor = borderColor0.copy(alpha = borderColor0.alpha.byIntensity(k).coerceAtMost(0.9f))

    // Read outside the effect block — that lambda is not composable.
    val base = if (Rcx.colors.isDark) g.fallback else Color.White

    return this
        .clip(shape)
        .then(
            // Performance: no live blur per card. Re-blurring an animated
            // backdrop for every card on every frame cost more than a phone
            // can give at 120 Hz. The backdrop is blurred once at its source
            // (GlassLightField), so a tinted pane over it reads as frosted
            // glass at a fraction of the cost - the same trick iOS uses.
            when {
                else -> Modifier.background(fill)
            }
        )
        .border(1.dp, rimBrush(borderColor, state == GlassState.Disabled), shape)
}

/**
 * The rim of a piece of glass is not one colour: light catches the top-left
 * edge, the sides almost vanish, and the bottom-right picks up a little bounce.
 * A flat 1dp outline in one colour is what made the old surfaces look like
 * grey boxes with a border drawn on.
 */
private fun rimBrush(base: Color, disabled: Boolean): Brush {
    if (disabled) return Brush.linearGradient(listOf(base, base))
    val strong = base.copy(alpha = (base.alpha * 1.9f).coerceAtMost(0.75f))
    return Brush.linearGradient(
        0f to strong,
        0.35f to base.copy(alpha = base.alpha * 0.35f),
        0.7f to base.copy(alpha = base.alpha * 0.2f),
        1f to base.copy(alpha = base.alpha * 0.8f),
    )
}

/**
 * The two details that separate "glass" from "a translucent box": a soft
 * specular pool at the top, and a bright hairline along the very top edge.
 *
 * Drawn as a child rather than in the modifier chain because both need to sit
 * above the fill and below the content.
 */
@Composable
fun BoxScope.GlassSheen(
    state: GlassState = GlassState.Default,
    tier: GlassTier = GlassTier.LIGHT,
) {
    if (state == GlassState.Disabled) return
    val g = glassTokens(tier)

    val pressed = state == GlassState.Pressed
    Box(
        Modifier
            .matchParentSize()
            .glassSheen(g, pressed)
    )
}

/**
 * Coloured light for glass to sit on.
 *
 * Glass over a flat dark background has nothing to bend or blur, so it reads as
 * grey plastic. This paints a few soft pools of the brand colours that drift
 * very slowly behind the glass surfaces. Only drawn in Glass mode.
 */
@Composable
fun GlassLightField(
    modifier: Modifier = Modifier,
    /** The page's photograph, shown full-width at the top and drifting slowly. */
    @DrawableRes photo: Int? = null,
    /** The page's colour; the light pools take its hue so the glass is clean, not muddy. */
    accent: Color? = null,
) {
    val c = Rcx.colors
    // x0.7: the pools were bright enough to tire the eyes on a dark phone.
    val k = 0.7f * (0.45f + 0.9f * LocalGlassIntensity.current).coerceAtMost(1.35f)
    // Throttled ambient clock (~30 fps, frozen in Battery Saver).
    val t by rememberBreath(14_000, "lightFieldDrift")
    val density = androidx.compose.ui.platform.LocalDensity.current
    val tilt = rememberTilt()
    Box(
        modifier
            .fillMaxSize()
    ) {
        if (photo != null) Box(Modifier.fillMaxWidth().fillMaxHeight(0.58f).clipToBounds()) {
            // Ken Burns: the picture breathes in and drifts sideways over
            // ~14 s, so the page feels alive without anything to watch.
            Image(
                painter = painterResource(photo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    // Transform first, blur inside: the blurred picture is
                    // rendered once and cached; each frame only moves it.
                    .graphicsLayer {
                        val s = 1.12f + 0.08f * t
                        scaleX = s; scaleY = s
                        // Against the tilt, so the photo sits behind the glass.
                        translationX = (t - 0.5f) * 28.dp.toPx() - tilt.value.x * 12.dp.toPx()
                        translationY = -tilt.value.y * 12.dp.toPx()
                    }
                    // The page's photo is atmosphere, not content: out of
                    // focus, which is also what makes the glass read as frost.
                    // N38: 14 -> 10 dp, a little sharper behind the clearer glass.
                    .blur(10.dp),
            )
            // Header stays readable at the top; the photo melts into the page below.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        // Owner found the photos too bright: a steady dim
                        // over the whole picture, heavier at the header.
                        0f to c.bg.copy(alpha = 0.70f),
                        0.3f to c.bg.copy(alpha = 0.38f),
                        0.65f to c.bg.copy(alpha = 0.55f),
                        1f to c.bg,
                    )
                )
            )
        }
        Box(Modifier.fillMaxSize().drawBehind {
                fun orb(color: Color, x: Float, y: Float, r: Float) = drawCircle(
                    Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(x, y), radius = r),
                    radius = r,
                    center = Offset(x, y),
                )
                val w = size.width
                val h = size.height
                // One hue family per page keeps the frosted glass clean; mixing
                // blue, cyan and green under a blur averages out to grey.
                val a = accent ?: c.blue
                val second = if (accent == null) c.cyan else lerp(accent, c.blue, 0.35f)
                val top = if (photo != null) 0.35f else 1f
                orb(a.copy(alpha = 0.38f * k * top), w * (0.85f - 0.15f * t), h * (0.12f + 0.05f * t), w * 0.75f)
                orb(second.copy(alpha = 0.24f * k), w * (0.10f + 0.12f * t), h * (0.45f - 0.04f * t), w * 0.65f)
                orb(a.copy(alpha = 0.20f * k), w * (0.78f + 0.08f * t), h * (0.72f + 0.04f * t), w * 0.6f)
                orb(second.copy(alpha = 0.18f * k), w * (0.25f - 0.08f * t), h * (0.94f - 0.03f * t), w * 0.6f)
            })
    }
}

/** Photo (default) or Glow behind the screens; provided by MainActivity. */
val LocalBackgroundStyle = staticCompositionLocalOf { com.eshwar.rideconnectx.domain.model.BackgroundStyle.PHOTO }

/** True when the rider chose the Glow background. */
val isGlow: Boolean
    @Composable get() = LocalBackgroundStyle.current == com.eshwar.rideconnectx.domain.model.BackgroundStyle.GLOW

/**
 * The Glow background ("Calm background" in app-look-preview.html): three
 * large soft lights in the rider's colour mix, or the accent, drifting very
 * slowly. Nothing else moves. Runs on [rememberBreath], so it ticks at about
 * 30 fps, stops when the app is not on screen and holds still in Battery Saver.
 */
@Composable
fun GlowField(modifier: Modifier = Modifier) {
    val cols = LocalButtonLook.current.fill
    val a = glowAlpha(Rcx.colors.isDark, LocalGlassIntensity.current)
    // One 0->1->0 sweep every ~78 s, as sin(t*0.08) in the preview.
    val t = rememberBreath(39_000, "glowDrift")
    Box(modifier.fillMaxSize().drawBehind {
        val w = size.width
        val h = size.height
        val s = (t.value - 0.5f) * 2f
        for (i in 0 until 3) {
            val dir = if (i % 2 == 0) 1f else -1f
            val cx = w * (0.2f + 0.6f * ((i * 0.37f) % 1f)) + dir * s * w * 0.12f
            val cy = h * (0.15f + 0.35f * i) - dir * s * h * 0.06f
            val r = w * 0.9f
            drawCircle(
                Brush.radialGradient(
                    listOf(cols[i % cols.size].copy(alpha = a), Color.Transparent),
                    center = Offset(cx, cy), radius = r,
                ),
                radius = r, center = Offset(cx, cy),
            )
        }
    })
}

/** Scrim for content sitting over a photograph. See [ScrimMin]. */
@Composable
fun Modifier.photoScrim(): Modifier = this.background(
    Brush.linearGradient(listOf(ScrimGradientTop, ScrimGradientBottom))
)

/** Gloss, specular pool and floor shade for a glass pane (see [GlassSheen]). */
fun Modifier.glassSheen(g: GlassTokens, pressed: Boolean = false): Modifier = drawBehind {
                // Gloss: the top of the pane catches light and it fades out
                // before the middle. Drawn inside the clipped shape, so it
                // follows the rounded corners instead of being a straight bar.
                drawRect(
                    Brush.verticalGradient(
                        0f to g.highlight.copy(alpha = g.highlight.alpha * if (pressed) 0.08f else 0.16f),
                        0.45f to Color.Transparent,
                    )
                )
                // Specular pool: a soft light near the top, a little left of
                // centre, sized to the surface. The old one used a fixed
                // 480px radius centred on the top-left pixel.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(g.specular.copy(alpha = g.specular.alpha * 0.5f), Color.Transparent),
                        center = Offset(size.width * 0.32f, 0f),
                        radius = size.maxDimension * 0.75f,
                    )
                )
                // A faint darker floor so the pane has thickness.
                drawRect(
                    Brush.verticalGradient(
                        0.6f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.12f),
                    )
                )
}

/**
 * A card background that follows the rider's style: flat card + hairline in
 * Flat, frosted glass with lit rim and gloss in Glass.
 */
@Composable
fun Modifier.cardSurface(shape: Shape): Modifier =
    if (LocalStyleMode.current == StyleMode.GLASS) {
        val g = glassTokens(GlassTier.LIGHT)
        this.glassSurface(shape).glassSheen(g)
    } else {
        val c = Rcx.colors
        this.background(c.card).border(1.dp, c.border, shape)
    }

/**
 * A screen's backdrop. Flat: the plain app background. Glass: the drifting
 * light field, which every glass card on the screen frosts.
 */
@Composable
fun ScreenBackdrop(
    modifier: Modifier = Modifier,
    @DrawableRes photo: Int? = null,
    accent: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Rcx.colors
    if (isGlow) {
        // Glow replaces the page photo and pools, in Flat and in Glass.
        Box(modifier.fillMaxSize().background(c.bg)) {
            GlowField()
            content()
        }
    } else if (LocalStyleMode.current == StyleMode.GLASS) {
        Box(modifier.fillMaxSize().background(c.bg)) {
            GlassLightField(photo = photo, accent = accent)
            content()
        }
    } else {
        Box(modifier.fillMaxSize().background(c.bg), content = content)
    }
}

/**
 * Card fill only, for cards that draw their own (e.g. state-coloured) border:
 * frosted glass with gloss in Glass mode, the flat card colour otherwise.
 * Put it after the card's clip.
 */
@Composable
fun Modifier.cardFill(): Modifier =
    if (LocalStyleMode.current == StyleMode.GLASS) {
        val g = glassTokens(GlassTier.LIGHT)
        val k = LocalGlassIntensity.current
        val fill = g.fill.copy(alpha = g.fill.alpha.byIntensity(k).coerceIn(g.fillFloor, 0.9f))
        val base = if (Rcx.colors.isDark) g.fallback else Color.White
        this.background(fill).glassSheen(g)
    } else {
        this.background(Rcx.colors.card)
    }

/**
 * Bottom-sheet colour: see-through in Glass mode so [GlassSheetWindow]'s blur
 * of the screen behind shows through; the normal card colour in Flat.
 */
@Composable
fun sheetContainerColor(): Color {
    val c = Rcx.colors
    if (LocalStyleMode.current != StyleMode.GLASS || !canBlur) return c.card
    val k = LocalGlassIntensity.current
    return c.card.copy(alpha = (0.92f - 0.42f * k).coerceIn(0.45f, 0.92f))
}

/**
 * Call first thing inside a bottom sheet or dialog. In Glass mode it asks
 * Android to blur everything behind that window (Android 12+, on phones that
 * support cross-window blur), which is what makes a sheet look like frosted
 * glass instead of a flat panel. Strength follows the intensity slider.
 */
@Composable
fun GlassSheetWindow() {
    if (LocalStyleMode.current != StyleMode.GLASS || !canBlur) return
    val view = LocalView.current
    val k = LocalGlassIntensity.current
    DisposableEffect(view, k) {
        var v: android.view.ViewParent? = view.parent
        var window: android.view.Window? = null
        while (v != null && window == null) {
            window = (v as? DialogWindowProvider)?.window
            v = v.parent
        }
        window?.let {
            it.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            // N38 (owner: too blurred): 12..40 px, was 18..88 (60 at the default 0.6).
            it.attributes = it.attributes.apply { blurBehindRadius = (12 + 28 * k).toInt().coerceAtMost(40) }
        }
        onDispose { }
    }
}

/** True when the rider has Glass on — for components that swap their look. */
val isGlass: Boolean
    @Composable get() = LocalStyleMode.current == StyleMode.GLASS

/**
 * A liquid-glass button: the backdrop frosted and tinted with [tint] (strong
 * for a main action, faint for a secondary one), a lit rim in the tint's
 * colour, and gloss that dims while pressed. Use only when [isGlass].
 */
@Composable
fun Modifier.glassButton(shape: Shape, tint: Color, strong: Boolean, pressed: Boolean = false): Modifier {
    val g = glassTokens(GlassTier.LIGHT)
    val k = LocalGlassIntensity.current
    val fillAlpha = (if (strong) 0.62f else 0.10f) * (if (pressed) 1.25f else 1f)
    val fill = tint.copy(alpha = fillAlpha.coerceAtMost(0.9f))
    val base = if (Rcx.colors.isDark) g.fallback else Color.White
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (strong) 0.55f else 0.40f),
        0.4f to tint.copy(alpha = 0.25f),
        1f to tint.copy(alpha = if (strong) 0.55f else 0.30f),
    )
    return this
        .clip(shape)
        .then(
            Modifier.background(fill)
        )
        .glassSheen(g, pressed)
        .border(1.dp, rim, shape)
}

/**
 * Fill for a row or chip that sits inside a card: a faint white veil in Glass
 * (Apple's inset-grouped look), the second card colour in Flat.
 */
@Composable
fun Modifier.innerFill(): Modifier =
    if (LocalStyleMode.current == StyleMode.GLASS) this.background(Color.White.copy(alpha = 0.06f))
    else this.background(Rcx.colors.card2)
