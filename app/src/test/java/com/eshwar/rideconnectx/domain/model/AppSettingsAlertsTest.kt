package com.eshwar.rideconnectx.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsAlertsTest {
    @Test
    fun alertDefaults() {
        val s = AppSettings()
        assertTrue(s.alertsCluster)
        assertTrue(s.alertsImportant)
        assertFalse(s.alertsMedium)
        assertFalse(s.alertsNameOnly)
    }
}
