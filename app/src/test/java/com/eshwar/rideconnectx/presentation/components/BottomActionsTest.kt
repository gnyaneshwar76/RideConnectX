package com.eshwar.rideconnectx.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Layout choice of [BottomActions]: a row only for exactly 2 buttons, normal font, no wrapping. */
class BottomActionsTest {
    @Test
    fun rowOnlyForTwoNormalNoWrap() {
        assertEquals(true, rowFits(true, false, 2) { true })
        assertEquals(false, rowFits(true, false, 1) { true })
        assertEquals(false, rowFits(true, false, 3) { true })
        assertEquals(false, rowFits(true, true, 2) { true })   // font scale >= 1.3
        assertEquals(false, rowFits(false, false, 2) { true }) // caller opted out
        assertEquals(false, rowFits(true, false, 2) { false }) // a label wraps
    }

    @Test
    fun wrapCheckSkippedWhenNotNeeded() {
        var asked = false
        rowFits(true, true, 2) { asked = true; true }
        assertEquals(false, asked)
    }
}
