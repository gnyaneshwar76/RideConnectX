package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.data.nav.MapsNotificationParser
import org.junit.Assert.assertEquals
import org.junit.Test

class EtaTileTextTest {

    @Test
    fun `minutes win when Maps states them`() {
        assertEquals("14 min", etaTileText(14, "1221AM"))
    }

    @Test
    fun `no minutes - the arrival clock in readable form`() {
        assertEquals("8:48 pm", etaTileText(null, "0848PM"))
        assertEquals("12:05 am", etaTileText(null, "1205AM"))
        assertEquals("11:30 am", etaTileText(null, "1130AM"))
    }

    @Test
    fun `neither, or a clock it cannot read - a dash`() {
        assertEquals("—", etaTileText(null, null))
        assertEquals("—", etaTileText(null, ""))
        assertEquals("—", etaTileText(null, "8:48"))
    }

    @Test
    fun `Android 16 and 17 Arrive line reaches the tile`() {
        val f = MapsNotificationParser.liveUpdateFields("70 m · Turn left", "", "Arrive 8:48 pm", 0, 5849, listOf("", "", "70 m"))!!
        val m = MapsNotificationParser.parseLive(f)!!
        assertEquals(null, m.etaMinutes)
        assertEquals("8:48 pm", etaTileText(m.etaMinutes, m.etaClock))
    }
}
