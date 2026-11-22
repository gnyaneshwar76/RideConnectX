package com.eshwar.rideconnectx.data.repository

import com.eshwar.rideconnectx.domain.ProtocolEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleRepositorySimulatorFrameTest {
    @After fun reset() = ProtocolEngine.configureForDevice(null)

    @Test fun accessNameUsesComplementChecksum() {
        val line = BleRepositorySimulatorImpl.configureAndDescribe("SAS210217219")
        assertTrue(line, line.contains("checksum=complement") && line.contains("type=0x31"))
        assertEquals("Access", ProtocolEngine.profile.model)
    }

    @Test fun reconnectOnlyWithSavedAddressAndNoLink() {
        val idle = com.eshwar.rideconnectx.domain.repository.ConnectionState.Idle
        val up = com.eshwar.rideconnectx.domain.repository.ConnectionState.Connected("A", "n")
        assertTrue(BleRepositorySimulatorImpl.shouldReconnect("A", idle))
        assertTrue(!BleRepositorySimulatorImpl.shouldReconnect(null, idle))
        assertTrue(!BleRepositorySimulatorImpl.shouldReconnect("", idle))
        assertTrue(!BleRepositorySimulatorImpl.shouldReconnect("A", up))
    }

    @Test fun otherModelsFollowTheName() {
        assertTrue(BleRepositorySimulatorImpl.configureAndDescribe("SBM010000000").contains("checksum=sum"))
        assertEquals("V-STROM SX", ProtocolEngine.profile.model)
        assertTrue(BleRepositorySimulatorImpl.configureAndDescribe("SCE100000000").contains("checksum=sum"))
    }
}
