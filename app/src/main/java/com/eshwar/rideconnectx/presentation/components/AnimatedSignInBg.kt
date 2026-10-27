package com.eshwar.rideconnectx.presentation.components

import com.eshwar.rideconnectx.presentation.theme.rememberAmbientTransition
import com.eshwar.rideconnectx.presentation.theme.animateAmbient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * §4 of the Figma export of 18 August 2026 — the animated sign-in background.
 *
 * A direct port of `AnimatedSignInBg()` in `App.tsx`. Every number here — orb
 * positions, drift keyframes, durations, particle and star formulas — is copied
 * from that file rather than chosen. The export's design space is **390 × 780**,
 * so all coordinates are expressed in it and scaled to whatever the screen
 * actually is. Do not retune these by eye; change them in Figma and re-port.
 *
 * Three layers, exactly as specified:
 *
 *  1. three large colour orbs that drift on 7s / 9s / 11s loops,
 *  2. a faint diagonal sweep panning left→right every 8s,
 *  3. 22 rising particles and 48 stars, a sixth of which twinkle.
 *
 * Everything animates transform and opacity only, which is what keeps it on the
 * GPU. Unlike the glass surfaces, `Modifier.blur` is *correct* here: the orbs
 * are decoration with no content of their own, so blurring their own pixels is
 * exactly what is wanted.
 */

private const val DW = 390f   // design width
private const val DH = 780f   // design height

private data class Orb(
    val cx: Float, val cy: Float, val rx: Float, val ry: Float,
    val color: Color,
    val ax: List<Float>, val ay: List<Float>,
    val durationMs: Int, val blur: Float, val alpha: Float,
)

private val ORBS = listOf(
    Orb(290f, 180f, 160f, 140f, Color(0xFF2B7FFF),
        listOf(0f, -95f, 55f, -30f, 0f), listOf(0f, 60f, -80f, 40f, 0f), 7_000, 48f, 0.55f),
    Orb(90f, 400f, 130f, 120f, Color(0xFF00C2E0),
        listOf(0f, 80f, -50f, 30f, 0f), listOf(0f, -70f, 60f, -25f, 0f), 9_000, 42f, 0.48f),
    Orb(330f, 580f, 120f, 110f, Color(0xFF5B44FF),
        listOf(0f, -60f, 40f, -20f, 0f), listOf(0f, -55f, 70f, -30f, 0f), 11_000, 52f, 0.44f),
)

/** `times: [0, .28, .55, .78, 1]` from the export. */
private val ORB_TIMES = listOf(0f, 0.28f, 0.55f, 0.78f, 1f)

private data class Particle(
    val x: Float, val y0: Float, val durationMs: Int, val delayMs: Int,
    val r: Float, val color: Color, val alpha: Float,
)

private val PARTICLES: List<Particle> = List(22) { i ->
    Particle(
        x = ((i * 173 + 31) % 380).toFloat() + 5f,
        y0 = 680f + ((i * 47) % 120).toFloat(),
        durationMs = (5 + (i * 37) % 7) * 1000,
        delayMs = ((i * 29) % 8) * 1000,
        r = if (i % 5 == 0) 2f else if (i % 3 == 0) 1.4f else 0.9f,
        color = when (i % 3) {
            0 -> Color(0xFF00D4FF)
            1 -> Color(0xFF2B7FFF)
            else -> Color.White
        },
        alpha = 0.18f + (i % 4) * 0.12f,
    )
}

private data class Star(
    val x: Float, val y: Float, val r: Float, val alpha: Float, val twinkle: Boolean,
)

private val STARS: List<Star> = List(48) { i ->
    Star(
        x = ((i * 179 + 37) % 390).toFloat(),
        y = ((i * 89 + 19) % 760).toFloat(),
        r = if (i % 9 == 0) 1.2f else if (i % 4 == 0) 0.8f else 0.5f,
        alpha = 0.05f + (i % 5) * 0.05f,
        twinkle = i % 6 == 0,
    )
}

