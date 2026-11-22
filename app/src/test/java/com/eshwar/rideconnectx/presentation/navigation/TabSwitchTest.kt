package com.eshwar.rideconnectx.presentation.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only a move between two different dock tabs gets the calm fade. */
class TabSwitchTest {
    @Test
    fun tabToTabFades() {
        assertTrue(isTabSwitch(Routes.DASHBOARD, Routes.NAVIGATION))
        assertTrue(isTabSwitch(Routes.SETTINGS, Routes.STATS))
    }

    @Test
    fun deeperScreensSlide() {
        assertFalse(isTabSwitch(Routes.DASHBOARD, Routes.SERVICE))
        assertFalse(isTabSwitch(Routes.STATS, Routes.RIDE))
        assertFalse(isTabSwitch(Routes.SPLASH, Routes.DASHBOARD))
        assertFalse(isTabSwitch(null, Routes.DASHBOARD))
        assertFalse(isTabSwitch(Routes.DASHBOARD, Routes.DASHBOARD))
    }
}
