package com.eshwar.rideconnectx.data.ble

import com.eshwar.rideconnectx.domain.ClusterFamily
import com.eshwar.rideconnectx.domain.ProtocolEngine
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkDiagnosticsTest {
    @After
    fun back() = ProtocolEngine.configureForDevice(null)

    private fun beat() = ProtocolEngine.buildHeartbeatPacket(
        batteryBucket = 5, isCharging = false, signalBars = 3, clockHHmmss = "101500",
        notificationPending = false, missedCallPending = false,
    )

    private fun sum27(p: ByteArray) = (1..27).sumOf { p[it].toInt() and 0xFF } and 0xFF

    @Test
    fun `the service's new name wins over the saved old name`() {
        assertEquals("SBM010000001", ClusterNameChoice.pick("SBM010000001", "Scooter", "SAS210217219"))
        assertEquals("SBM010000001", ClusterNameChoice.pick(null, "SBM010000001", "SAS210217219"))
        assertEquals("SAS210217219", ClusterNameChoice.pick(null, "Scooter", "SAS210217219"))
        assertNull(ClusterNameChoice.pick(null, null, null))
    }

    @Test
    fun `access name gives the same bytes as before with complement checksum`() {
        ProtocolEngine.configureForDevice("SAS210217219")
        val before = beat()
        ProtocolEngine.configureForDevice(ClusterNameChoice.pick("SAS210217219", "Scooter", "SAS210217219"))
        val after = beat()
        assertArrayEquals(before, after)
        assertEquals((sum27(after).inv() and 0xFF), after[28].toInt() and 0xFF)
    }

    @Test
    fun `motorcycle name gives a plain sum in byte 28`() {
        ProtocolEngine.configureForDevice(ClusterNameChoice.pick("SBM010000001", "Scooter", "SAS210217219"))
        val p = beat()
        assertEquals(sum27(p), p[28].toInt() and 0xFF)
    }

    @Test
    fun `only the motorcycle family bonds, and only when not bonded`() {
        assertTrue(BondPolicy.shouldCreateBond(ClusterFamily.MOTORCYCLE, true))
        assertFalse(BondPolicy.shouldCreateBond(ClusterFamily.MOTORCYCLE, false))
        for (f in ClusterFamily.values().filter { it != ClusterFamily.MOTORCYCLE }) {
            assertFalse(f.name, BondPolicy.shouldCreateBond(f, true))
        }
    }

    @Test
    fun `waiting for the bike is motorcycle only and ends when the service is found`() {
        val s = LinkDiagnostics.Snapshot(family = ClusterFamily.MOTORCYCLE)
        assertTrue(s.awaitingBike)
        assertTrue(s.copy(serviceFound = false).awaitingBike)
        assertFalse(s.copy(serviceFound = true).awaitingBike)
        assertFalse(LinkDiagnostics.Snapshot(family = ClusterFamily.ACCESS_LCD).awaitingBike)
    }

    @Test
    fun `counts packets by type and short uuids`() {
        LinkDiagnostics.reset()
        val p = ByteArray(30).also { it[1] = 0x33 }
        LinkDiagnostics.onPacketSent(p, complement = false)
        LinkDiagnostics.onPacketSent(p, complement = false)
        assertEquals(2, LinkDiagnostics.state.value.sent[0x33])
        assertEquals(true, LinkDiagnostics.state.value.plainSumSent)
        assertEquals("FEFB", LinkDiagnostics.shortUuid("0000fefb-0000-1000-8000-00805f9b34fb"))
    }
}
