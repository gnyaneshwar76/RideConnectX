package com.eshwar.rideconnectx.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsRideAccumulatorTest {
    private val t0 = 1_700_000_000_000L
    private val lon = 77.0
    private fun lat(metresNorth: Double) = 12.0 + metresNorth / 111_195.0

    /** A fix [i] seconds after t0, [m] metres north of the start. */
    private fun GpsRideAccumulator.fix(i: Int, m: Double, acc: Float? = 5f, speed: Float? = null, ageMs: Long = 0) =
        onFix(lat(m), lon, acc, speed, t0 + i * 1000L, t0 + i * 1000L + ageMs)

    /** [n] fixes 10 m apart, 1 s apart (36 km/h), the first one being the anchor. */
    private fun GpsRideAccumulator.ride(n: Int, from: Int = 0, startM: Double = 0.0) {
        for (k in 0 until n) fix(from + k, startM + k * 10.0)
    }

    @Test fun standingStillWithJitterAddsNothing() {
        val a = GpsRideAccumulator()
        // reported accuracy 20 m: jitter up to 9 m is under accuracy/2
        for (i in 0 until 60) a.fix(i, if (i % 2 == 0) 0.0 else 9.0, acc = 20f)
        assertEquals(0, a.metres)
        // accuracy 5 m, jitter 15 m, but the Doppler speed says stopped
        val b = GpsRideAccumulator()
        for (i in 0 until 60) b.fix(i, if (i % 2 == 0) 0.0 else 15.0, acc = 5f, speed = 0f)
        assertEquals(0, b.metres)
    }

    @Test fun badFixesAreDropped() {
        val a = GpsRideAccumulator()
        a.fix(0, 0.0)
        assertFalse(a.fix(1, 50.0, acc = 25f))
        assertFalse(a.fix(2, 50.0, acc = null))
        assertFalse(a.fix(3, 50.0, ageMs = 11_000))
        assertFalse(a.fix(0, 50.0))             // not after the last fix
        assertEquals(0, a.metres)
        assertTrue(a.fix(4, 50.0))
    }

    @Test fun teleportIsDropped() {
        val a = GpsRideAccumulator()
        a.fix(0, 0.0)
        assertFalse(a.fix(1, 5_000.0))
        assertEquals(0, a.metres)
        assertTrue(a.fix(2, 10.0))
        assertEquals(10, a.metres)
    }

    @Test fun threeMetreRule() {
        val a = GpsRideAccumulator()
        a.fix(0, 0.0, acc = 3f)
        assertFalse(a.fix(1, 2.5, acc = 3f))
        assertEquals(0, a.metres)
        assertTrue(a.fix(2, 3.6, acc = 3f))      // anchor stayed at 0, so this is 3.6 m
        assertEquals(4, a.metres)
    }

    @Test fun maxSpeedIgnoresOneSpike() {
        val a = GpsRideAccumulator()
        var m = 0.0
        a.fix(0, m)
        for (i in 1..20) {
            m += if (i == 10) 28.0 else 10.0     // 28 m in 1 s is about 100 km/h
            a.fix(i, m)
        }
        assertEquals(36, a.maxKmh)
    }

    @Test fun avgIsDistanceOverDuration() {
        val a = GpsRideAccumulator()
        a.ride(101)                              // 100 moves of 10 m; the first fix only anchors
        val r = a.finish("o")
        assertNotNull(r)
        assertEquals(1000, r!!.distanceMeters)
        assertEquals(100_000L, r.durationMillis)  // start of first move to last move
        assertEquals(36, r.avgSpeedKmh)
        assertEquals(r.startedAt + 100_000L, r.endedAt)
        assertEquals(r.endedAt + 30L * 24 * 3600 * 1000, r.expiresAt)
        assertTrue(r.route.isNotEmpty())
    }

    @Test fun liveAvgMatchesSpeedAndMaxNeverBelowAvg() {
        val a = GpsRideAccumulator()
        a.ride(4)                                // 3 moves of 10 m at 1 s: steady 36 km/h
        val l = a.live(t0 + 3000L)
        assertEquals(36, l.avgKmh)               // was 54: first segment's distance without its time
        assertTrue(l.maxKmh >= l.avgKmh)
    }

    @Test fun shortRideIsDiscarded() {
        val a = GpsRideAccumulator()
        a.ride(16)                               // 150 m
        assertNull(a.finish("o"))
        assertEquals(0, a.metres)
    }

    @Test fun draftRestoreContinues() {
        val a = GpsRideAccumulator()
        a.ride(31)                               // 300 m
        val b = GpsRideAccumulator().apply { restore(a.draft()) }
        assertEquals(300, b.metres)
        b.ride(11, from = 40, startM = 5_000.0)  // first fix anchors, then 10 moves
        assertEquals(400, b.metres)
        assertNotNull(b.finish("o"))
    }

    @Test fun reconnectGapIsNotCounted() {
        val a = GpsRideAccumulator()
        a.ride(31)
        a.breakTrack()
        a.fix(100, 8_000.0)                      // far away after the gap: new anchor only
        assertEquals(300, a.metres)
        a.fix(101, 8_010.0)
        assertEquals(310, a.metres)
    }

    @Test fun liveShowsZeroSpeedWhenStill() {
        val a = GpsRideAccumulator()
        a.ride(11)
        val moving = a.live(t0 + 10_000)
        assertEquals(36, moving.speedKmh)
        assertTrue(moving.gpsOk)
        assertEquals(0, a.live(t0 + 25_000).speedKmh)
        assertFalse(a.live(t0 + 25_000).gpsOk)
    }

    @Test fun reAnchorAfterTeleportsDropsTheOldRoute() {
        val a = GpsRideAccumulator()
        a.ride(5)
        for (i in 0 until 5) a.fix(10 + i, 500_000.0)   // 5 jumps in a row: start over from here
        a.fix(16, 500_010.0)
        val route = a.live(t0 + 16_000).route
        assertTrue(route.all { it.lat > lat(400_000.0) })
        assertTrue(a.metres < 100)
    }
}
