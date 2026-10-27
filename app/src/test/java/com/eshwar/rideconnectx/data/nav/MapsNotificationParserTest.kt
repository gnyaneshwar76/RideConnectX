package com.eshwar.rideconnectx.data.nav

import com.eshwar.rideconnectx.domain.ProtocolEngine
import com.eshwar.rideconnectx.domain.model.NavManeuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.eshwar.rideconnectx.data.repository.UnknownTurnHold
import com.eshwar.rideconnectx.data.repository.keepsTurnRule
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
        // Android 16 live-update Maps (Samsung S23 FE, 22 Oct 2026)
        assertEquals("0501PM", MapsNotificationParser.parseEtaClock("Arrive 5:01 pm"))
        // Live-update frame after mapping: title = metres from progress, text = instruction
        MapsNotificationParser.parse("200 m", "Head north", "Arrive 6:32 pm · 25.8 km").let {
            assertEquals("200 m", it?.distanceToTurn)
            assertEquals("25.8 km", it?.remainingDistance)
            assertEquals("0632PM", it?.etaClock)
        }
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

    // ---- Android 16 live-update layout; titles and sub texts copied from the owner's ride log 4 ----

    private fun live(title: String, text: String = "", sub: String, progress: Int = 0, max: Int = 0, chips: List<String> = emptyList()) =
        MapsNotificationParser.liveUpdateFields(title, text, sub, progress, max, chips)

    private fun parseLive(f: MapsNotificationParser.LiveFields) = MapsNotificationParser.parseLive(f)

    @Test
    fun `live update with distance in the title is split and gets remaining km`() {
        val f = live("120 m · Turn left", sub = "Arrive 5:17 pm", progress = 1000, max = 26846)!!
        assertEquals("120 m", f.title)
        assertEquals("Turn left", f.text)
        val m = parseLive(f)!!
        assertEquals("120 m", m.distanceToTurn)
        assertEquals("Turn left", m.instruction)
        assertEquals("25.8 km", m.remainingDistance)
        assertEquals("0517PM", m.etaClock)
        assertEquals(ProtocolEngine.Maneuver.TURN_LEFT, m.maneuverId)
    }

    @Test
    fun `live update title without distance takes it from the chip, else unknown, and still gets remaining`() {
        val a = live("Turn left", sub = "Arrive 5:17 pm", progress = 25000, max = 25846, chips = listOf("", "20 m"))!!
        assertEquals("20 m", a.title)
        assertEquals("Turn left", a.text)
        assertEquals("846 m", parseLive(a)!!.remainingDistance)
        val b = live("Head toward Rushi School Rd", sub = "Arrive 5:28 pm", max = 4200)!!
        assertEquals("", b.title)
        assertEquals("", parseLive(b)!!.distanceToTurn)
        assertEquals("4.2 km", parseLive(b)!!.remainingDistance)
    }

    @Test
    fun `live update place name near the end is arrival`() {
        val f = live("SRI DEVI RESIDENCY", sub = "Arrive 5:27 pm", progress = 780, max = 800)!!
        val m = parseLive(f)!!
        assertEquals("SRI DEVI RESIDENCY", m.instruction)
        assertEquals("20 m", m.remainingDistance)
    }

    @Test
    fun `live update split keeps long road names and a 450 m title`() {
        val f = live("450 m · Turn right onto Vayushakti Nagar Rd", sub = "Arrive 5:28 pm", progress = 100, max = 5100)!!
        assertEquals("450 m", f.title)
        assertEquals("Turn right onto Vayushakti Nagar Rd", f.text)
        assertEquals("5.0 km", parseLive(f)!!.remainingDistance)
    }

    @Test
    fun `live update rerouting and arriving frames`() {
        val r = live("Rerouting...", sub = "Arrive ", progress = 0, max = 5000)!!
        assertTrue(MapsNotificationParser.isReroute("${r.title} ${r.text}"))
        // Arriving: text is filled, so this is not a live-update frame and goes the old way.
        assertNull(live("Arriving", text = "at SRI DEVI RESIDENCY", sub = "", max = 100))
    }

    @Test
    fun `old layout and non navigation frames are untouched`() {
        assertNull(live("400 m", text = "Turn left", sub = "12 min · 5 km · 2:53 pm ETA"))
        assertNull(live("400 m", sub = "x", max = 5000))                 // distance-only title: old layout
        assertNull(live("Starting navigation…", sub = ""))              // no progress, no distance
        assertNull(live("", sub = "", max = 5000))
    }

    @Test
    fun `live update with no progress still splits but sends no remaining`() {
        val f = live("120 m · Turn left", sub = "Arrive 5:17 pm")!!
        assertEquals("Arrive 5:17 pm", f.subText)
        assertEquals("", parseLive(f)!!.remainingDistance)
    }

    @Test
    fun `ringing Samsung call carries a full screen intent, an answered one does not`() {
        assertTrue(isAnsweredCall(callType = 2, hasFullScreenIntent = false))
        assertTrue(!isAnsweredCall(callType = 2, hasFullScreenIntent = true))
        assertTrue(!isAnsweredCall(callType = 1, hasFullScreenIntent = true))   // incoming (Pixel)
        assertTrue(!isAnsweredCall(callType = 0, hasFullScreenIntent = false))  // not CallStyle
    }

    // ---- N101-N103 (26 Oct 2026). NOT RUN when written: no gradle was allowed. Xiaomi values from the owner's capture. ----

    @Test
    fun `T1 xiaomi first frame has no distance - unknown, not 0 m`() {
        val f = live("Head north", sub = "Arrive 12:34 pm", progress = 0, max = 12408, chips = listOf("", "", ""))!!
        assertEquals("", f.title)
        val m = parseLive(f)!!
        assertEquals("", m.distanceToTurn)
        assertEquals("Head north", m.instruction)
        assertEquals("12.4 km", m.remainingDistance)
        assertEquals("1234PM", m.etaClock)
    }

    @Test
    fun `T2 empty shortCriticalText is not a distance`() {
        assertEquals("", live("Head north", sub = "Arrive 12:34 pm", progress = 0, max = 12408, chips = listOf("", "", ""))!!.title)
    }

    @Test
    fun `T3 shortCriticalText that could be minutes is not a distance`() {
        assertEquals("", live("Turn left", sub = "Arrive 5:17 pm", max = 5000, chips = listOf("", "", "5 m"))!!.title)
        assertEquals("", live("Turn left", sub = "Arrive 5:17 pm", max = 5000, chips = listOf("", "", "12 min"))!!.title)
        assertEquals("300 m", live("Turn left", sub = "Arrive 5:17 pm", max = 5000, chips = listOf("", "300 m", ""))!!.title)
        assertEquals("1.2 km", live("Turn left", sub = "Arrive 5:17 pm", max = 5000, chips = listOf("", "", "1.2 km"))!!.title)
    }

    @Test
    fun `T4 title with distance on xiaomi gets remaining km`() {
        val f = live("450 m · Turn right onto Vayushakti Nagar Rd", sub = "Arrive 12:34 pm", progress = 8000, max = 12408)!!
        assertEquals("450 m", f.title)
        assertEquals("4.4 km", parseLive(f)!!.remainingDistance)
    }

    @Test
    fun `T5 unknown distance keeps the previous turn`() {
        assertTrue(keepsTurnRule(prevMetres = 120, freshMetres = 0, freshKnown = false, freshArrival = false))
        assertTrue(!keepsTurnRule(prevMetres = 0, freshMetres = 0, freshKnown = false, freshArrival = false))
        assertTrue(!keepsTurnRule(prevMetres = 120, freshMetres = 0, freshKnown = false, freshArrival = true))
        assertTrue(!keepsTurnRule(prevMetres = 120, freshMetres = 90, freshKnown = true, freshArrival = false))
    }

    @Test
    fun `T6 first frame with no previous turn is held 3 s, then goes`() {
        val h = UnknownTurnHold()
        assertTrue(!h.mayShow(known = false, nowMs = 10_000))
        assertTrue(!h.mayShow(known = false, nowMs = 12_900))
        assertTrue(h.mayShow(known = false, nowMs = 13_050))
        assertTrue(h.mayShow(known = true, nowMs = 13_100))
        assertTrue(!h.mayShow(known = false, nowMs = 14_000))   // a later unknown waits again
    }

    @Test
    fun `T7 alert titles with a distance are not turns`() {
        for (t in listOf("Accident-prone area for the next 500 m", "Collision on NH44", "Low visibility reported ahead", "Lane closure")) {
            assertNull(t, live(t, sub = ""))                          // no progress, no split
            assertNull(t, MapsNotificationParser.parse(t, "", ""))    // and parse refuses it too
        }
    }

    @Test
    fun `T8 xiaomi arrival place name`() {
        val f = live("SRI DEVI RESIDENCY", sub = "Arrive 5:27 pm", progress = 12350, max = 12408)!!
        val m = parseLive(f)!!
        assertEquals(ProtocolEngine.Maneuver.DESTINATION, m.maneuverId)
        assertEquals("58 m", m.remainingDistance)
    }

    @Test
    fun `T9 rerouting on xiaomi`() {
        val r = live("Rerouting...", sub = "Arrive ", progress = 0, max = 12408)!!
        assertTrue(MapsNotificationParser.isReroute("${r.title} ${r.text}"))
    }

    @Test
    fun `frame watch logs a changed shape once and unreadable after 5 in a row`() {
        val w = MapsFrameWatch()
        assertTrue(w.shape("a:String"))
        assertTrue(!w.shape("a:String"))
        assertTrue(w.shape("a:String,b:Integer"))
        assertNull(w.parsed(false)); assertNull(w.parsed(false)); assertNull(w.parsed(false)); assertNull(w.parsed(false))
        assertEquals(5, w.parsed(false))
        assertNull(w.parsed(false))                 // only once per run
        assertNull(w.parsed(true))
        repeat(4) { assertNull(w.parsed(false)) }
        assertEquals(5, w.parsed(false))
    }
}
