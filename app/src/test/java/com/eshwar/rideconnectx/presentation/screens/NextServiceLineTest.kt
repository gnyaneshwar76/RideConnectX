package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.domain.model.ServiceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class NextServiceLineTest {
    private val now get() = System.currentTimeMillis()

    @Test fun noRecordMeansNoBaseline() =
        assertEquals(NextServiceKind.NO_BASELINE, nextServiceLine(ServiceStatus()).kind)

    @Test fun kmLeftWhenFarFromDue() {
        val s = ServiceStatus(lastServiceOdometerKm = 1000, lastServiceAt = now, currentOdometerKm = 1500)
        assertEquals(NextServiceLine(NextServiceKind.KM_LEFT, 2500), nextServiceLine(s))
    }

    @Test fun dueSoonNearTheLimit() {
        val s = ServiceStatus(lastServiceOdometerKm = 1000, lastServiceAt = now, currentOdometerKm = 3800)
        assertEquals(NextServiceKind.DUE_SOON, nextServiceLine(s).kind)
    }

    @Test fun overduePastTheLimit() {
        val s = ServiceStatus(lastServiceOdometerKm = 1000, lastServiceAt = now, currentOdometerKm = 4200)
        assertEquals(NextServiceKind.OVERDUE, nextServiceLine(s).kind)
    }
}
