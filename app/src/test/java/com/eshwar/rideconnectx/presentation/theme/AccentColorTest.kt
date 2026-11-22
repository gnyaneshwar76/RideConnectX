package com.eshwar.rideconnectx.presentation.theme

import com.eshwar.rideconnectx.domain.model.AccentColor
import org.junit.Assert.assertEquals
import org.junit.Test

/** The saved palette name: new values round-trip, anything old reads as Soft teal. */
class AccentColorTest {
    @Test
    fun newValuesRoundTrip() {
        AccentColor.entries.forEach { assertEquals(it, AccentColor.fromName(it.name)) }
    }

    @Test
    fun oldOrMissingValuesBecomeTheDefault() {
        listOf("BLUE", "CYAN", "MAGENTA", "LIME", "AMBER", "", "junk", null).forEach {
            assertEquals(AccentColor.SOFT_TEAL, AccentColor.fromName(it))
        }
        assertEquals(AccentColor.SOFT_TEAL, AccentColor.BLUE)
    }
}
