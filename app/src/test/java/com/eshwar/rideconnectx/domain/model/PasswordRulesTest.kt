package com.eshwar.rideconnectx.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sign-up password rule: 8+ characters, at least one letter and one number. */
class PasswordRulesTest {

    @Test
    fun `needs length, a letter and a number`() {
        assertTrue(PasswordRules.isValid("scooter1"))
        assertFalse(PasswordRules.isValid("scoot1"))      // too short
        assertFalse(PasswordRules.isValid("scooters"))    // no number
        assertFalse(PasswordRules.isValid("12345678"))    // no letter
        assertFalse(PasswordRules.isValid(""))
    }
}
