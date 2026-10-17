package com.eshwar.rideconnectx.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class RoutePolylineTest {
    @Test fun googleSampleEncodes() {
        val pts = listOf(LatLon(38.5, -120.2), LatLon(40.7, -120.95), LatLon(43.252, -126.453))
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", RoutePolyline.encode(pts))
    }

    @Test fun roundTrip() {
        val pts = List(50) { LatLon(12.9 + it * 0.00123, 77.5 - it * 0.00077) }
        val back = RoutePolyline.decode(RoutePolyline.encode(pts))
        assertEquals(pts.size, back.size)
        pts.zip(back).forEach { (a, b) -> assertTrue(a.distanceTo(b) < 1.5) }
    }

    @Test fun badInputDoesNotThrow() {
        RoutePolyline.decode("!!!")
        assertEquals(0, RoutePolyline.decode("").size)
    }

    @Test fun simplifyKeepsEndsAndDropsStraightRun() {
        val line = List(100) { LatLon(12.0 + it * 0.0001, 77.0) }
        val s = RoutePolyline.simplify(line, 5.0)
        assertEquals(2, s.size)
        assertEquals(line.first(), s.first())
        assertEquals(line.last(), s.last())
    }

    @Test fun capIsHonoured() {
        // 20 000 points of a 40 m wiggle over about 40 km
        val wiggle = List(20_000) { LatLon(12.0 + it * 0.00002, 77.0 + 0.0003 * sin(it / 3.0)) }
        val s = RoutePolyline.simplifyCapped(wiggle)
        assertTrue(s.size <= RoutePolyline.MAX_POINTS)
        assertEquals(wiggle.first(), s.first())
        assertEquals(wiggle.last(), s.last())
    }
}
