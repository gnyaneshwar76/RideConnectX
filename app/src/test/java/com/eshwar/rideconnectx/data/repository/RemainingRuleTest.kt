package com.eshwar.rideconnectx.data.repository

import com.eshwar.rideconnectx.data.nav.MapsNotificationParser
import com.eshwar.rideconnectx.domain.ProtocolEngine
import com.eshwar.rideconnectx.domain.model.NavManeuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** N4: the remaining-distance rule. Sequences are shaped after ride logs 4 and 5 (titles and metres only). */
class RemainingRuleTest {
    private val U = ProtocolEngine.REMAINING_UNKNOWN
    private fun rule() = RemainingRule { a, b -> kotlin.math.abs(b.lat - a.lat) }
    private fun fix(m: Double, t: Long) = GpsCountdown.Fix(m, 0.0, 5f, 10f, t)
    private fun field(p: ByteArray) = String(p, 18, 5, Charsets.ISO_8859_1)
    private fun isBlank(p: ByteArray) = (18..22).all { p[it] == 0xFF.toByte() }

    /** What the relay does with one Maps post: parse, feed the rule, ask the rule for the frame. */
    private class Sim(val rule: RemainingRule) {
        val packets = mutableListOf<ByteArray?>()
        var posts = 0; var postsWithoutValue = 0
        fun post(title: String, progress: Int, max: Int, nowMs: Long) {
            val f = MapsNotificationParser.liveUpdateFields(title, "", "Arrive 5:17 pm", progress, max)
            if (MapsNotificationParser.isStarting(title)) return
            val m: NavManeuver = (if (f != null) MapsNotificationParser.parse(f.title, f.text, f.subText) else null) ?: return
            posts++
            val own = MapsNotificationParser.distanceToMetres(m.remainingDistance) ?: -1
            if (own < 0) postsWithoutValue++
            rule.onMaps(own, nowMs)
            val rem = rule.frame(nowMs)
            packets += rem?.let { ProtocolEngine.buildNavigationPacket(1, 0, "0517PM", it) }
        }
    }

    @Test
    fun `alternating Head toward and turn posts both carry a real value, never blank`() {
        val sim = Sim(rule())
        sim.post("Head toward Rushi School Rd", 3600, 5000, 0)      // value
        for (s in 1..30) {
            // reconstructed: the log has no progress; here the turn post carries none, the Head post does
            sim.post("450 m · Turn right onto Vayushakti Nagar Rd", 0, 0, s * 1000L)
            sim.post("Head toward Rushi School Rd", 3600 + s * 5, 5000, s * 1000L)
        }
        assertTrue(sim.packets.all { it != null && !isBlank(it) })
        assertEquals(sim.packets.size, sim.packets.filterNotNull().size)
        assertEquals("01.4K", field(sim.packets.first()!!))
    }

    @Test
    fun `frozen 01-4K counts down while moving and never goes up`() {
        val r = rule()
        r.onFix(fix(0.0, 0)); r.onMaps(1400, 0)
        var prev = 1400
        for (s in 1..100) {                       // 100 s at 10 m/s, Maps silent (40 frozen packets in log 4)
            r.onFix(fix(s * 10.0, s * 1000L))
            val v = r.frame(s * 1000L)!!
            assertTrue(v <= prev); prev = v
        }
        assertEquals(400, prev)
        assertEquals("0400M", ProtocolEngine.formatDistance(prev))
    }

    @Test
    fun `first route 424 406 397 393 382 and posts without a value in between keep the count`() {
        val r = rule()
        for ((i, m) in listOf(424, 406, 397, 393, 382).withIndex()) {
            r.onMaps(m, i * 1000L); assertEquals(m, r.frame(i * 1000L))
            r.onMaps(-1, i * 1000L + 500)                       // a post with no figure
            assertEquals(m, r.frame(i * 1000L + 500))
        }
        assertEquals("0382M", ProtocolEngine.formatDistance(r.frame(4500)!!))
    }

    @Test
    fun `replay of a 3-route sequence has no blank packets (reconstructed data, 148 of 175 blank before)`() {
        val sim = Sim(rule())
        var t = 0L
        for (route in 1..3) {
            sim.rule.newRoute()
            sim.post("Head toward X", 1000, 6000 + route, t)                  // first post has the value
            for (i in 1..57) {                                                // reconstructed: turns carry no figure
                t += 1000
                if (i % 7 == 0) sim.post("Head toward X", 1000 + i * 8, 6000 + route, t)
                else sim.post("${600 - i * 10} m · Turn left", 0, 0, t)
            }
            t += 60_000
        }
        assertTrue("replay too short", sim.posts >= 170)
        assertTrue("most posts have no figure, like the log", sim.postsWithoutValue >= 140)
        assertEquals(0, sim.packets.count { it == null || isBlank(it) })
    }

    @Test
    fun `reroute re-anchors to Maps new value even when higher, a big jump does too`() {
        val r = rule()
        r.onFix(fix(0.0, 0)); r.onMaps(5000, 0)
        r.onMaps(5600, 1000)                      // stale-high, same route: never up
        assertTrue(r.frame(1000)!! <= 5000)
        r.reroute(); r.onMaps(5600, 2000)         // announced reroute: Maps' new value, higher
        assertEquals(5600, r.frame(2000))
        r.onMaps(7000, 3000)                      // 1400 m higher without announcement = new route
        assertEquals(7000, r.frame(3000))
    }

    @Test
    fun `a new route clears the carried value`() {
        val r = rule()
        r.onMaps(3000, 0); assertEquals(3000, r.shown(0))
        r.newRoute()
        assertEquals(U, r.shown(10)); assertNull(r.frame(10))   // held, not 3000
    }

    @Test
    fun `hold 3 s - value at 1 s is sent at once, none gives FF x5 at 3 s`() {
        val a = rule()
        assertNull(a.frame(0)); assertNull(a.frame(500))
        a.onMaps(1400, 1000); assertEquals(1400, a.frame(1000))
        val b = rule()
        assertNull(b.frame(0)); assertNull(b.frame(2999))
        assertEquals(U, b.frame(3000))
        assertTrue(isBlank(ProtocolEngine.buildNavigationPacket(1, 0, "0517PM", b.frame(3100)!!)))
    }

    @Test
    fun `a value at once is never held, a real zero is a number not unknown`() {
        val r = rule(); r.onMaps(2000, 0)
        assertEquals(2000, r.frame(0))
        val z = rule(); z.onMaps(0, 0)
        assertEquals(0, z.frame(0))
        assertEquals("0000M", field(ProtocolEngine.buildNavigationPacket(9, 0, "0517PM", z.frame(0)!!)))
    }

    @Test
    fun `Starting navigation and an invalid progress give no frame and no figure`() {
        assertTrue(MapsNotificationParser.isStarting("Starting navigation…"))
        val sim = Sim(rule()); sim.post("Starting navigation…", 40, 5000, 0)
        assertEquals(0, sim.posts)
        val f = MapsNotificationParser.liveUpdateFields("Head north", "", "Arrive 5:17 pm", 6000, 5000)!!
        assertFalse(f.subText.contains(" m") || f.subText.contains("km"))
    }
}
