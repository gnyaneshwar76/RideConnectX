package com.eshwar.rideconnectx.data.nav

import com.eshwar.rideconnectx.domain.ProtocolEngine
import com.eshwar.rideconnectx.domain.model.NavManeuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser, checked against the field layout Google Maps really uses.
 *
 * **These tests used to be wrong in the same way the parser was.** They passed
 * the instruction as `title` and the distance as `text`, matching the parser's
 * assumption rather than the phone's behaviour — so all of them passed while a
 * whole ride relayed nothing but straight-ahead. Captured from the rider's
 * phone on 6 August 2026:
 *
 * ```
 * title   = "350 m"                                ← distance to the turn
 * text    = "Turn right onto Ring Road"            ← the instruction
 * subText = "1 hr 12 min · 38 km · 1:38 am ETA"    ← duration, remaining, ETA
 * ```
 *
 * Everything below goes through [maps], which puts the arguments in that order,
 * so a test cannot silently drift back to the old shape.
 */
class MapsNotificationParserTest {

    @Test
    fun `reroute wording is recognised and normal turns are not`() {
        assertTrue(MapsNotificationParser.isReroute("Rerouting..."))
        assertTrue(MapsNotificationParser.isReroute(" Finding a new route"))
        assertTrue(!MapsNotificationParser.isReroute("200 m Turn right onto Dammaiguda Road"))
    }

    @Test
    fun `reroute frame puts dashes in the turn distance field`() {
        val f = ProtocolEngine.buildNavigationPacket(46, 0, "0930PM", distanceText = "----M")
        assertEquals("----M", String(f.copyOfRange(4, 9), Charsets.US_ASCII))
    }

    /** Builds a notification the way Maps actually posts one. */
    private fun maps(instruction: String, distance: String, journey: String = "") =
        MapsNotificationParser.parse(title = distance, text = instruction, subText = journey)

    private fun code(instruction: String) = maps(instruction, "300 m")?.maneuverId

    @Test
    fun `reads a plain left turn`() {
        val m = maps("Turn left onto MG Road", "400 m", "12 min · 5 km · 9:20 am ETA")

        assertNotNull(m)
        assertEquals(ProtocolEngine.Maneuver.TURN_LEFT, m!!.maneuverId)
        assertEquals("Turn left onto MG Road", m.instruction)
        assertEquals("400 m", m.distanceToTurn)
        assertEquals(12, m.etaMinutes)
    }

    /**
     * The instruction must never be the distance. The ride log filled up with
     * entries reading `Maps said : "350 m"`, which told nobody anything.
     */
    @Test
    fun `the instruction is the text, not the distance`() {
        val m = maps("Turn right onto Ring Road", "1.2 km", "8 min · 3 km · 9:15 am ETA")!!

        assertEquals("Turn right onto Ring Road", m.instruction)
        assertEquals("1.2 km", m.distanceToTurn)
        assertEquals(ProtocolEngine.Maneuver.TURN_RIGHT, m.maneuverId)
        assertEquals(8, m.etaMinutes)
    }

    /**
     * The ordering trap: "slight left" contains "left". If the plain patterns
     * were tested first every slight turn would be reported as a hard one, and
     * the cluster would show the wrong arrow.
     */
    @Test
    fun `slight and sharp turns are not mistaken for plain ones`() {
        assertEquals(ProtocolEngine.Maneuver.SLIGHT_LEFT, code("Slight left onto Church Street"))
        assertEquals(ProtocolEngine.Maneuver.SLIGHT_RIGHT, code("Slight right toward NH 44"))
        assertEquals(ProtocolEngine.Maneuver.SHARP_LEFT, code("Sharp left onto 5th Cross"))
        assertEquals(ProtocolEngine.Maneuver.SHARP_RIGHT, code("Sharp right onto Old Airport Rd"))
    }

    /**
     * The qualifier is sometimes joined to the direction by a hyphen. Losing it
     * turns a slight turn into a hard one on the cluster, which is worse than
     * useless at a junction.
     */
    @Test
    fun `a hyphenated qualifier still counts`() {
        assertEquals(ProtocolEngine.Maneuver.SLIGHT_RIGHT, code("Slight-right toward NH 44"))
        assertEquals(ProtocolEngine.Maneuver.SHARP_LEFT, code("Sharp-left onto 5th Cross"))
        assertEquals(ProtocolEngine.Maneuver.KEEP_RIGHT, code("Keep-right at the fork"))
    }

    @Test
    fun `keep left and keep right are distinct from turns`() {
        assertEquals(ProtocolEngine.Maneuver.KEEP_LEFT, code("Keep left at the fork"))
        assertEquals(ProtocolEngine.Maneuver.KEEP_RIGHT, code("Keep right to stay on NH 48"))
    }

    @Test
    fun `u-turn is recognised however it is hyphenated`() {
        assertEquals(ProtocolEngine.Maneuver.U_TURN, code("Make a U-turn at Silk Board"))
        assertEquals(ProtocolEngine.Maneuver.U_TURN, code("Make a U turn"))
    }

