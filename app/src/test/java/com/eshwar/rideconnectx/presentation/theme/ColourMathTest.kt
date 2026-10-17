package com.eshwar.rideconnectx.presentation.theme

import com.eshwar.rideconnectx.domain.model.AccentColor
import com.eshwar.rideconnectx.domain.model.ColourMix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** N48: soft colours, and 4.5:1 text on every button in both themes. */
class ColourMathTest {

    private val darkBg = 0xFF070D1BL
    private val lightBg = 0xFFEEF2FFL
    private val darkText = 0xFFE4ECFFL
    private val lightText = 0xFF0A1628L

    @Test
    fun softBlendsTowardSlateLikeThePreview() {
        // 0x00*.35+0x8A*.65=90, 0xD9*.35+0x9C*.65=177, 0xA3*.35+0xB4*.65=174
        assertEquals(0xFF5AB1AEL, soft(0xFF00D9A3L))
        assertEquals(0xFF8A9CB4L, soft(0xFF8A9CB4L))
        assertEquals(0xFFFF0000L, soft(0xFFFF0000L, k = 1f))
    }

    @Test
    fun contrastMatchesWcag() {
        assertEquals(21.0, contrast(BLACK, WHITE), 0.01)
        assertEquals(1.0, contrast(darkBg, darkBg), 0.001)
    }

    @Test
    fun everyMixIsReadableInBothThemes() {
        val rows = ColourMix.entries.map { mix ->
            val soft = mix.colours.map { soft(it) }
            val fill = readableFill(soft)
            // Solid: black or white on the soft gradient.
            assertTrue("${mix.label} solid ${fill.worstContrast}", fill.worstContrast >= MIN_CONTRAST)
            // Black check mark on the Appearance swatch (icons need 3:1).
            soft.forEach { assertTrue(mix.label, contrast(BLACK, it) >= 3.0) }
            val perTheme = listOf(darkBg to darkText, lightBg to lightText).map { (bg, text) ->
                // Accent (Outline text, links) on the page background.
                val accent = readableOn(soft.first(), bg)
                val accentRatio = contrast(accent, bg)
                assertTrue("${mix.label} accent $accentRatio", accentRatio >= MIN_CONTRAST)
                // Glass button: theme text on the tinted fill over the page.
                val glass = minOf(
                    contrast(text, over(fill.colours.first(), 0.24f, bg)),
                    contrast(text, over(fill.colours.last(), 0.14f, bg)),
                )
                assertTrue("${mix.label} glass $glass", glass >= MIN_CONTRAST)
                "accent %.2f glass %.2f".format(accentRatio, glass)
            }
            "%-10s solid %s %.2f | dark %s | light %s".format(
                mix.label, if (fill.text == BLACK) "black" else "white", fill.worstContrast, perTheme[0], perTheme[1],
            )
        }
        rows.forEach(::println)
    }

    @Test
    fun accentButtonsWithoutAMixAreReadable() {
        AccentColor.entries.forEach { a ->
            listOf(a.darkArgb, a.lightArgb).forEach { argb ->
                // CEO 10 Oct: the true accent, flat, as RcxTheme builds it; never darkened.
                val fill = readableFill(listOf(argb, argb))
                assertTrue("${a.label} ${fill.worstContrast}", fill.worstContrast >= MIN_CONTRAST)
                assertEquals("${a.label} darkened", listOf(argb, argb), fill.colours)
                println("%-8s %08X %s %.2f".format(a.label, argb, if (fill.text == BLACK) "black" else "white", fill.worstContrast))
            }
        }
        // Light accents take black text.
        assertEquals(BLACK, readableFill(listOf(AccentColor.AMBER.darkArgb, AccentColor.AMBER.darkArgb)).text)
        assertEquals(BLACK, readableFill(listOf(AccentColor.LIME.darkArgb, AccentColor.LIME.darkArgb)).text)
    }

