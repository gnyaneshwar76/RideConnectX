package com.eshwar.rideconnectx.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundStyleTest {
    @Test fun missingOrUnknownReadsAsPhoto() {
        assertEquals(BackgroundStyle.PHOTO, BackgroundStyle.fromName(null))
        assertEquals(BackgroundStyle.PHOTO, BackgroundStyle.fromName("nonsense"))
        assertEquals(BackgroundStyle.PHOTO, AppSettings().backgroundStyle)
    }

    @Test fun storedNameRoundTrips() {
        assertEquals(BackgroundStyle.GLOW, BackgroundStyle.fromName(BackgroundStyle.GLOW.name))
    }
}
