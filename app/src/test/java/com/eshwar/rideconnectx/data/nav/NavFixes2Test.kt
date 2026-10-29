package com.eshwar.rideconnectx.data.nav

import com.eshwar.rideconnectx.data.repository.EtaClockCache
import com.eshwar.rideconnectx.data.repository.FrameKey
import com.eshwar.rideconnectx.data.repository.keepsTurnRule
import com.eshwar.rideconnectx.data.repository.skipsGpsFrame
import com.eshwar.rideconnectx.domain.ProtocolEngine
import com.eshwar.rideconnectx.domain.model.NavManeuver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Job C4b. */
class NavFixes2Test {

    // ---- road name at the very start of the text ----

    @Test
    fun `a text that starts with onto or toward is a road name, not a turn`() {
        val toward = MapsNotificationParser.parse("70 m", "toward Right Side Colony / Main Rd", "14 min · 5.3 km · 12:21 AM ETA")!!
        assertEquals(ProtocolEngine.Maneuver.STRAIGHT, toward.maneuverId)
        assertEquals("toward Right Side Colony / Main Rd", toward.instruction)
        val onto = MapsNotificationParser.parse("200 m", "onto Left Bank Rd", "5 min · 2 km · 1:00 PM ETA")!!
        assertEquals(ProtocolEngine.Maneuver.STRAIGHT, onto.maneuverId)
        assertEquals("", MapsNotificationParser.beforeRoadName("Toward Left Bank Rd"))
        assertEquals("Turn right", MapsNotificationParser.beforeRoadName("Turn right onto Left Bank Rd"))
        // "Ontario Rd" is a word, not "onto".
        assertEquals("Ontario Rd", MapsNotificationParser.beforeRoadName("Ontario Rd"))
    }

    // ---- relay skip ----

    private val m = NavManeuver(ProtocolEngine.Maneuver.TURN_LEFT, "Turn left", "120 m", etaClock = "0132AM")

    @Test
    fun `a GPS tick with nothing changed is skipped, anything changed is sent`() {
        val sent = FrameKey(m, 120, 4800, "0132AM")
        assertTrue(skipsGpsFrame(true, sent, FrameKey(m, 120, 4800, "0132AM")))
        assertTrue(!skipsGpsFrame(true, sent, FrameKey(m, 110, 4800, "0132AM")))   // metres moved
        assertTrue(!skipsGpsFrame(true, sent, FrameKey(m, 120, 4790, "0132AM")))   // remaining moved
        assertTrue(!skipsGpsFrame(true, sent, FrameKey(m, 120, 4800, "0133AM")))   // ETA moved
        assertTrue(!skipsGpsFrame(true, null, FrameKey(m, 120, 4800, "0132AM")))   // nothing sent yet / after a clear
        assertTrue(!skipsGpsFrame(false, sent, FrameKey(m, 120, 4800, "0132AM")))  // Maps posts always go to the repeat check
    }

    // ---- ETA cache ----

    @Test
    fun `Maps' own ETA clock is formatted once for the maneuver`() {
        var calls = 0
        val cache = EtaClockCache { calls++; it.etaClock!! }
        assertEquals("0132AM", cache.get(m, 0))
        assertEquals("0132AM", cache.get(m, 10 * 60_000L))
        assertEquals(1, calls)
    }

    @Test
    fun `a computed ETA is redone every minute, never frozen`() {
        var minute = 0
        val noClock = NavManeuver(ProtocolEngine.Maneuver.STRAIGHT, "Head west", "", etaMinutes = 10)
        val cache = EtaClockCache { "08%02dPM".format(minute) }
        assertEquals("0800PM", cache.get(noClock, 0))
        minute = 1
        assertEquals("0800PM", cache.get(noClock, 59_999))   // same minute: cached
        assertEquals("0801PM", cache.get(noClock, 60_000))   // next minute: recomputed
        minute = 7
        assertEquals("0807PM", cache.get(noClock, 7 * 60_000L))
    }

    @Test
    fun `a new maneuver always gets its own ETA`() {
        val cache = EtaClockCache { it.etaClock!! }
        assertEquals("0132AM", cache.get(m, 0))
        assertEquals("0134AM", cache.get(m.copy(etaClock = "0134AM"), 0))
    }

    // ---- lane order ----

    @Test
    fun `the Maps lane runs frames one at a time in posting order`() = runBlocking {
        val lane = serialLane()
        val scope = CoroutineScope(lane)
        val seen = java.util.Collections.synchronizedList(ArrayList<Int>())
        var running = 0
        var overlap = false
        val jobs = (0 until 200).map { i ->
            scope.launch {
                if (++running > 1) overlap = true
                Thread.sleep(if (i % 20 == 0) 2L else 0L)
                seen += i
                running--
            }
        }
        jobs.joinAll()
        assertEquals((0 until 200).toList(), seen.toList())
        assertTrue("two frames ran at once", !overlap)
    }

    // ---- no carried turn without a real signal ----

    private fun old(title: String, text: String, sub: String) = MapsNotificationParser.parse(title, text, sub)!!
    private fun live(title: String, sub: String, progress: Int, max: Int) =
        MapsNotificationParser.parseLive(MapsNotificationParser.liveUpdateFields(title, "", sub, progress, max, listOf("", "", ""))!!)!!

    private fun keeps(prev: NavManeuver, fresh: NavManeuver) = keepsTurnRule(
        MapsNotificationParser.distanceToMetres(prev.distanceToTurn) ?: 0,
        MapsNotificationParser.distanceToMetres(fresh.distanceToTurn) ?: 0,
        fresh.distanceToTurn.isNotBlank(),
        freshArrival = false,
        freshHasTurn = fresh.codeSource != NavManeuver.Source.FALLBACK,
    )

    @Test
    fun `a distance-less turn is shown as itself, not swapped for the old turn`() {
        // Sample 5 then sample 6: the right turn must not hide behind "40 m left".
        assertTrue(!keeps(old("40 m", "Turn left", "13 min · 4.8 km · 12:44 AM ETA"), old("", "Turn right", "11 min · 4.2 km · 12:29 AM ETA")))
        // Sample 3 then sample 10: even the same "Turn left" is not carried without a distance.
        assertTrue(!keeps(old("50 m", "Turn left", "13 min · 4.9 km · 12:22 AM ETA"), live("Turn left", "Arrive 1:32 AM", 0, 4848)))
    }

    @Test
    fun `a post with no direction still keeps the turn`() {
        // Sample 3 then sample 8 ("Head east on ..." has no turn of its own).
        assertTrue(keeps(
            old("50 m", "Turn left", "13 min · 4.9 km · 12:22 AM ETA"),
            live("Head east on Hyderabad - Janagam Hwy/Koti Women's College Rd/Main Rd", "Arrive 1:32 AM", 0, 5348),
        ))
    }
}
