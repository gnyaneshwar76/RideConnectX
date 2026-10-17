package com.eshwar.rideconnectx.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every text field's limits (N16). */
class FieldRulesTest {

    @Test
    fun `names take letters, space, hyphen and underscore, 2 to 24`() {
        assertNull(GuestNameRules.validate("Gnyaneshwar P"))
        assertNull(GuestNameRules.validate("Souza-Rao_Jr"))
        assertNotNull(GuestNameRules.validate("R"))
        assertNotNull(GuestNameRules.validate("Rocky123"))
        assertNotNull(GuestNameRules.validate("D'Souza"))
        assertNotNull(GuestNameRules.validate("a".repeat(25)))
    }

    @Test
    fun `other characters are ignored as typed, and say so`() {
        assertEquals(FieldRules.Filtered("Gnyaneshwar P", ignored = true), FieldRules.filterName("Gnyaneshwar .P", 24))
        assertEquals(FieldRules.Filtered("Rocky_Bhai", ignored = false), FieldRules.filterName("Rocky_Bhai", 24))
        assertEquals("Rocky", FieldRules.filterName("Rocky@123!", 24).text)
        assertEquals(12, FieldRules.filterName("a".repeat(20), 12).text.length)
    }

    @Test
    fun `a nickname is 2 to 12 of the same characters`() {
        assertNull(FieldRules.nicknameError("Eshwar"))
        assertNull(FieldRules.nicknameError("Rocky Bhai"))
        assertNotNull(FieldRules.nicknameError("E"))
        assertNotNull(FieldRules.nicknameError("Rocky1"))
        assertNotNull(FieldRules.nicknameError("Abcdefghijklm"))
    }

    @Test
    fun `centre, task and contact names need letters and a sensible length`() {
        assertTrue(FieldRules.isValidCentre(""))
        assertFalse(FieldRules.isValidCentre("AB"))
        assertFalse(FieldRules.isValidCentre("2000"))
        assertFalse(FieldRules.isValidCentre("S".repeat(61)))
        assertTrue(FieldRules.isValidTaskName("Oil"))
        assertFalse(FieldRules.isValidTaskName("O"))
        assertTrue(FieldRules.isValidContactName("Amma"))
        assertFalse(FieldRules.isValidContactName("A"))
        assertFalse(FieldRules.isValidContactName("Amma 2"))
    }

    @Test
    fun `phones are digits with an optional leading plus, 10 to 15 digits`() {
        assertEquals("+919876543210", FieldRules.phoneInput("+91 98765-43210"))
        assertEquals("9876543210", FieldRules.phoneInput("98765+43210"))
        assertTrue(FieldRules.isValidPhone("+919876543210"))
        assertTrue(FieldRules.isValidPhone("9876543210"))
        assertFalse(FieldRules.isValidPhone("98765432"))
        assertFalse(FieldRules.isValidPhone("98765 43210"))
    }

    @Test
    fun `passwords stay 8 to 64`() {
        assertTrue(PasswordRules.isValid("abcdefg1"))
        assertFalse(PasswordRules.isValid("a1".repeat(33)))
    }
}
