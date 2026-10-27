package com.eshwar.rideconnectx.data.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerWatchdogTest {
    private val gap = ListenerWatchdog.MIN_GAP_MS

    @Test fun rebindsWhenGrantedAndDownAndNeverAsked() =
        assertTrue(ListenerWatchdog.shouldRebind(true, false, 0L, 5L))

    @Test fun noRebindWithoutAccessOrWhenConnected() {
        assertFalse(ListenerWatchdog.shouldRebind(false, false, 0L, 5L))
        assertFalse(ListenerWatchdog.shouldRebind(true, true, 0L, 5L))
    }

    @Test fun rateLimited() {
        assertFalse(ListenerWatchdog.shouldRebind(true, false, 1_000L, 1_000L + gap - 1))
        assertTrue(ListenerWatchdog.shouldRebind(true, false, 1_000L, 1_000L + gap))
    }
}
