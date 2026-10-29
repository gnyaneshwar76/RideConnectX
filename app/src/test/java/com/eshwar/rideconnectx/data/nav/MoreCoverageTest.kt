package com.eshwar.rideconnectx.data.nav

import com.eshwar.rideconnectx.data.repository.RemainingRule
import com.eshwar.rideconnectx.data.repository.UnknownTurnHold
import com.eshwar.rideconnectx.data.repository.carryTurn
import com.eshwar.rideconnectx.data.repository.keepsTurnRule
import com.eshwar.rideconnectx.domain.ProtocolEngine
import com.eshwar.rideconnectx.domain.model.NavManeuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Job C6: extra coverage for ProtocolEngine, the relay rules and the Maps parser. Metres only, no km styles. */
class MoreCoverageTest {

    private fun ascii(f: ByteArray, from: Int, to: Int) = String(f.copyOfRange(from, to), Charsets.US_ASCII)

    // ---- ProtocolEngine ----

    @Test
    fun `navigation frame envelope and checksum`() {
        val f = ProtocolEngine.buildNavigationPacket(ProtocolEngine.Maneuver.TURN_LEFT, 120, "0517PM", remainingMetres = 850)
        assertEquals(30, f.size)
        assertEquals(0xA5.toByte(), f[0])
        assertEquals(0x7F.toByte(), f[29])
        assertEquals(ProtocolEngine.Maneuver.TURN_LEFT.toByte(), f[2])
        assertEquals(ProtocolEngine.calculateChecksum(f), f[28])
        assertEquals("0120M", ascii(f, 4, 9))
        assertEquals("0850M", ascii(f, 18, 23))
    }

    @Test
    fun `real 0 remaining is 0000M, unknown is FF x5`() {
        val zero = ProtocolEngine.buildNavigationPacket(ProtocolEngine.Maneuver.DESTINATION, 0, "0517PM", remainingMetres = 0)
        assertEquals("0000M", ascii(zero, 18, 23))
        val unknown = ProtocolEngine.buildNavigationPacket(ProtocolEngine.Maneuver.STRAIGHT, 0, "0517PM")
        for (i in 18..22) assertEquals(0xFF.toByte(), unknown[i])
    }

    @Test
    fun `distance text replaces the turn distance and is always five characters`() {
        val reroute = ProtocolEngine.buildNavigationPacket(46, 300, "0517PM", distanceText = "----M")
        assertEquals("----M", ascii(reroute, 4, 9))
        val blank = ProtocolEngine.buildNavigationPacket(46, 300, "0517PM", distanceText = "     ")
        assertEquals("     ", ascii(blank, 4, 9))
        val long = ProtocolEngine.buildNavigationPacket(46, 300, "0517PM", distanceText = "1234567")
        assertEquals("12345", ascii(long, 4, 9))
    }

    @Test
    fun `same input gives identical bytes`() {
        val a = ProtocolEngine.buildNavigationPacket(ProtocolEngine.Maneuver.TURN_RIGHT, 400, "0129AM", remainingMetres = 3646)
        val b = ProtocolEngine.buildNavigationPacket(ProtocolEngine.Maneuver.TURN_RIGHT, 400, "0129AM", remainingMetres = 3646)
        assertTrue(a.contentEquals(b))
    }

    // ---- Maps parser ----

    @Test
    fun `empty title is a turn only when the text is a turn`() {
        assertEquals(ProtocolEngine.Maneuver.TURN_RIGHT, MapsNotificationParser.parse("", "Turn right", "11 min · 4.2 km · 12:29 AM ETA")!!.maneuverId)
        assertEquals(ProtocolEngine.Maneuver.SLIGHT_LEFT, MapsNotificationParser.parse("", "Slight left", "11 min · 4.2 km · 12:29 AM ETA")!!.maneuverId)
        assertNull(MapsNotificationParser.parse("", "Main Rd", "11 min · 4.2 km · 12:29 AM ETA"))
        assertNull(MapsNotificationParser.parse("", "", "11 min · 4.2 km · 12:29 AM ETA"))
    }

    @Test
    fun `non-breaking and narrow spaces in every distance field`() {
        assertEquals(1400, MapsNotificationParser.distanceToMetres("1.4 km"))
        assertEquals(1400, MapsNotificationParser.distanceToMetres("1.4 km"))
        assertEquals(80, MapsNotificationParser.distanceToMetres("80 m"))
        val m = MapsNotificationParser.parse("80 m", "Turn left", "3 min · 1.2 km · 9:20 PM ETA")!!
        assertEquals("80 m", m.distanceToTurn)
        assertEquals("1.2 km", m.remainingDistance)
    }

