package com.eshwar.rideconnectx.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phone-number handling for Screen 19's emergency contacts.
 *
 * Duplicate prevention rests entirely on normalisation — two spellings of the
 * same number must collapse to one key, or the unique index never fires.
 */
class SafetyRepositoryTest {

    @Test
    fun `normalisation strips everything a human types around the digits`() {
        assertEquals("9876543210", SafetyRepository.normalize("98765 43210"))
        assertEquals("9876543210", SafetyRepository.normalize("(98765) 43210"))
        assertEquals("9876543210", SafetyRepository.normalize("98765-43210"))
        assertEquals("9876543210", SafetyRepository.normalize("  9876543210  "))
    }

    @Test
    fun `a leading plus is kept because it changes what the number means`() {
        assertEquals("+919876543210", SafetyRepository.normalize("+91 98765 43210"))
        assertEquals("+919876543210", SafetyRepository.normalize("+91-98765-43210"))
    }

    @Test
    fun `the same number written four ways normalises to one key`() {
        val spellings = listOf("9876543210", "98765 43210", "98765-43210", "(98765)43210")
        val keys = spellings.map(SafetyRepository::normalize).toSet()
        assertEquals(1, keys.size)
    }

    @Test
    fun `plausible numbers are accepted`() {
        assertTrue(SafetyRepository.isValidPhone("9876543210"))      // India, 10 digits
        assertTrue(SafetyRepository.isValidPhone("+919876543210"))   // with country code
        assertTrue(SafetyRepository.isValidPhone("0401234567"))      // 10, the short end (N16)
        assertTrue(SafetyRepository.isValidPhone("123456789012345")) // 15, E.164's ceiling
    }

    @Test
    fun `too short or too long is refused`() {
        assertFalse(SafetyRepository.isValidPhone(""))
        assertFalse(SafetyRepository.isValidPhone("123"))
        assertFalse(SafetyRepository.isValidPhone("123456789"))       // 9: below N16's 10
        assertFalse(SafetyRepository.isValidPhone("1234567890123456"))
    }

    @Test
    fun `validation counts digits, not the plus`() {
        // 10 digits with a plus is still 10 digits.
        assertTrue(SafetyRepository.isValidPhone("+1234567890"))
    }
}
