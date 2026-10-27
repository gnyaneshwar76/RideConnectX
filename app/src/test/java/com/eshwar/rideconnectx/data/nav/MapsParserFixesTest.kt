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

    // ---- non-breaking spaces between number and unit (sample 11) ----

    @Test
    fun `live title with U+00A0 is a right turn in 1_4 km, remaining 3_6 km`() {
        val f = MapsNotificationParser.liveUpdateFields(
            "1.4\u00A0km · Turn right at Madina Cir to stay on NH 44", "", "Arrive 1:29 AM", 642, 4288,
            chipTexts = listOf("", "", "1.4\u00A0km"),
        )!!
        val m = MapsNotificationParser.parseLive(f)
        assertNotNull(m)
        assertEquals(ProtocolEngine.Maneuver.TURN_RIGHT, m!!.maneuverId)
        assertEquals("1.4 km", m.distanceToTurn)
        assertEquals(1400, MapsNotificationParser.distanceToMetres(m.distanceToTurn))
        assertEquals("3.6 km", m.remainingDistance)
    }

    @Test
    fun `old layout with U+00A0 and U+202F distances is read`() {
        val a = MapsNotificationParser.parse("350\u00A0m", "Turn left", "5 min · 2\u202Fkm · 1:00 PM ETA")!!
        assertEquals("350 m", a.distanceToTurn)
        assertEquals("2 km", a.remainingDistance)
        assertEquals(350, MapsNotificationParser.distanceToMetres("350\u202Fm"))
    }

    @Test
    fun `a U+00A0 chip distance is trusted, a bare metres shortCriticalText still is not`() {
        assertEquals("300\u00A0m", MapsNotificationParser.liveUpdateFields("Turn left", "", "Arrive 5:17 pm", 0, 5000, listOf("", "300\u00A0m", ""))!!.title)
        assertEquals("", MapsNotificationParser.liveUpdateFields("Turn left", "", "Arrive 5:17 pm", 0, 5000, listOf("", "", "5\u00A0m"))!!.title)
    }
}
