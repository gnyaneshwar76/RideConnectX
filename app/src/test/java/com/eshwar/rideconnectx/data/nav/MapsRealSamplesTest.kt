package com.eshwar.rideconnectx.data.nav

import com.eshwar.rideconnectx.domain.ProtocolEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Real Google Maps 26.42 notifications, copied field for field. Each test says what a
 * rider expects to see on the dashboard. Samples 1-7 are the old layout (title = distance,
 * text = instruction); 8-12 are the Android 16 live-update layout (text is null, the
 * listener reads it as "").
 */
class MapsRealSamplesTest {

    private fun old(title: String, text: String, sub: String) =
        MapsNotificationParser.parse(title = title, text = text, subText = sub)

    /** Same chip order as the listener: primaryInfo, chipExpandedText, shortCriticalText. */
    private fun live(title: String, sub: String, progress: Int, max: Int, shortCritical: String) =
        MapsNotificationParser.liveUpdateFields(
            title, "", sub, progress, max, chipTexts = listOf("", "", shortCritical),
        )

    // ---- OLD layout ----

    @Test
    fun `1 toward a road is straight on, 70 m`() {
        val m = old("70 m", "toward Koti Women's College Rd / Main Rd", "14 min · 5.3 km · 12:21 AM ETA")!!
        assertEquals(ProtocolEngine.Maneuver.STRAIGHT, m.maneuverId)
        assertEquals("70 m", m.distanceToTurn)
        assertEquals("toward Koti Women's College Rd / Main Rd", m.instruction)
        assertEquals("5.3 km", m.remainingDistance)
        assertEquals(14, m.etaMinutes)
        assertEquals("1221AM", m.etaClock)
    }

    @Test
    fun `2 head north at 0 m is straight on`() {
        val m = old("0 m", "Head north", "15 min · 5.6 km · 12:23 AM ETA")!!
        assertEquals(ProtocolEngine.Maneuver.STRAIGHT, m.maneuverId)
        assertEquals("0 m", m.distanceToTurn)
        assertEquals("5.6 km", m.remainingDistance)
        assertEquals("1223AM", m.etaClock)
    }

    @Test
    fun `3 turn left in 50 m`() {
        val m = old("50 m", "Turn left", "13 min · 4.9 km · 12:22 AM ETA")!!
        assertEquals(ProtocolEngine.Maneuver.TURN_LEFT, m.maneuverId)
        assertEquals("50 m", m.distanceToTurn)
        assertEquals("4.9 km", m.remainingDistance)
        assertEquals(13, m.etaMinutes)
    }

    @Test
    fun `4 a road change with no direction word is not a left or right turn`() {
        val m = old("1.4 km", "Madina Cir onto NH 44", "10 min · 3.6 km · 12:19 AM ETA")!!
        assertEquals(ProtocolEngine.Maneuver.STRAIGHT, m.maneuverId)
        assertEquals(1400, MapsNotificationParser.distanceToMetres(m.distanceToTurn))
        assertEquals("3.6 km", m.remainingDistance)
        assertEquals("1219AM", m.etaClock)
    }

    @Test
    fun `5 turn left in 40 m`() {
        val m = old("40 m", "Turn left", "13 min · 4.8 km · 12:44 AM ETA")!!
        assertEquals(ProtocolEngine.Maneuver.TURN_LEFT, m.maneuverId)
        assertEquals("40 m", m.distanceToTurn)
        assertEquals("1244AM", m.etaClock)
    }

    @Ignore("fails today: parse() returns null when the title is empty, so the right turn never reaches the dashboard (job C3 step 1)")
    @Test
    fun `6 empty title with turn right is still a right turn, distance unknown`() {
        val m = old("", "Turn right", "11 min · 4.2 km · 12:29 AM ETA")
        assertNotNull(m)
        assertEquals(ProtocolEngine.Maneuver.TURN_RIGHT, m!!.maneuverId)
        assertEquals("", m.distanceToTurn)
        assertEquals("4.2 km", m.remainingDistance)
        assertEquals("1229AM", m.etaClock)
    }

