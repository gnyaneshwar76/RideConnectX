package com.eshwar.rideconnectx.data

import com.eshwar.rideconnectx.data.local.ServicePreferencesStore.Companion.isPlausibleOdometer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the guard that stops one corrupt telemetry frame from permanently
 * inflating the service odometer. Telemetry is accepted without verifying the
 * checksum in byte 28, so this is the only thing standing between a garbled
 * frame and a dashboard stuck at 6,001,923 km.
 */
class OdometerPlausibilityTest {

    @Test
    fun `normal ride advances the reading`() {
        assertTrue(isPlausibleOdometer(known = 2248, km = 2301))
    }

    @Test
    fun `first ever reading is accepted`() {
        assertTrue(isPlausibleOdometer(known = 0, km = 2248))
    }

    @Test
    fun `odometer never goes backwards`() {
        assertFalse(isPlausibleOdometer(known = 2248, km = 2247))
    }

    @Test
    fun `the frame that poisoned the real cache is rejected`() {
        assertFalse(isPlausibleOdometer(known = 2248, km = 6_001_923))
        assertFalse(isPlausibleOdometer(known = 0, km = 6_001_923))
    }

    @Test
    fun `an in-range but impossible jump is rejected`() {
        assertFalse(isPlausibleOdometer(known = 2248, km = 92_248))
    }

    @Test
    fun `a season away from the app still lands`() {
        assertTrue(isPlausibleOdometer(known = 2248, km = 7000))
    }

    @Test
    fun `a cache already poisoned heals on the next real reading`() {
        assertTrue(isPlausibleOdometer(known = 6_001_923, km = 2248))
    }
}
