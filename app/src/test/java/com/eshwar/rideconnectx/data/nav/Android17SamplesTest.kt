package com.eshwar.rideconnectx.data.nav

import com.eshwar.rideconnectx.domain.ProtocolEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Real Android 17 Maps live-update frames (template ProgressStyle, text null, read as "").
 * Chips are passed the way the listener reads them: primaryInfo, chipExpandedText, shortCriticalText.
 */
class Android17SamplesTest {

    private fun frame(title: String, shortCritical: String, sub: String, progress: Int, max: Int) =
        MapsNotificationParser.liveUpdateFields(title, "", sub, progress, max, listOf("", "", shortCritical))!!

    @Test
    fun `A17 sample 1 - 70 m turn left, remaining 5_8 km, arrive 1_37 AM`() {
        val f = frame("70 m · Turn left", "70 m", "Arrive 1:37 AM", 0, 5849)
        assertEquals("70 m", f.title)
        assertEquals("Turn left", f.text)
        val m = MapsNotificationParser.parseLive(f)!!
        assertEquals(ProtocolEngine.Maneuver.TURN_LEFT, m.maneuverId)
        assertEquals("70 m", m.distanceToTurn)
        assertEquals("Turn left", m.instruction)
        assertEquals("5.8 km", m.remainingDistance)          // 5849 - 0
        assertEquals(5800, MapsNotificationParser.distanceToMetres(m.remainingDistance))
        assertEquals("0137AM", m.etaClock)
        assertNull(m.etaMinutes)                              // Android 17 gives no minutes
    }

    @Test
    fun `A17 sample 2 - 1_4 km turn right to stay on NH 44, remaining 3_6 km`() {
        val f = frame("1.4 km · Turn right at Madina Cir to stay on NH 44", "1.4 km", "Arrive 1:32 AM", 650, 4297)
        assertEquals("1.4 km", f.title)
        assertEquals("Turn right at Madina Cir to stay on NH 44", f.text)
        val m = MapsNotificationParser.parseLive(f)!!
        assertEquals(ProtocolEngine.Maneuver.TURN_RIGHT, m.maneuverId)
        assertEquals(1400, MapsNotificationParser.distanceToMetres(m.distanceToTurn))
        assertEquals("3.6 km", m.remainingDistance)          // 4297 - 650 = 3647 m
        assertEquals("0132AM", m.etaClock)
    }

    @Test
    fun `A17 samples with non-breaking spaces read the same`() {
        val a = MapsNotificationParser.parseLive(frame("70 m · Turn left", "70 m", "Arrive 1:37 AM", 0, 5849))!!
        assertEquals("70 m", a.distanceToTurn)
        assertEquals("0137AM", a.etaClock)
        val b = MapsNotificationParser.parseLive(frame("1.4 km · Turn right at Madina Cir to stay on NH 44", "1.4 km", "Arrive 1:32 AM", 650, 4297))!!
        assertEquals(ProtocolEngine.Maneuver.TURN_RIGHT, b.maneuverId)
        assertEquals("1.4 km", b.distanceToTurn)
    }

    @Test
    fun `A17 sample 1 builds the expected cluster frame`() {
        val m = MapsNotificationParser.parseLive(frame("70 m · Turn left", "70 m", "Arrive 1:37 AM", 0, 5849))!!
        val p = ProtocolEngine.buildNavigationPacket(
            m.maneuverId, MapsNotificationParser.distanceToMetres(m.distanceToTurn)!!, m.etaClock!!,
            remainingMetres = MapsNotificationParser.distanceToMetres(m.remainingDistance)!!,
        )
        assertEquals(ProtocolEngine.Maneuver.TURN_LEFT.toByte(), p[2])
        assertEquals("0070M", String(p.copyOfRange(4, 9), Charsets.US_ASCII))
        assertEquals(ProtocolEngine.calculateChecksum(p), p[28])
    }
}
