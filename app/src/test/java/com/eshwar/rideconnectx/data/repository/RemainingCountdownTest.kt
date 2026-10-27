package com.eshwar.rideconnectx.data.repository

import com.eshwar.rideconnectx.data.nav.MapsNotificationParser
import com.eshwar.rideconnectx.domain.ProtocolEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sequences taken from ride log 4 (17:15-17:24): titles and metres only. */
class RemainingCountdownTest {
    private fun c(jump: Int) = GpsCountdown({ a, b -> kotlin.math.abs(b.lat - a.lat) }, neverUp = true, jumpM = jump)
    private fun fix(m: Double, t: Long) = GpsCountdown.Fix(m, 0.0, 5f, 10f, t)

    private fun parseLive(title: String, progress: Int, max: Int): com.eshwar.rideconnectx.domain.model.NavManeuver {
        val f = MapsNotificationParser.liveUpdateFields(title, "", "Arrive 5:28 pm", progress, max)!!
        return MapsNotificationParser.parseLive(f)!!
    }

    @Test
    fun `both title forms carry remaining, a Head toward post carries no turn distance`() {
        val a = parseLive("450 m · Turn right onto Vayushakti Nagar Rd", 3600, 5000)
        val b = parseLive("Head toward Rushi School Rd", 3600, 5000)
        assertEquals("1.4 km", a.remainingDistance)
        assertEquals("1.4 km", b.remainingDistance)
        assertEquals("450 m", a.distanceToTurn)
        assertEquals(0, MapsNotificationParser.distanceToMetres(b.distanceToTurn) ?: 0)
    }

    @Test
    fun `Head toward post keeps the previous turn and takes only ETA and remaining`() {
        val a = parseLive("450 m · Turn right onto Vayushakti Nagar Rd", 3600, 5000)
        val b = parseLive("Head toward Rushi School Rd", 3650, 5000)
        val m = carryTurn(a, b)
        assertEquals(a.maneuverId, m.maneuverId)
        assertEquals("450 m", m.distanceToTurn)
        assertEquals(a.instruction, m.instruction)
        assertEquals("1.4 km", m.remainingDistance)
    }

    @Test
    fun `alternating posts (log 17-23-18 to 17-24-24) give one steady count and never up`() {
        val turn = c(150); val rem = c(1000)
        var t = 0L; var pos = 0.0
        turn.onFix(fix(0.0, 0)); rem.onFix(fix(0.0, 0))
        var prevTurn = Int.MAX_VALUE; var prevRem = Int.MAX_VALUE
        val packets = mutableListOf<String>()
        for (s in 0..60) {
            t = s * 1000L; pos = s * 10.0
            val f = fix(pos, t); turn.onFix(f); rem.onFix(f)
            if (s % 6 == 0) {                         // Maps post, progress stale by about 6 s
                val stale = 60
                if (s % 12 == 0) turn.onMaps("t", 700 - pos.toInt() + stale, t)  // title with distance
                // "Head toward" post: no turn update at all (carried); remaining only
                rem.onMaps(1, 1400 - pos.toInt() + stale, t)
            }
            val tr = turn.shown(t); val rm = rem.shown(t)
            assertTrue("turn went up at $s", tr <= prevTurn); assertTrue("remaining went up at $s", rm <= prevRem)
            prevTurn = tr; prevRem = rm
            packets += ProtocolEngine.formatDistance(tr) + "/" + ProtocolEngine.formatDistance(rm)
        }
        assertTrue(rem.shown(60_000) < 1000 + 100)
        assertFalse(packets.any { it.endsWith("0000M") })
    }

    @Test
    fun `Maps every 6 s, GPS every 1 s gives one new packet per second and metres fall steadily`() {
        val turn = c(150)
        turn.onFix(fix(0.0, 0)); turn.onMaps("t", 600, 0)
        val shown = mutableListOf<Int>()
        for (s in 1..30) {
            turn.onFix(fix(s * 10.0, s * 1000L))
            if (s % 6 == 0) turn.onMaps("t", 600 - (s - 6) * 10, s * 1000L)  // stale by 6 s = 60 m, higher than ours
            shown += turn.shown(s * 1000L)
        }
        assertEquals(30, shown.distinct().size)                     // a new value every second
        assertEquals(300, shown.last())
        assertTrue(shown.zipWithNext().all { (a, b) -> b < a })     // never up, not even on a Maps post
    }

    @Test
    fun `first route countdown 424 406 397 393 382 and a Maps post of unknown remaining does not reset`() {
        val rem = c(1000)
        rem.onMaps(1, 424, 0)
        for ((i, m) in listOf(406, 397, 393, 382).withIndex()) {
            rem.onMaps(1, m, (i + 1) * 1000L); assertEquals(m, rem.shown((i + 1) * 1000L))
        }
        // (the relay skips onMaps when remaining is 0; counting just goes on)
        assertEquals(382, rem.shown(6000))
    }

    @Test
    fun `cluster resolution - metres below 1 km, 100 m steps to 10 km, whole km above`() {
        assertEquals("0382M", ProtocolEngine.formatDistance(382))
        assertEquals("01.4K", ProtocolEngine.formatDistance(1400))
        assertEquals("01.5K", ProtocolEngine.formatDistance(1451))
        assertEquals("12K".padStart(5, '0'), ProtocolEngine.formatDistance(12_900).let { it.dropLast(1).padStart(4, '0') + "K" })
    }

    @Test
    fun `reroute re-anchors to the new route and a big jump counts as new route`() {
        val rem = c(1000)
        rem.onFix(fix(0.0, 0)); rem.onMaps(1, 5000, 0)
        rem.onMaps(2, 9000, 1000); assertEquals(9000, rem.shown(1000))   // new key = reroute
        rem.onMaps(2, 12_500, 2000); assertEquals(12_500, rem.shown(2000)) // > 1000 m higher
    }

    @Test
    fun `arriving counts down to zero and stays there`() {
        val t = c(150)
        t.onFix(fix(0.0, 0)); t.onMaps("a", 40, 0)
        t.onFix(fix(30.0, 3000)); t.onFix(fix(60.0, 6000))
        assertEquals(0, t.shown(6000))
        t.onMaps("a", 20, 6500)                    // stale higher post: stays
        assertEquals(0, t.shown(6500))
    }
}