@Composable
fun AnimatedSignInBg(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var sizePx by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier
            .fillMaxSize()
            // near-black space base with slight indigo at the bottom
            .background(
                Brush.verticalGradient(
                    0.00f to Color(0xFF020610),
                    0.55f to Color(0xFF060416),
                    1.00f to Color(0xFF080318),
                )
            )
            .onSizeChanged { sizePx = Size(it.width.toFloat(), it.height.toFloat()) }
    ) {
        val transition = rememberAmbientTransition("signInAurora")

        // ── 1. the three drifting orbs ──────────────────────────────────
        ORBS.forEachIndexed { index, orb ->
            val dx = transition.animateAmbient(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = orb.durationMs
                        orb.ax.forEachIndexed { k, v ->
                            v at (ORB_TIMES[k] * orb.durationMs).toInt()
                        }
                    },
                ),
                label = "orbX$index",
            )
            val dy = transition.animateAmbient(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = orb.durationMs
                        orb.ay.forEachIndexed { k, v ->
                            v at (ORB_TIMES[k] * orb.durationMs).toInt()
                        }
                    },
                ),
                label = "orbY$index",
            )

            // Design-space units map onto the real screen.
            val sx = if (sizePx.width > 0f) sizePx.width / DW else 1f
            val sy = if (sizePx.height > 0f) sizePx.height / DH else 1f

            val wDp = with(density) { (orb.rx * 2 * sx).toDp() }
            val hDp = with(density) { (orb.ry * 2 * sy).toDp() }

            Box(
                Modifier
                    // Read in the placement lambda: a drift tick re-places the orb, it does not
                    // recompose the screen. Same Dp rounding as offset(x, y).
                    .offset {
                        IntOffset(
                            ((orb.cx - orb.rx + dx.value) * sx).toDp().roundToPx(),
                            ((orb.cy - orb.ry + dy.value) * sy).toDp().roundToPx(),
                        )
                    }
                    .size(width = wDp, height = hDp)
                    .blur(with(density) { (orb.blur * sx).toDp() })
                    .background(
                        // radial-gradient(ellipse at 38% 38%, col 0%, col44 40%, transparent 72%)
                        Brush.radialGradient(
                            0.00f to orb.color.copy(alpha = orb.alpha),
                            0.40f to orb.color.copy(alpha = orb.alpha * 0.27f),
                            0.72f to Color.Transparent,
                            center = Offset(0.38f, 0.38f),
                        )
                    )
            )
        }

        // ── 2. the diagonal sweep, panning left→right every 8s ──────────
        val sweep by transition.animateAmbient(
            initialValue = -DW,
            targetValue = DW,
            animationSpec = infiniteRepeatable(
                animation = tween(8_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "sweep",
        )

        // ── 3. particles and stars ──────────────────────────────────────
        val starPhase by transition.animateAmbient(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(6_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "starPhase",
        )
        val particlePhase by transition.animateAmbient(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(12_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "particlePhase",
        )

        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / DW
            val sy = size.height / DH

            // Faint diagonal sweep — one soft band travelling across.
            drawRect(
                brush = Brush.linearGradient(
                    0.20f to Color.Transparent,
                    0.50f to Color(0xFF2B7FFF).copy(alpha = 0.06f),
                    0.80f to Color.Transparent,
                    start = Offset(sweep * sx, 0f),
                    end = Offset((sweep + DW) * sx, size.height),
                ),
                size = size,
            )

            // Stars. A sixth twinkle on their own 2.5–4.5s cycle; the rest are
            // static, exactly as the export has them.
            STARS.forEachIndexed { i, s ->
                val alpha = if (s.twinkle) {
                    val period = 2.5f + (i % 3)
                    val delay = (i * 0.4f) % 3f
                    // triangle wave over the star's own period
                    val t = (((starPhase * 6f) + delay) % period) / period
                    val tri = if (t < 0.5f) t * 2f else (1f - t) * 2f
                    s.alpha + (s.alpha * 3f) * tri
                } else {
                    s.alpha
                }
                drawCircle(
                    color = Color.White.copy(alpha = alpha.coerceIn(0f, 1f)),
                    radius = s.r * sx,
                    center = Offset(s.x * sx, s.y * sy),
                )
            }

            // Rising motes: cy travels y0 → y0-820, opacity [0, op, op, 0]
            // across times [0, .12, .8, 1].
            PARTICLES.forEach { p ->
                val cycle = p.durationMs / 1000f
                val delay = p.delayMs / 1000f
                val t = (((particlePhase * 12f) - delay) % cycle + cycle) % cycle / cycle
                val cy = p.y0 - 820f * t
                val alpha = when {
                    t < 0.12f -> p.alpha * (t / 0.12f)
                    t < 0.80f -> p.alpha
                    else -> p.alpha * (1f - (t - 0.80f) / 0.20f)
                }
                drawCircle(
                    color = p.color.copy(alpha = alpha.coerceIn(0f, 1f)),
                    radius = p.r * sx,
                    center = Offset(p.x * sx, cy * sy),
                )
            }

            // Top-edge specular — the iPhone screen-edge catch-light, inset 8%.
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Color(0xFF78AAFF).copy(alpha = 0.22f),
                    1f to Color.Transparent,
                ),
                topLeft = Offset(size.width * 0.08f, 0f),
                size = Size(size.width * 0.84f, 1.dp.toPx()),
            )
        }
    }
}
