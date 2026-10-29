package com.eshwar.rideconnectx.data.nav

import com.eshwar.rideconnectx.data.repository.keepsTurnRule
import com.eshwar.rideconnectx.domain.ProtocolEngine
import com.eshwar.rideconnectx.domain.model.NavManeuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Job C4: when the relay keeps the previous turn, checked with real Maps 26.42 samples. */
class TurnKeepRealSamplesTest {

    private fun old(title: String, text: String, sub: String) = MapsNotificationParser.parse(title, text, sub)!!
    private fun live(title: String, sub: String, progress: Int, max: Int) =
        MapsNotificationParser.parseLive(MapsNotificationParser.liveUpdateFields(title, "", sub, progress, max, listOf("", "", ""))!!)!!

    /** The same inputs NavigationRelay.keepsTurn passes. */
    private fun keeps(prev: NavManeuver, fresh: NavManeuver) = keepsTurnRule(
        MapsNotificationParser.distanceToMetres(prev.distanceToTurn) ?: 0,
        MapsNotificationParser.distanceToMetres(fresh.distanceToTurn) ?: 0,
        fresh.distanceToTurn.isNotBlank(),
        freshArrival = false,
        freshNewTurn = fresh.codeSource != NavManeuver.Source.FALLBACK && fresh.maneuverId != prev.maneuverId,
    )

    @Test
    fun `sample 5 then 6 - turn right with no distance replaces the old left turn`() {
        val prev = old("40 m", "Turn left", "13 min · 4.8 km · 12:44 AM ETA")
        val fresh = old("", "Turn right", "11 min · 4.2 km · 12:29 AM ETA")
        assertEquals(ProtocolEngine.Maneuver.TURN_RIGHT, fresh.maneuverId)
        assertTrue(!keeps(prev, fresh))
    }

    @Test
    fun `sample 3 then 10 - same left turn with no distance keeps the counted metres`() {
        val prev = old("50 m", "Turn left", "13 min · 4.9 km · 12:22 AM ETA")
        val fresh = live("Turn left", "Arrive 1:32 AM", 0, 4848)
        assertTrue(keeps(prev, fresh))
    }

    @Test
    fun `sample 4 then 8 - a head-on-road post with no direction keeps the turn`() {
        val prev = old("1.4 km", "Madina Cir onto NH 44", "10 min · 3.6 km · 12:19 AM ETA")
        val fresh = live("Head east on Hyderabad - Janagam Hwy/Koti Women's College Rd/Main Rd", "Arrive 1:32 AM", 0, 5348)
        assertEquals(NavManeuver.Source.FALLBACK, fresh.codeSource)
        assertTrue(keeps(prev, fresh))
    }

    @Test
    fun `a new turn at 0 m is shown, not hidden behind the old one`() {
        val prev = old("50 m", "Turn left", "13 min · 4.9 km · 12:22 AM ETA")
        val fresh = old("0 m", "Turn right", "13 min · 4.9 km · 12:22 AM ETA")
        assertTrue(!keeps(prev, fresh))
    }

    @Test
    fun `road names with left or right never win over the real instruction`() {
        assertEquals(ProtocolEngine.Maneuver.TURN_RIGHT, old("200 m", "Turn right onto Left Bank Rd", "5 min · 2 km · 1:00 PM ETA").maneuverId)
        assertEquals(ProtocolEngine.Maneuver.TURN_LEFT, old("200 m", "Turn left toward Right Side Colony", "5 min · 2 km · 1:00 PM ETA").maneuverId)
        // No turn verb: the "Left" is part of the road name.
        val road = old("400 m", "Neredmet - Left Bank Rd", "5 min · 2 km · 1:00 PM ETA")
        assertEquals(ProtocolEngine.Maneuver.STRAIGHT, road.maneuverId)
        assertEquals(NavManeuver.Source.FALLBACK, road.codeSource)
        assertEquals(ProtocolEngine.Maneuver.STRAIGHT, old("70 m", "toward Koti Women's College Rd / Main Rd", "14 min · 5.3 km · 12:21 AM ETA").maneuverId)
    }

    @Test
    fun `the arrow still wins over a road name`() {
        val m = MapsNotificationParser.parse("400 m", "Neredmet - Left Bank Rd", "5 min · 2 km · 1:00 PM ETA", iconCode = ProtocolEngine.Maneuver.TURN_RIGHT)!!
        assertEquals(ProtocolEngine.Maneuver.TURN_RIGHT, m.maneuverId)
    }
}