    @Test
    fun `roundabout and merge are recognised`() {
        assertEquals(ProtocolEngine.Maneuver.ROUNDABOUT, code("At the roundabout, take the 2nd exit"))
        assertEquals(ProtocolEngine.Maneuver.MERGE, code("Merge onto Outer Ring Road"))
    }

    @Test
    fun `arrival is recognised`() {
        assertEquals(ProtocolEngine.Maneuver.DESTINATION, code("Your destination is on the left"))
        assertEquals(ProtocolEngine.Maneuver.DESTINATION, code("Arriving at College"))
    }

    /**
     * "Head north" is what Maps posts at the start of a route, and it was the
     * very first thing the rider's phone sent. It has no turn word, so it must
     * land on straight-ahead rather than being dropped.
     */
    @Test
    fun `the opening instruction of a route is handled`() {
        val m = maps("Head north", "0 m", "1 hr 12 min · 38 km · 1:38 am ETA")

        assertNotNull(m)
        assertEquals(ProtocolEngine.Maneuver.STRAIGHT, m!!.maneuverId)
        assertEquals("Head north", m.instruction)
        assertEquals(72, m.etaMinutes)
        assertEquals("38 km", m.remainingDistance)
    }

    /**
     * Google rewords these notifications between releases. An unfamiliar phrase
     * must still produce an update — losing the instruction entirely would be a
     * far worse failure than showing a neutral arrow.
     */
    @Test
    fun `unknown phrasing still yields an update`() {
        val m = maps("Continue straight past the flyover", "900 m", "4 min · 1 km · 9:04 am ETA")

        assertNotNull(m)
        assertEquals(ProtocolEngine.Maneuver.STRAIGHT, m!!.maneuverId)
        assertEquals("900 m", m.distanceToTurn)
    }

    /**
     * The full translation, phrase to the number the cluster is told to draw.
     *
     * The other tests assert against `ProtocolEngine.Maneuver` constants, so
     * they would keep passing even if every constant were wrong. These pin the
     * literal values, every one of which was photographed on the rider's
     * Access 125 on 11 August 2026.
     */
    @Test
    fun `phrases map to the photographed cluster codes`() {
        // Junction glyphs 1/4 since 11 Sep 2026 — 37/35 drew a bend in the road.
        assertEquals(1, code("Turn left onto MG Road"))
        assertEquals(4, code("Turn right onto Ring Road"))
        assertEquals(41, code("Slight right toward NH44"))
        assertEquals(39, code("Make a U-turn at the junction"))
        assertEquals(45, code("At the roundabout, take the 2nd exit"))
        assertEquals(34, code("Sharp left onto Church Street"))
        assertEquals(36, code("Sharp right onto Brigade Road"))
        assertEquals(31, code("Keep left at the fork"))
        assertEquals(32, code("Keep right at the fork"))

        // Slight left is 19, photographed 18 August 2026. It was 37 (a full
        // curved left) while the cluster was thought to have no up-left
        // diagonal; the icon simply lives below 31, where no sweep had looked.
        assertEquals(19, code("Slight left onto Tank Bund"))

        // The destination marker is 9 - a ring with a filled centre, also
        // photographed 18 August. It was 40 because a chequered flag was being
        // looked for and the cluster draws a bullseye.
        assertEquals(9, code("Arriving at College"))

        // Still not observed; falls back to straight rather than a blank code.
        assertEquals(40, code("Merge onto the flyover"))
    }

    /** Nothing may be sent in the band the cluster draws nothing for. */
    @Test
    fun `no phrase produces a code the cluster ignores`() {
        val phrases = listOf(
            "Turn left onto MG Road", "Turn right", "Slight left", "Slight right",
            "Make a U-turn", "At the roundabout take the 2nd exit", "Merge",
            "Arriving at destination", "Continue straight past the flyover",
            "Head north on some unnamed road",
        )
        for (p in phrases) {
            val id = code(p) ?: continue
            assertTrue("$p produced $id, which draws nothing", id < 46)
            // Lower bound is 1, not 31: the 18 August sweep confirmed the 1-9
            // junction family and the 15-19 bearings all draw. A guard at 31
            // would now reject the correct slight-left (19) and destination (9).
            assertTrue("$p produced $id, below the drawable range", id >= 1)
        }
    }

    /** Maps posts plenty of notifications that are not navigation. */
    @Test
    fun `non-navigation notifications are ignored`() {
        assertNull(maps("Some features are unavailable", "You're offline"))
        assertNull(maps("How was Cafe Coffee Day?", "Rate your visit"))
        assertNull(maps("", ""))
    }

    @Test
    fun `imperial units are read`() {
        assertEquals("0.5 mi", maps("Turn left", "0.5 mi", "3 min")!!.distanceToTurn)
        assertEquals("250 ft", maps("Turn right", "250 ft", "1 min")!!.distanceToTurn)
    }

    @Test
    fun `eta combines hours and minutes`() {
        assertEquals(65, maps("Merge onto NH 65", "2.0 km", "1 hr 5 min · 40 km")!!.etaMinutes)
    }

