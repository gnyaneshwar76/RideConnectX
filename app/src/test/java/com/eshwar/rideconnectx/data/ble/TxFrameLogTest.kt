package com.eshwar.rideconnectx.data.ble

import org.junit.Test
import org.junit.Assert.assertEquals

class TxFrameLogTest {
    @Test
    fun formatsUpperHexWithType() {
        val p = ByteArray(30).also { it[0] = 0x7E; it[1] = 0x31; it[2] = 0xAB.toByte() }
        val line = TxFrameLog.format("ACCESS_LCD", p, 7)
        assertEquals("TX model=ACCESS_LCD type=0x31 hex=7E31AB" + "00".repeat(27) + " seq=7", line)
    }
}