    @Test
    fun `7 empty title with only road names is never a turn and does not crash`() {
        val m = old("", "Hyderabad - Janagam Hwy / Koti Women's College Rd / Main Rd", "22 min · 4.9 km · 7:51 PM ETA")
        assertTrue(
            "road names must not become a turn",
            m == null || m.maneuverId !in setOf(ProtocolEngine.Maneuver.TURN_LEFT, ProtocolEngine.Maneuver.TURN_RIGHT),
        )
    }

    // ---- NEW live-update layout ----

    @Test
    fun `8 head east on a long road name, distance unknown, remaining 5_3 km`() {
        val f = live("Head east on Hyderabad - Janagam Hwy/Koti Women's College Rd/Main Rd", "Arrive 1:32 AM", 0, 5348, "")!!
        val m = MapsNotificationParser.parseLive(f)!!
        assertEquals(ProtocolEngine.Maneuver.STRAIGHT, m.maneuverId)
        assertEquals("", m.distanceToTurn)
        assertEquals("Head east on Hyderabad - Janagam Hwy/Koti Women's College Rd/Main Rd", m.instruction)
        assertEquals("5.3 km", m.remainingDistance)
        assertEquals("0132AM", m.etaClock)
    }

    @Test
    fun `9 head west, remaining 6_0 km`() {
        val m = MapsNotificationParser.parseLive(live("Head west", "Arrive 1:34 AM", 0, 5960, "")!!)!!
        assertEquals(ProtocolEngine.Maneuver.STRAIGHT, m.maneuverId)
        assertEquals("", m.distanceToTurn)
        assertEquals("6.0 km", m.remainingDistance)
        assertEquals("0134AM", m.etaClock)
    }

    @Test
    fun `10 turn left with no distance in the title is a left turn, distance unknown`() {
        val m = MapsNotificationParser.parseLive(live("Turn left", "Arrive 1:32 AM", 0, 4848, "")!!)!!
        assertEquals(ProtocolEngine.Maneuver.TURN_LEFT, m.maneuverId)
        assertEquals("", m.distanceToTurn)
        assertEquals("4.8 km", m.remainingDistance)
    }

    /** U+00A0 between "1.4" and "km", exactly as Maps sent it. */
    private val sample11Title = "1.4 km · Turn right at Madina Cir to stay on NH 44"

    @Test
    fun `11a the non-breaking-space title is split into distance and instruction`() {
        val f = live(sample11Title, "Arrive 1:29 AM", 642, 4288, "1.4 km")!!
        assertEquals("1.4 km", f.title)
        assertEquals("Turn right at Madina Cir to stay on NH 44", f.text)
        assertTrue(f.subText, f.subText.endsWith("3.6 km"))
    }

    @Ignore("fails today: DISTANCE uses \\s*, which does not match U+00A0, so parse() finds no distance and returns null (job C3 step 2)")
    @Test
    fun `11b right turn in 1_4 km with a non-breaking space, remaining 3_6 km`() {
        val m = MapsNotificationParser.parseLive(live(sample11Title, "Arrive 1:29 AM", 642, 4288, "1.4 km")!!)
        assertNotNull(m)
        assertEquals(ProtocolEngine.Maneuver.TURN_RIGHT, m!!.maneuverId)
        assertEquals(1400, MapsNotificationParser.distanceToMetres(m.distanceToTurn))
        assertEquals("3.6 km", m.remainingDistance)
        assertEquals("0129AM", m.etaClock)
    }

    @Test
    fun `12 rerouting is a reroute, not a turn, and does not crash`() {
        val f = live("Rerouting...", "Arrive ", 0, 5895, "")!!
        assertTrue(MapsNotificationParser.isReroute("${f.title} ${f.text}"))
        assertNull(MapsNotificationParser.parseEtaClock(f.subText))
    }
}
