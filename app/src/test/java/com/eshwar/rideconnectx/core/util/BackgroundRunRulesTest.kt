package com.eshwar.rideconnectx.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundRunRulesTest {
    @Test fun popupOnlyWhenOffNeverShownAndFirstOpen() {
        for (enabled in listOf(false, true)) for (shown in listOf(false, true)) for (first in listOf(false, true)) {
            assertEquals(!enabled && !shown && first, BackgroundRunRules.showPopup(enabled, shown, first))
        }
    }

    @Test fun reminderOnlyWhileOff() {
        assertEquals(true, BackgroundRunRules.showReminder(false))
        assertEquals(false, BackgroundRunRules.showReminder(true))
    }
}
