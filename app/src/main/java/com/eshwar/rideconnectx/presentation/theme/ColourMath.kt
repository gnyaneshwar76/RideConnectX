package com.eshwar.rideconnectx.presentation.theme

import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Pure colour helpers for the colour mixes and button text (no Compose, so
 * unit tests run them directly). Colours are 0xAARRGGBB longs.
 */

/** How much of the pure colour a mix keeps; the rest is the quiet slate (owner: "use soft colours"). */
const val SOFT_K = 0.35f

/** WCAG AA for normal text. */
const val MIN_CONTRAST = 4.5

const val BLACK = 0xFF000000L
const val WHITE = 0xFFFFFFFFL

private val SLATE = intArrayOf(0x8A, 0x9C, 0xB4)

private fun channel(argb: Long, n: Int): Int = ((argb shr (16 - 8 * n)) and 0xFF).toInt()

private fun rgb(r: Int, g: Int, b: Int): Long =
    BLACK or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()

/** Each channel = c*k + slate*(1-k), exactly as soft() in app-look-preview.html. */
fun soft(argb: Long, k: Float = SOFT_K): Long {
    val v = IntArray(3) { n -> (channel(argb, n) * k + SLATE[n] * (1 - k)).roundToInt().coerceIn(0, 255) }
    return rgb(v[0], v[1], v[2])
}

/** Moves [argb] a fraction [t] of the way to [target] (alpha forced opaque). */
fun mix(argb: Long, target: Long, t: Float): Long {
    val v = IntArray(3) { n -> (channel(argb, n) + (channel(target, n) - channel(argb, n)) * t).roundToInt().coerceIn(0, 255) }
    return rgb(v[0], v[1], v[2])
}

/** WCAG relative luminance. */
fun luminance(argb: Long): Double {
    fun lin(c: Int): Double { val s = c / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
    return 0.2126 * lin(channel(argb, 0)) + 0.7152 * lin(channel(argb, 1)) + 0.0722 * lin(channel(argb, 2))
}

fun contrast(a: Long, b: Long): Double {
    val la = luminance(a); val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** [fg] drawn with [alpha] over opaque [bg]. */
fun over(fg: Long, alpha: Float, bg: Long): Long = mix(bg, fg, alpha)

/** A filled button's stops and the text colour that reads on all of them. */
data class ButtonFill(val colours: List<Long>, val text: Long) {
    /** The weakest contrast of the text on any stop of the gradient. */
    val worstContrast: Double get() = colours.minOf { contrast(text, it) }
}

/**
 * Text is black or white, whichever reaches [MIN_CONTRAST] on every stop (the
 * stronger one if both do). If neither does, the fill is darkened in 5% steps
 * until white does.
 */
fun readableFill(colours: List<Long>): ButtonFill {
    var cols = colours
    repeat(40) {
        val black = cols.minOf { contrast(BLACK, it) }
        val white = cols.minOf { contrast(WHITE, it) }
        if (maxOf(black, white) >= MIN_CONTRAST) return ButtonFill(cols, if (black >= white) BLACK else WHITE)
        cols = cols.map { mix(it, BLACK, 0.05f) }
    }
    return ButtonFill(cols, WHITE) // unreachable: black stops give white 21:1
}

/** [fg] nudged toward black (light [bg]) or white (dark [bg]) until it reaches [MIN_CONTRAST] on [bg]. */
fun readableOn(fg: Long, bg: Long): Long {
    val target = if (luminance(bg) > 0.18) BLACK else WHITE
    var c = fg
    repeat(40) {
        if (contrast(c, bg) >= MIN_CONTRAST) return c
        c = mix(c, target, 0.05f)
    }
    return c
}

/**
 * Peak alpha of one Glow background light. The caps (0.18 dark, 0.16 light)
 * keep theme text at 4.5:1 with all three lights overlapping (ColourMathTest).
 */
fun glowAlpha(dark: Boolean, intensity: Float): Float =
    (if (dark) 0.08f else 0.06f) + 0.10f * intensity.coerceIn(0f, 1f)
