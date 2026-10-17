package com.eshwar.rideconnectx.data.repository

import com.eshwar.rideconnectx.domain.ProtocolEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class LaneCodeTest {
    private fun code(name: String, c: Int) = laneCodeFor(ProtocolEngine.profileFor(name), c)

    @Test fun accessFamilyUnchanged() {
        for (n in listOf("SAS210217219", "SAS01xxxxxxx", "SAS02xxxxxxx", "SAS11xxxxxxx"))
            for (c in listOf(74, 58, 1, 4, 40)) assertEquals(c, code(n, c))
    }

    @Test fun motorcycleUsesModelCode() {
        assertEquals(44, code("SBM11xxxxxxx", 74))
        assertEquals(46, code("SBM11xxxxxxx", 58))
        assertEquals(1, code("SBM11xxxxxxx", 1))
    }
}
