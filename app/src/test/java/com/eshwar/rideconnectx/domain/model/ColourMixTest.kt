package com.eshwar.rideconnectx.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColourMixTest {
    @Test fun twelveMixesWithTwoToFourColours() {
        assertEquals(12, ColourMix.entries.size)
        assertTrue(ColourMix.entries.all { it.colours.size in 2..4 })
        assertEquals(12, ColourMix.entries.map { it.label }.toSet().size)
    }

    @Test fun unknownNamesFallBackToDefault() {
        assertNull(ColourMix.fromName(null))
        assertNull(ColourMix.fromName("NOPE"))
        assertEquals(ColourMix.ICE, ColourMix.fromName("ICE"))
        assertEquals(ButtonStyle.SOLID, ButtonStyle.fromName("NOPE"))
        assertEquals(ButtonStyle.OUTLINE, ButtonStyle.fromName("OUTLINE"))
        assertNull(AppSettings().colourMix)
    }
}
