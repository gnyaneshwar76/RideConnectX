package com.eshwar.rideconnectx.data.nav

import com.eshwar.rideconnectx.domain.ProtocolEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixes found with the real Maps 26.42 samples (job C3). */
class MapsParserFixesTest {

    // ---- empty title, turn in the text (Android 11/12) ----

    @Test
    fun `empty title with turn right is a right turn with unknown distance`() {
        val m = MapsNotificationParser.parse("", "Turn right", "11 min · 4.2 km · 12:29 AM ETA")
        assertNotNull(m)
        assertEquals(ProtocolEngine.Maneuver.TURN_RIGHT, m!!.maneuverId)
        assertEquals("", m.distanceToTurn)
        assertEquals("4.2 km", m.remainingDistance)
        assertEquals("1229AM", m.etaClock)
    }

    @Test
    fun `empty title with turn left and keep right are accepted`() {
        assertEquals(ProtocolEngine.Maneuver.TURN_LEFT, MapsNotificationParser.parse("", "Turn left", "5 min · 2 km · 1:00 PM ETA")!!.maneuverId)
        assertEquals(ProtocolEngine.Maneuver.KEEP_RIGHT, MapsNotificationParser.parse("", "Keep right", "5 min · 2 km · 1:00 PM ETA")!!.maneuverId)
    }

    @Test
    fun `empty title with a road name containing Left or Right is not a turn`() {
        assertNull(MapsNotificationParser.parse("", "Left Bank Rd", "5 min · 2 km · 1:00 PM ETA"))
        assertNull(MapsNotificationParser.parse("", "Right Side Colony / Main Rd", "5 min · 2 km · 1:00 PM ETA"))
        assertNull(MapsNotificationParser.parse("", "Hyderabad - Janagam Hwy / Koti Women's College Rd / Main Rd", "22 min · 4.9 km · 7:51 PM ETA"))
    }

    @Test
    fun `turn instruction check needs a maneuver verb first`() {
        assertTrue(MapsNotificationParser.isTurnInstruction("Turn right onto NH 44"))
        assertTrue(MapsNotificationParser.isTurnInstruction("Make a U-turn"))
        assertTrue(!MapsNotificationParser.isTurnInstruction("Turnberry Rd"))
        assertTrue(!MapsNotificationParser.isTurnInstruction("Left Bank Rd"))
    }
}
