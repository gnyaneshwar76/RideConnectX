package com.eshwar.rideconnectx.core.util

import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictedGuideTest {

    @Test
    fun `shown for a sideloaded app on Android 13+ with access still off`() {
        assertTrue(shouldShowRestrictedGuide(sdkInt = 33, installer = null, granted = false))
        assertTrue(shouldShowRestrictedGuide(sdkInt = 36, installer = "com.google.android.packageinstaller", granted = false))
    }

    @Test
    fun `not shown from the Play Store, below Android 13, or once access is on`() {
        assertTrue(!shouldShowRestrictedGuide(sdkInt = 34, installer = "com.android.vending", granted = false))
        assertTrue(!shouldShowRestrictedGuide(sdkInt = 32, installer = null, granted = false))
        assertTrue(!shouldShowRestrictedGuide(sdkInt = 34, installer = null, granted = true))
    }
}
