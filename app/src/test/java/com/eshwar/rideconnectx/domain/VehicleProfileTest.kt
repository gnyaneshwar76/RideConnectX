package com.eshwar.rideconnectx.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-model profiles (Plan B, 14 Oct 2026).
 *
 * The first test pins the Access family: the hex strings were built from the
 * code as it was BEFORE profiles existed. If any of them changes, the Access
 * 125 path changed, which the owner has not allowed.
 */
class VehicleProfileTest {

    @After
    fun reset() = ProtocolEngine.configureForDevice(null)

    private fun hex(p: ByteArray) = p.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    private fun allPackets(): List<String> = listOf(
        ProtocolEngine.buildNavigationPacket(4, 299, "0613PM", remainingMetres = 2500),
        ProtocolEngine.buildHeartbeatPacket(2, false, 3, 0, "181530", true, false),
        ProtocolEngine.buildProfilePacket("Eshwar", 'R'),
        ProtocolEngine.buildCallPacket("Ravi"),
        ProtocolEngine.buildMissedCallPacket(2, "+91 98480"),
        ProtocolEngine.buildMessagePacket('W', "Mom", 3),
    ).map(::hex)

    @Test
    fun `access family packets are byte for byte what they were before profiles`() {
        val before = listOf(
            "A53104FF303239394D30363133504DFFFFFF30322E354B3131FFFFFFD77F",
            "A533324EFFFFFF33313831353330594EFFFFFFFFFFFFFFFFFFFFFFFF4F7F",
            "A5364573687761720000000000000000000000000000FFFFFFFFFF52127F",
            "A53252617669000000000000000000000000000000004E31FFFFFFFFC07F",
            "A534FF0239312039383438300000000000000000000000004EFFFFFFE87F",
            "A535FF59034D6F6D000000000000000000000000000000000057FFFFF17F",
        )
        for (name in listOf("SAS210217219", "SAS010000000", "SAS020000000", "SAS110000000", "SAS120000000", null)) {
            ProtocolEngine.configureForDevice(name)
            assertEquals("name $name", before, allPackets())
        }
    }

    @Test
    fun `name codes map to the official models and checksum branch`() {
        val cases = mapOf(
            "SAS210217219" to Triple("Access", ClusterFamily.ACCESS_LCD, true),
            "SAS010000000" to Triple("Access 125", ClusterFamily.ACCESS_LCD, true),
            "SAS120000000" to Triple("Burgman Street", ClusterFamily.ACCESS_LCD, true),
            "SCE100000000" to Triple("e-ACCESS", ClusterFamily.ELECTRIC, false),
            "SCS010000000" to Triple("Access-TFT Edition", ClusterFamily.TFT, false),
            "SCS110000000" to Triple("Burgman Street-TFT Edition", ClusterFamily.TFT, false),
            "SBS220000000" to Triple("Avenis", ClusterFamily.SCOOTER_LCD, false),
            "SBS310000000" to Triple("Burgman Street EX", ClusterFamily.SCOOTER_LCD, false),
            "SBS510000000" to Triple("Burgman Street", ClusterFamily.SCOOTER_LCD, false),
            "SBM010000000" to Triple("V-STROM SX", ClusterFamily.MOTORCYCLE, false),
            "SBM110000000" to Triple("GIXXER / GIXXER SF", ClusterFamily.MOTORCYCLE, false),
            "SBM220000000" to Triple("GIXXER 250 / SF 250", ClusterFamily.MOTORCYCLE, false),
            // Unlisted names keep today's behaviour.
            "SCS210000000" to Triple("", ClusterFamily.UNKNOWN_SUM, false),
            "SAS310000000" to Triple("", ClusterFamily.UNKNOWN, true),
        )
        for ((name, want) in cases) {
            val p = ProtocolEngine.profileFor(name)
            assertEquals(name, want.first, p.model)
            assertEquals(name, want.second, p.family)
            assertEquals(name, want.third, p.complementChecksum)
        }
    }

    @Test
    fun `b family checksum is the plain sum`() {
        ProtocolEngine.configureForDevice("SBS210000000")
        val p = ProtocolEngine.buildProfilePacket("Eshwar", 'R')
        var sum = 0
        for (i in 1..27) sum += p[i].toInt() and 0xFF
        assertEquals((sum and 0xFF).toByte(), p[28])
    }

    @Test
    fun `tft clusters blank a leading zero hour at byte 9`() {
        ProtocolEngine.configureForDevice("SCS010000000")
        assertEquals(' '.code.toByte(), ProtocolEngine.buildNavigationPacket(4, 299, "0613PM")[9])
        assertEquals('1'.code.toByte(), ProtocolEngine.buildNavigationPacket(4, 299, "1013PM")[9])
        ProtocolEngine.configureForDevice("SBS210000000")
        assertEquals('0'.code.toByte(), ProtocolEngine.buildNavigationPacket(4, 299, "0613PM")[9])
    }

    @Test
    fun `scan rule is 12 characters with SA SB or SC`() {
        assertTrue(ProtocolEngine.isClusterName("SAS210217219"))
        assertTrue(ProtocolEngine.isClusterName("SBM010000000"))
        assertTrue(ProtocolEngine.isClusterName("SCE100000000"))
        assertFalse(ProtocolEngine.isClusterName("SAS21021721"))
        assertFalse(ProtocolEngine.isClusterName("XYZ123456789"))
        assertFalse(ProtocolEngine.isClusterName(null))
    }

    @Test
    fun `lane codes 74 and 58 follow the model`() {
        val access = ProtocolEngine.profileFor("SAS210217219")
        assertEquals(38, access.officialLaneCode(74))
        assertEquals(44, access.officialLaneCode(58))
        val access125 = ProtocolEngine.profileFor("SAS010000000")
        assertEquals(44, access125.officialLaneCode(74))
        assertEquals(46, access125.officialLaneCode(58))
        assertNull(access125.officialLaneCode(1))
        assertNull(ProtocolEngine.profileFor("SBS510000000").officialLaneCode(74))
    }

    @Test
    fun `battery percent is read only for the e-ACCESS`() {
        val frame = ByteArray(30)
        frame[0] = 0xA5.toByte(); frame[1] = 0x37
        for (i in 2..22) frame[i] = '0'.code.toByte()
        frame[24] = (64 + 80).toByte()
        frame[28] = ProtocolEngine.calculateChecksum(frame)
        frame[29] = 0x7F

        ProtocolEngine.configureForDevice("SCE100000000")
        assertEquals(80, ProtocolEngine.parseTelemetry(frame)?.batteryPercent)

        ProtocolEngine.configureForDevice("SAS210217219")
        assertNull(ProtocolEngine.parseTelemetry(frame)?.batteryPercent)
    }
}