    @Test
    fun anySingleColourReadsInBlackOrWhiteWithoutDarkening() {
        // Every 17th step of each channel: black or white always reaches 4.5:1 on one colour.
        for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
            val argb = BLACK or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
            assertEquals(listOf(argb), readableFill(listOf(argb)).colours)
        }
    }

    @Test
    fun glowKeepsThemeTextReadable() {
        // Worst case: all three Glow lights overlap at full intensity, in every
        // mix (soft) and every accent of each theme.
        val mixes = ColourMix.entries.flatMap { m -> m.colours.map { soft(it) } }
        val cases = listOf(
            Triple(true, darkBg, darkText) to mixes + AccentColor.entries.map { it.darkArgb },
            Triple(false, lightBg, lightText) to mixes + AccentColor.entries.map { it.lightArgb },
        )
        cases.forEach { (theme, pools) ->
            val (dark, bg, text) = theme
            val a = glowAlpha(dark, 1f)
            pools.forEach { pool ->
                val under = over(pool, a, over(pool, a, over(pool, a, bg)))
                val ratio = contrast(text, under)
                assertTrue("glow dark=$dark %08X %.2f".format(pool, ratio), ratio >= MIN_CONTRAST)
            }
        }
    }

    @Test
    // Literal floors, equal to fillFloor in Glass.kt (kept off Compose classes so this stays a plain JVM test).
    fun glassFloorsKeepTextReadable() {
        // Heavy: white photo pixel under the lightest photo dim (bg at 0.38).
        val photoBright = over(darkBg, 0.38f, WHITE)
        assertTrue(contrast(darkText, over(darkBg, 0.40f, photoBright)) >= MIN_CONTRAST)
        // Light tier: two overlapping light-field pools of the brightest accent.
        listOf(0xFFFFB547L, 0xFF9BE564L, 0xFF00D4FFL, 0xFFE4ECFFL).forEach { pool ->
            assertTrue(contrast(darkText, over(darkBg, 0.18f, over(pool, 0.5f, darkBg))) >= MIN_CONTRAST)
        }
        // Light theme: black photo pixel under the lightest dim.
        val photoDark = over(lightBg, 0.38f, BLACK)
        assertTrue(contrast(lightText, over(WHITE, 0.22f, photoDark)) >= MIN_CONTRAST)
    }

    @Test
    fun buttonTextIsTheBetterOfBlackAndWhiteOnTheRealFillInBothThemes() {
        // Mirrors RcxTheme: no mix = flat accent of THAT theme; mix = soft stops.
        val fills = listOf(true, false).flatMap { dark ->
            AccentColor.entries.map { a -> "${a.label} dark=$dark" to (if (dark) a.darkArgb else a.lightArgb).let { listOf(it, it) } } +
                ColourMix.entries.map { m -> "${m.label} dark=$dark" to m.colours.map { soft(it) } }
        }
        fills.forEach { (name, stops) ->
            val f = readableFill(stops)
            val black = f.colours.minOf { contrast(BLACK, it) }
            val white = f.colours.minOf { contrast(WHITE, it) }
            assertTrue("$name ${f.worstContrast}", f.worstContrast >= MIN_CONTRAST)
            assertEquals(name, maxOf(black, white), f.worstContrast, 1e-9)
        }
    }

    @Test
    fun mutedTextReadsOnBackgroundAndCard() {
        // Notification time uses readableOn(muted, card): plain dark muted on card is 4.48.
        listOf(0xFF6B7FA0L to listOf(darkBg, 0xFF0D1526L), 0xFF5B6E88L to listOf(lightBg, WHITE)).forEach { (m, bgs) ->
            bgs.forEach { bg -> val t = readableOn(m, bg); assertTrue("muted %08X on %08X %.2f".format(m, bg, contrast(t, bg)), contrast(t, bg) >= MIN_CONTRAST) }
        }
    }
}