    @Test
    fun `km and m and other units convert to metres`() {
        assertEquals(400, MapsNotificationParser.distanceToMetres("400 m"))
        assertEquals(1200, MapsNotificationParser.distanceToMetres("1.2 km"))
        assertEquals(1200, MapsNotificationParser.distanceToMetres("1,2 km"))
        assertEquals(15000, MapsNotificationParser.distanceToMetres("15 km"))
        assertEquals(804, MapsNotificationParser.distanceToMetres("0.5 mi"))
        assertEquals(76, MapsNotificationParser.distanceToMetres("250 ft"))
        assertNull(MapsNotificationParser.distanceToMetres("Turn left"))
    }

    @Test
    fun `Arrive time in 12 and 24 hour forms`() {
        assertEquals("0132AM", MapsNotificationParser.parseEtaClock("Arrive 1:32 AM"))
        assertEquals("1205AM", MapsNotificationParser.parseEtaClock("Arrive 12:05 am"))
        assertEquals("0253PM", MapsNotificationParser.parseEtaClock("Arrive 14:53"))
        assertEquals("1221AM", MapsNotificationParser.parseEtaClock("14 min · 5.3 km · 12:21 AM ETA"))
        assertNull(MapsNotificationParser.parseEtaClock("Arrive "))
    }

    private fun remaining(progress: Int, max: Int) =
        MapsNotificationParser.liveUpdateFields("Turn left", "", "Arrive 1:32 AM", progress, max)!!.let {
            MapsNotificationParser.parseLive(it)!!.remainingDistance
        }

    @Test
    fun `remaining is progressMax minus progress`() {
        assertEquals("3.6 km", remaining(642, 4288))     // 3646 m
        assertEquals("5.9 km", remaining(0, 5895))
        assertEquals("999 m", remaining(1, 1000))
        assertEquals("1.0 km", remaining(0, 1000))
        assertEquals("0 m", remaining(4288, 4288))
    }

    @Test
    fun `progress past the end or negative gives no remaining`() {
        assertEquals("", remaining(5000, 4288))
        assertEquals("", remaining(-1, 4288))
    }

    // ---- NavigationRelay rules ----

    private fun nav(code: Int, distance: String, remaining: String = "", eta: String? = null) =
        NavManeuver(maneuverId = code, instruction = "x", distanceToTurn = distance, remainingDistance = remaining, etaClock = eta)

    @Test
    fun `carried turn keeps the old arrow and distance, takes the new ETA and remaining`() {
        val prev = nav(ProtocolEngine.Maneuver.TURN_LEFT, "120 m", "5.0 km", "0517PM")
        val c = carryTurn(prev, nav(ProtocolEngine.Maneuver.STRAIGHT, "", "4.8 km", "0518PM"))
        assertEquals(ProtocolEngine.Maneuver.TURN_LEFT, c.maneuverId)
        assertEquals("120 m", c.distanceToTurn)
        assertEquals("4.8 km", c.remainingDistance)
        assertEquals("0518PM", c.etaClock)
        // A post with no remaining or clock keeps the old ones.
        val k = carryTurn(prev, nav(ProtocolEngine.Maneuver.STRAIGHT, ""))
        assertEquals("5.0 km", k.remainingDistance)
        assertEquals("0517PM", k.etaClock)
    }

    @Test
    fun `turn keep rule edge cases`() {
        assertTrue(keepsTurnRule(prevMetres = 120, freshMetres = 0, freshKnown = true, freshArrival = false))   // "0 m" repeat
    }

    @Test
    fun `unknown turn hold resets with a new route`() {
        val h = UnknownTurnHold()
        assertTrue(!h.mayShow(known = false, nowMs = 0))
        h.reset()
        assertTrue(!h.mayShow(known = false, nowMs = 2_900))   // the wait starts again
        assertTrue(h.mayShow(known = false, nowMs = 5_900))
    }

    @Test
    fun `remaining rule holds 3 s with nothing real, then sends unknown`() {
        val r = RemainingRule { _, _ -> 0.0 }
        assertNull(r.frame(0))
        assertNull(r.frame(2_999))
        assertEquals(ProtocolEngine.REMAINING_UNKNOWN, r.frame(3_000))
        r.onMaps(4288, 3_100)
        assertNotNull(r.frame(3_100))
        assertEquals(4288, r.frame(3_100))
    }
}
