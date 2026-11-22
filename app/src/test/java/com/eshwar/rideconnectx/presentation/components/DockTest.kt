package com.eshwar.rideconnectx.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DockTest {
    @Test fun tabUnderFinger() {
        assertEquals(0, dockTabAt(10f, 500f, 5))
        assertEquals(2, dockTabAt(250f, 500f, 5))
        assertEquals(4, dockTabAt(499f, 500f, 5))
    }

    @Test fun outsideAndEmptyAreClamped() {
        assertEquals(0, dockTabAt(-30f, 500f, 5))
        assertEquals(4, dockTabAt(900f, 500f, 5))
        assertEquals(0, dockTabAt(10f, 0f, 5))
    }
}
