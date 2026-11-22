package com.eshwar.rideconnectx.data.nav

import org.junit.Assert.assertEquals
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

    @Test fun periodicCallsAreRateLimitedToOnePerGap() {
        var last = 0L
        var asks = 0
        for (t in 30_000L..300_000L step 30_000L) {
            if (ListenerWatchdog.shouldRebind(true, false, last, t)) { asks++; last = t }
        }
        assertEquals(5, asks) // 30 s, 90 s, 150 s, 210 s, 270 s
    }

    @Test fun stopsAsksOnceConnectedAndResumesWhenDownAgain() {
        assertFalse(ListenerWatchdog.shouldRebind(true, true, 0L, 500_000L))
        assertTrue(ListenerWatchdog.shouldRebind(true, false, 100_000L, 500_000L))
    }

    @Test fun downSecondsCountsFromFirstSeenDown() {
        assertEquals(0L, ListenerWatchdog.downSeconds(0L, 90_000L))
        assertEquals(60L, ListenerWatchdog.downSeconds(30_000L, 90_999L))
        assertEquals(0L, ListenerWatchdog.downSeconds(90_000L, 80_000L))
    }
}
