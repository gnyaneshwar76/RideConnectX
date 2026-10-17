package com.eshwar.rideconnectx

import com.eshwar.rideconnectx.presentation.viewmodel.odometerBounds
import org.junit.Assert.assertEquals
import org.junit.Test

/** The odometer rule follows the service date (rider request, 20 Sep 2026). */
class OdometerBoundsTest {
    private val day = 86_400_000L
    private val now = 1_790_000_000_000L // a fixed "today"

    @Test fun `today - floor is highest earlier record and current reading`() {
        val others = listOf((now - 30 * day) to 2_000)
        assertEquals(2_427 to null, odometerBounds(others, now, currentKm = 2_427, now = now))
    }

    @Test fun `past date - current reading becomes a ceiling, not a floor`() {
        assertEquals(0 to 2_427, odometerBounds(emptyList(), now - 365 * day, 2_427, now))
    }

    @Test fun `back-filled between two records - bounded by both`() {
        val others = listOf((now - 200 * day) to 1_000, (now - 10 * day) to 2_000)
        assertEquals(1_000 to 2_000, odometerBounds(others, now - 100 * day, 2_427, now))
    }

    @Test fun `nothing known - no bounds`() {
        assertEquals(0 to null, odometerBounds(emptyList(), now, 0, now))
    }
}
