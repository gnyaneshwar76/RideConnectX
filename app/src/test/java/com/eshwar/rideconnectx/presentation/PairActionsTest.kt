package com.eshwar.rideconnectx.presentation

import com.eshwar.rideconnectx.domain.repository.ConnectionState
import com.eshwar.rideconnectx.presentation.viewmodel.ScanUiState
import com.eshwar.rideconnectx.presentation.viewmodel.pairActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairActionsTest {
    private fun s(c: ConnectionState, last: String? = "AA:BB") =
        ScanUiState(connectionState = c, lastDeviceAddress = last)

    @Test fun scanningShowsStopOnly() {
        val a = pairActions(s(ConnectionState.Scanning))
        assertTrue(a.stop); assertFalse(a.scanAgain); assertFalse(a.connectLast)
    }

    @Test fun idleAfterStopOffersScanAgain() {
        val a = pairActions(s(ConnectionState.Idle))
        assertFalse(a.stop); assertTrue(a.scanAgain); assertFalse(a.connectLast)
    }

    @Test fun disconnectedOffersConnectAndScan() {
        val a = pairActions(s(ConnectionState.Disconnected("x")))
        assertTrue(a.scanAgain); assertTrue(a.connectLast)
    }

    @Test fun noSavedScooterNoConnect() {
        assertFalse(pairActions(s(ConnectionState.Disconnected("x"), last = null)).connectLast)
    }

    @Test fun connectingHidesAll() {
        assertEquals(
            false to false,
            pairActions(s(ConnectionState.Connecting("A", "n"))).let { it.scanAgain to it.connectLast },
        )
    }
}
