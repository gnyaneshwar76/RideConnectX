package com.eshwar.rideconnectx.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSetupTest {
    @Test fun guestNameIsValid() = assertTrue(GuestNameRules.isValid(AutoSetup.GUEST_NAME))

    @Test fun guestOnlyWhenNobodySignedIn() {
        assertTrue(AutoSetup.needsGuest(UserSession()))
        assertFalse(AutoSetup.needsGuest(UserSession(name = "A", method = LoginMethod.GUEST)))
        assertFalse(AutoSetup.needsGuest(UserSession(name = "A", method = LoginMethod.GOOGLE, uid = "u")))
    }

    @Test fun modelFromBluetoothName() {
        assertEquals("access_125", AutoSetup.vehicleIdFor("SAS010217219", null))
    }

    @Test fun savedModelIsKept() = assertNull(AutoSetup.vehicleIdFor("SAS010217219", "access_125"))

    @Test fun unknownNameSavesNothing() {
        assertNull(AutoSetup.vehicleIdFor("SXYZ00000", null))
        assertNull(AutoSetup.vehicleIdFor(null, null))
        assertNull(AutoSetup.vehicleIdFor("ab", null))
    }
}
