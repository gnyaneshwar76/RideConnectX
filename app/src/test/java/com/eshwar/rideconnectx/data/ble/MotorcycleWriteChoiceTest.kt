package com.eshwar.rideconnectx.data.ble

import android.bluetooth.BluetoothGattCharacteristic as C
import com.eshwar.rideconnectx.domain.ClusterFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorcycleWriteChoiceTest {
    private val w = C.PROPERTY_WRITE
    private val wnr = C.PROPERTY_WRITE_NO_RESPONSE
    private val n = C.PROPERTY_NOTIFY

    @Test fun `first characteristic wins when writable`() =
        assertEquals(0, pickMotorcycleWriteIndex(listOf("FFF1" to wnr, "FFF2" to w)))

    @Test fun `FFF2 wins when the first is not writable`() =
        assertEquals(2, pickMotorcycleWriteIndex(listOf("FFF1" to n, "FFF3" to w, "FFF2" to wnr)))

    @Test fun `first writable when there is no FFF2`() =
        assertEquals(1, pickMotorcycleWriteIndex(listOf("FFF1" to n, "FFF3" to w, "FFF4" to w)))

    @Test fun `null when nothing is writable`() {
        assertNull(pickMotorcycleWriteIndex(listOf("FFF1" to n, "FFF2" to n)))
        assertNull(pickMotorcycleWriteIndex(emptyList()))
    }

    @Test fun `only the motorcycle family takes the FFF0 path and indicate subscribe`() {
        assertTrue(usesFff0Path(ClusterFamily.MOTORCYCLE))
        for (f in ClusterFamily.values().filter { it != ClusterFamily.MOTORCYCLE }) {
            assertFalse(f.name, usesFff0Path(f))
            assertFalse(f.name, shouldSubscribe(C.PROPERTY_INDICATE, f))
            assertTrue(f.name, shouldSubscribe(n, f))
        }
        assertTrue(shouldSubscribe(C.PROPERTY_INDICATE, ClusterFamily.MOTORCYCLE))
        assertFalse(shouldSubscribe(w, ClusterFamily.MOTORCYCLE))
    }

    @Test fun `saved name is used only when the device gives no name`() {
        assertEquals("SBM010000001", ClusterNameChoice.pick(null, null, "SBM010000001"))
        assertEquals("SAS210217219", ClusterNameChoice.pick("SAS210217219", null, "SBM010000001"))
    }
}
