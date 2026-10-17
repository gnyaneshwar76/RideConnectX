package com.eshwar.rideconnectx.data.nav

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Log

/**
 * Identifies a Google Maps manoeuvre arrow by its shape.
 *
 * ### The problem this solves
 *
 * The arrow in the navigation notification is a **90x90 bitmap**, not a named
 * resource, so it cannot be identified by asking Android what it is called. But
 * it is the only field carrying the turn direction — the words say
 * `"Dammaiguda Rd  400 m"` and nothing about left or right.
 *
 * ### How
 *
 * Every arrow in [MapsArrowCatalog] is loaded from Maps' own package, rendered,
 * and reduced to a fingerprint. The live notification icon is reduced the same
 * way, then matched to the nearest catalogue entry.
 *
 * The reduction deliberately throws away almost everything:
 *
 * - **Alpha channel only.** Maps tints the arrow white on a coloured card, so
 *   the colour is noise and the shape lives entirely in the alpha.
 * - **16x16 on/off grid**, four pixels averaged per cell. Anti-aliasing and the
 *   difference between a 90x90 bitmap and a freshly rendered vector both move
 *   individual pixels; neither moves whole cells.
 *
 * ### Why nearest-match rather than equality
 *
 * Measured against five arrows captured from live notifications on 20 August
 * 2026: the correct entry was **0–7 cells different out of 256**, and the
 * nearest *wrong* entry was **43+**. Two of the five matched nothing exactly, so
 * equality would have failed them, while nearest-match got all five right with
 * an enormous margin. [MAX_DISTANCE] sits in that gap.
 *
 * Anything further away than that is reported as no match, and the caller falls
 * back to what the text said — an unrecognised arrow must never become a
 * confident wrong turn.
 */
class ArrowMatcher(private val context: Context) {

    companion object {
        private const val TAG = "RCX-Nav"

        /** Cells per side of the fingerprint grid. */
        const val GRID = 16

        /** Render size; each grid cell averages a 2x2 block of these pixels. */
        private const val RENDER = GRID * 2

        /**
         * Furthest a live arrow may sit from a catalogue entry and still count.
         *
         * Correct matches measured 0–7, wrong ones 43+. Twenty is comfortably
         * inside that gap in both directions: loose enough to absorb a Maps
         * redraw, tight enough that a genuinely new arrow is refused rather
         * than forced onto the closest old one.
         */
        const val MAX_DISTANCE = 20
    }

    /** One catalogue arrow: its name, its cluster code, and its fingerprint. */
    private class Entry(val name: String, val code: Int, val bits: BooleanArray)

    @Volatile
    private var catalogue: List<Entry>? = null

    /** What a live arrow turned out to be. */
    data class Match(val name: String, val code: Int, val distance: Int)

    /**
     * Renders Maps' arrows once and keeps them.
     *
     * Lazy because it needs Maps installed and costs ~67 small renders; doing it
     * at construction would run on whatever thread built the service, for a
     * rider who may never start navigation.
     */
    private fun catalogue(): List<Entry> {
        catalogue?.let { return it }
        synchronized(this) {
            catalogue?.let { return it }

            val res = runCatching {
                context.packageManager.getResourcesForApplication(MapsArrowCatalog.MAPS_PACKAGE)
            }.getOrNull()
            if (res == null) {
                Log.w(TAG, "Maps resources unavailable - arrow matching disabled")
                return emptyList<Entry>().also { catalogue = it }
            }

            val built = ArrayList<Entry>(MapsArrowCatalog.CODES.size)
            for ((name, code) in MapsArrowCatalog.CODES) {
                val id = runCatching {
                    res.getIdentifier(name, "drawable", MapsArrowCatalog.MAPS_PACKAGE)
                }.getOrDefault(0)
                if (id == 0) continue
                val drawable = runCatching {
                    androidx.core.content.res.ResourcesCompat.getDrawable(res, id, null)
                }.getOrNull() ?: continue
                val bits = runCatching { fingerprint(drawable) }.getOrNull() ?: continue
                built += Entry(name, code, bits)
            }
            Log.d(TAG, "Arrow catalogue built: ${built.size}/${MapsArrowCatalog.CODES.size}")
            catalogue = built
            return built
        }
    }

    /** Reduces a drawable to the 16x16 alpha grid. */
    fun fingerprint(drawable: Drawable): BooleanArray {
        val bmp = Bitmap.createBitmap(RENDER, RENDER, Bitmap.Config.ARGB_8888)
        Canvas(bmp).also {
            drawable.setBounds(0, 0, RENDER, RENDER)
            drawable.draw(it)
        }
        val px = IntArray(RENDER * RENDER)
        bmp.getPixels(px, 0, RENDER, 0, 0, RENDER, RENDER)
        bmp.recycle()

        val bits = BooleanArray(GRID * GRID)
        for (cy in 0 until GRID) for (cx in 0 until GRID) {
            var sum = 0
            for (dy in 0..1) for (dx in 0..1) {
                sum += px[(cy * 2 + dy) * RENDER + (cx * 2 + dx)] ushr 24
            }
            bits[cy * GRID + cx] = sum / 4 > 128
        }
        return bits
    }

    /**
     * The catalogue arrow this one is, or null if nothing is close enough.
     */
    fun match(live: BooleanArray): Match? {
        var best: Entry? = null
        var bestDistance = Int.MAX_VALUE
        for (entry in catalogue()) {
            var d = 0
            for (i in live.indices) {
                if (live[i] != entry.bits[i]) {
                    d++
                    if (d >= bestDistance) break
                }
            }
            if (d < bestDistance) {
                bestDistance = d
                best = entry
            }
        }
        val winner = best ?: return null
        if (bestDistance > MAX_DISTANCE) return null
        return Match(winner.name, winner.code, bestDistance)
    }

    /** Renders then matches, for a live notification icon. */
    fun match(drawable: Drawable): Match? =
        runCatching { match(fingerprint(drawable)) }.getOrNull()

    /** Human-readable fingerprint, for the ride log. */
    fun hex(bits: BooleanArray): String =
        bits.joinToString("") { if (it) "1" else "0" }
            .chunked(4)
            .joinToString("") { Integer.toHexString(it.toInt(2)) }
}
