package com.eshwar.rideconnectx

import com.eshwar.rideconnectx.presentation.navigation.Routes
import com.eshwar.rideconnectx.presentation.navigation.SetupGate
import org.junit.Assert.assertEquals
import org.junit.Test

/** First open is Welcome then Home; every later open goes straight to Home (22 Nov 2026). */
class SetupGateTest {

    @Test
    fun `nobody on the phone opens the welcome page`() {
        assertEquals(Routes.INTRO, SetupGate.launchRoute(signedIn = false))
    }

    @Test
    fun `a guest or account already on the phone opens Home`() {
        assertEquals(Routes.DASHBOARD, SetupGate.launchRoute(signedIn = true))
    }

    @Test
    fun `an unanswered guest-merge question comes first`() {
        assertEquals(Routes.PROFILE_FOUND, SetupGate.launchRoute(signedIn = true, guestMergePending = true))
    }
}
