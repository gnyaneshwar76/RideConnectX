package com.eshwar.rideconnectx.data

import com.eshwar.rideconnectx.data.local.ServicePreferencesStore.RefusedStreak
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceOdometerRecoveryTest {

    @Test
    fun `garbage readings that disagree are never believed`() {
        val r = RefusedStreak()
        assertFalse(r.believe(2248)); assertFalse(r.believe(91000)); assertFalse(r.believe(130))
        assertFalse(r.believe(70000)); assertFalse(r.believe(500))
    }

    @Test
    fun `a real reading refused three times in a row is believed`() {
        val r = RefusedStreak()
        assertFalse(r.believe(2248))
        assertFalse(r.believe(2248))
        assertTrue(r.believe(2249))
    }
}
