package com.eshwar.rideconnectx.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReportProblemTest {
    @Test fun subjectUsesVersion() = assertEquals("RideConnectX 1.0.0-tester report", reportSubject("1.0.0-tester"))

    @Test fun bodyIsExactAndHasNoPersonalData() {
        val b = reportBody("1.0.0-tester", "Pixel 10", "16")
        assertEquals(
            "App version: 1.0.0-tester\nPhone: Pixel 10\nAndroid: 16\n\n" +
                "What I did:\n\n\nWhat I expected:\n\n\nWhat happened:\n\n\nScreenshot attached? (yes/no)\n", b)
        assertFalse(b.contains("@"))
        assertFalse(Regex("[0-9]+[.][0-9]{3,}").containsMatchIn(b))
    }
}