    @Test
    fun `eta is null when maps does not state one`() {
        assertNull(maps("Turn left onto MG Road", "400 m")!!.etaMinutes)
    }

    /* -- The arrow, and arrival: added 20 August 2026 ----------------- */

    /**
     * The arrow outranks the words.
     *
     * Maps names the road being turned onto and counts down to it, so the text
     * carried a direction on only 12% of the 19 August ride. The arrow carries
     * one on every frame and distinguishes slight from normal from sharp, which
     * the text does not, so it wins where the two are both available.
     */
    @Test
    fun `the arrow outranks the text`() {
        val m = MapsNotificationParser.parse(
            title = "300 m",
            text = "Turn left onto MG Road",
            iconCode = ProtocolEngine.Maneuver.SLIGHT_LEFT,
        )

        assertEquals(ProtocolEngine.Maneuver.SLIGHT_LEFT, m!!.maneuverId)
        assertEquals(NavManeuver.Source.ICON, m.codeSource)
    }

    /**
     * A road name with an arrow is the case that was broken all along: 1,643
     * frames of the 19 August ride went out as STRAIGHT because only the words
     * were read.
     */
    @Test
    fun `a road name with an arrow is no longer straight`() {
        val m = MapsNotificationParser.parse(
            title = "400 m",
            text = "Dammaiguda Rd / Dammaiguda X Rd / Nagaram Rd",
            iconCode = ProtocolEngine.Maneuver.TURN_RIGHT,
        )

        assertEquals(ProtocolEngine.Maneuver.TURN_RIGHT, m!!.maneuverId)
    }

    /** With no arrow identified, the text still decides - as it always did. */
    @Test
    fun `text still decides when the arrow is unknown`() {
        val m = maps("Turn left onto MG Road", "400 m")

        assertEquals(ProtocolEngine.Maneuver.TURN_LEFT, m!!.maneuverId)
        assertEquals(NavManeuver.Source.TEXT, m.codeSource)
    }

    /**
     * Arrival, which was invisible in every log before 20 August.
     *
     * Maps drops the next-turn distance because there is no next turn, and puts
     * the destination's NAME in the text. No regex can match an arbitrary place
     * name; the collapsed remaining distance identifies it instead.
     */
    @Test
    fun `arrival is recognised from the remaining distance, not the name`() {
        val m = MapsNotificationParser.parse(
            title = "",
            text = "SRI DEVI RESIDENCY",
            subText = "0 min · 40 m · 6:12 pm ETA",
        )

        assertNotNull(m)
        assertEquals(ProtocolEngine.Maneuver.DESTINATION, m!!.maneuverId)
    }

    /** A place name still far away is not arrival - it is an unreadable frame. */
    @Test
    fun `a distant destination name is not arrival`() {
        assertNull(
            MapsNotificationParser.parse(
                title = "",
                text = "SRI DEVI RESIDENCY",
                subText = "1 hr 12 min · 38 km · 1:38 am ETA",
            )
        )
    }

    /** 30 Sep ride: the cluster clock must be Maps' own ETA, not now + minutes. */
    @Test
    fun `the arrival clock is taken from Maps`() {
        assertEquals("1107AM", maps("Turn left", "50 m", "32 min · 12 km · 11:07 am ETA")!!.etaClock)
        assertEquals("0253PM", MapsNotificationParser.parseEtaClock("15 min · 6.3 km · 2:53 pm ETA"))
        assertEquals("0253PM", MapsNotificationParser.parseEtaClock("15 min · 6.3 km · 14:53 ETA"))
        assertEquals("1205AM", MapsNotificationParser.parseEtaClock("5 min · 1 km · 00:05 ETA"))
        assertNull(MapsNotificationParser.parseEtaClock("1 hr 5 min · 40 km"))
    }

    @Test
    fun `distance units convert correctly, miles are not metres`() {
        assertEquals(1609, MapsNotificationParser.distanceToMetres("1 mi"))
        assertEquals(804, MapsNotificationParser.distanceToMetres("0.5 mi"))
        assertEquals(1200, MapsNotificationParser.distanceToMetres("1,2 km"))
        assertEquals(15, MapsNotificationParser.distanceToMetres("50 ft"))
        assertEquals(400, MapsNotificationParser.distanceToMetres("400 m"))
        assertNull(MapsNotificationParser.distanceToMetres("12 min"))
    }

    @Test
    fun `half a km with a decimal comma is not read as arrival`() {
        assertNull(MapsNotificationParser.parse("", "Ramani Nilayam", "3 min · 0,5 km · 6:12 pm ETA"))
        assertNull(MapsNotificationParser.parse("", "Ramani Nilayam", "1 min · 0.2 mi · 6:12 pm ETA"))
    }

    @Test
    fun `group summary flag is detected`() {
        assertTrue(isGroupSummary(android.app.Notification.FLAG_GROUP_SUMMARY))
        assertTrue(!isGroupSummary(0))
    }
}
