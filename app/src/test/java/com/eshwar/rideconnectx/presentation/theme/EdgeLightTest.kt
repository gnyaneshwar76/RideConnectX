package com.eshwar.rideconnectx.presentation.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/** The edge light stays calm at every glass setting, in both themes. */
class EdgeLightTest {
    @Test
    fun darkStaysFaint() {
        listOf(0f, 0.3f, 0.6f, 1f).forEach { assertTrue(edgeLightColor(true, it).alpha in 0.245f..0.555f) }
    }

    @Test
    fun lightStaysSoft() {
        listOf(0f, 0.3f, 0.6f, 1f).forEach { assertTrue(edgeLightColor(false, it).alpha in 0.345f..0.705f) }
    }
}
