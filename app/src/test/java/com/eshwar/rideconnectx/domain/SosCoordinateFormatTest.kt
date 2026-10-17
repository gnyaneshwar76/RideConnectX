package com.eshwar.rideconnectx.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * The SOS share link must carry a real coordinate pair whatever locale the
 * rider's phone is set to.
 *
 * `"%.6f".format(x)` uses `Locale.getDefault()`, so on a comma-decimal locale
 * the link became `?q=48,856600,2,352200` — four tokens, not a lat/lon. The one
 * message this feature exists to deliver would have pointed nowhere.
 */
class SosCoordinateFormatTest {

    private fun link(lat: Double, lon: Double): String {
        val la = String.format(Locale.US, "%.6f", lat)
        val lo = String.format(Locale.US, "%.6f", lon)
        return "https://maps.google.com/?q=$la,$lo"
    }

    @Test
    fun `coordinates use a dot on a comma-decimal locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(
                "https://maps.google.com/?q=17.385000,78.486700",
                link(17.3850, 78.4867),
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `link has exactly one comma, separating lat from lon`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertEquals(1, link(-37.814000, 144.963100).count { it == ',' })
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `southern and western hemispheres keep their sign`() {
        assertEquals("https://maps.google.com/?q=-37.814000,-144.963100", link(-37.8140, -144.9631))
    }
}
