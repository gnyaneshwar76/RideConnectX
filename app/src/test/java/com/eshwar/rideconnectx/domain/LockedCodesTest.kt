package com.eshwar.rideconnectx.domain

import com.eshwar.rideconnectx.data.ble.PhoneStatusProvider
import com.eshwar.rideconnectx.data.nav.MapsArrowCatalog
import com.eshwar.rideconnectx.domain.ProtocolEngine.Maneuver
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the turn codes the owner locked after photographing them on the Access 125.
 * If one of these fails, a code was changed. Do not "fix" the test: ask the owner.
 */
class LockedCodesTest {

    @Test
    fun lockedTurnCodes() {
        assertEquals(1, Maneuver.TURN_LEFT)
        assertEquals(4, Maneuver.TURN_RIGHT)
        assertEquals(40, Maneuver.STRAIGHT)
        assertEquals(39, Maneuver.U_TURN)
        assertEquals(19, Maneuver.SLIGHT_LEFT)
        assertEquals(41, Maneuver.SLIGHT_RIGHT)
        assertEquals(45, Maneuver.ROUNDABOUT)
        assertEquals(9, Maneuver.DESTINATION)
        assertEquals(46, Maneuver.FIRST_BLANK)
    }

    @Test
    fun lockedForkCodes() {
        assertEquals(31, Maneuver.KEEP_LEFT)
        assertEquals(32, Maneuver.KEEP_RIGHT)
        // N33, owner yes 10 Oct 2026 (was 19 / 41). Locked 13 Oct 2026: "good for now lock the nivigations".
        assertEquals(27, Maneuver.RAMP_FORK_LEFT)
        assertEquals(28, Maneuver.RAMP_FORK_RIGHT)
        assertEquals(27, MapsArrowCatalog.CODES["maneuver_off_ramp_slight_left"])
        assertEquals(28, MapsArrowCatalog.CODES["maneuver_off_ramp_slight_right"])
        assertEquals(32, MapsArrowCatalog.CODES["maneuver_fork_right"])
        assertEquals(31, MapsArrowCatalog.CODES["maneuver_fork_left"])
    }

    @Test
    fun lockedSignalBarsMatchOfficialApp() {
        // getLevel() 0..4 -> 0,0,1,2,3 (C4858B.onSignalStrengthsChanged)
        assertEquals(
            listOf(0, 0, 1, 2, 3),
            (0..4).map { PhoneStatusProvider.officialSignalBars(it, null) },
        )
        // 5G SA: RSRP bins, ignore level
        assertEquals(3, PhoneStatusProvider.officialSignalBars(0, -89))
        assertEquals(2, PhoneStatusProvider.officialSignalBars(4, -90))
        assertEquals(1, PhoneStatusProvider.officialSignalBars(4, -105))
        assertEquals(0, PhoneStatusProvider.officialSignalBars(4, -120))
    }

    @Test
    fun lockedRoundaboutExits() {
        assertEquals(
            listOf(20, 21, 22, 23, 24, 25, 26),
            listOf(
                Maneuver.ROUNDABOUT_EXIT_SHARP_LEFT,
                Maneuver.ROUNDABOUT_EXIT_LEFT,
                Maneuver.ROUNDABOUT_EXIT_SLIGHT_LEFT,
                Maneuver.ROUNDABOUT_EXIT_STRAIGHT,
                Maneuver.ROUNDABOUT_EXIT_SLIGHT_RIGHT,
                Maneuver.ROUNDABOUT_EXIT_RIGHT,
                Maneuver.ROUNDABOUT_EXIT_SHARP_RIGHT,
            ),
        )
    }

    @Test
    fun drawableKeepsDrawnCodesAndBlanksBecomeStraight() {
        assertEquals(1, Maneuver.drawable(1))
        assertEquals(45, Maneuver.drawable(45))
        assertEquals(Maneuver.STRAIGHT, Maneuver.drawable(46))
        assertEquals(Maneuver.STRAIGHT, Maneuver.drawable(0))
    }
}
