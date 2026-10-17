package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.data.local.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRouteTest {
    @Test fun empty() = assertTrue(projectRoute(emptyList(), 100f, 100f, 10f).isEmpty())

    @Test fun onePointIsCentred() {
        val p = projectRoute(listOf(LatLon(12.9, 77.6)), 200f, 100f, 10f).single()
        assertEquals(100f, p.x, 0.5f); assertEquals(50f, p.y, 0.5f)
    }

    @Test fun anyShapeStaysInsideThePadding() {
        val pts = listOf(LatLon(12.0, 77.0), LatLon(12.5, 77.01), LatLon(12.2, 77.4))
        projectRoute(pts, 300f, 200f, 20f).forEach {
            assertTrue(it.x in 19.9f..280.1f && it.y in 19.9f..180.1f)
        }
    }

    @Test fun straightLineDoesNotBlowUp() {
        val p = projectRoute(listOf(LatLon(12.0, 77.0), LatLon(12.1, 77.0)), 100f, 100f, 10f)
        assertTrue(p.all { it.x.isFinite() && it.y.isFinite() })
    }
}
