package com.eshwar.rideconnectx.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsIdleTest {
    @Test fun slowsAfterTwoMinutesWithoutRealMove() {
        assertFalse(GpsIdle.shouldSlow(119_999, 0))
        assertTrue(GpsIdle.shouldSlow(120_000, 0))
    }

    @Test fun wakesOnRealMoveOrDopplerSpeed() {
        assertTrue(GpsIdle.wakes(true, null))
        assertTrue(GpsIdle.wakes(false, 2.5f))
        assertFalse(GpsIdle.wakes(false, 0.3f))
        assertFalse(GpsIdle.wakes(false, null))
    }

    @Test fun standingStillAtSlowRateAddsNothingAndDoesNotWake() {
        val a = GpsRideAccumulator()
        var woke = false
        for (i in 0 until 30) {   // one fix per 10 s, 9 m jitter, 20 m accuracy
            val t = 1_700_000_000_000L + i * 10_000L
            val m = if (i % 2 == 0) 0.0 else 9.0
            val moved = a.onFix(12.0 + m / 111_195.0, 77.0, 20f, 0f, t, t)
            if (GpsIdle.wakes(moved, 0f)) woke = true
        }
        assertEquals(0, a.metres)
        assertFalse(woke)
    }

    private class Fake : MotionTrigger {
        var armed = 0; var cancelled = 0; var cb: (() -> Unit)? = null
        override fun arm(onMotion: () -> Unit) { armed++; cb = onMotion }
        override fun cancel() { cancelled++; cb = null }
    }

    @Test fun slowArmsTriggerAndTriggerWakes() {
        val f = Fake(); val w = SlowWake(f); var fast = false
        w.enterSlow { fast = true }
        assertEquals(1, f.armed)
        f.cb!!.invoke()
        assertTrue(fast)
    }

    @Test fun leavingSlowOrDisconnectCancels() {
        val f = Fake(); val w = SlowWake(f)
        w.enterSlow {}
        w.leaveSlow()
        assertEquals(1, f.cancelled)
    }

    @Test fun noSensorKeepsTenSeconds() {
        var fast = false
        SlowWake(null).apply { enterSlow { fast = true }; leaveSlow() }
        assertFalse(fast)
        assertEquals(10_000L, GpsIdle.SLOW_MS)
    }
}
