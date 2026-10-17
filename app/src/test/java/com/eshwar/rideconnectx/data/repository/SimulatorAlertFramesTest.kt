package com.eshwar.rideconnectx.data.repository

import com.eshwar.rideconnectx.domain.ProtocolEngine as PE
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SimulatorAlertFramesTest {
    private val s = BleRepositorySimulatorImpl.Companion

    @Test fun callNamed() = assertArrayEquals(PE.buildCallPacket("Ravi", false), s.callFrame("Ravi", false))
    @Test fun callFixedLabel() = assertArrayEquals(PE.buildCallPacket("Incoming call", true), s.callFrame("रवि", true))
    @Test fun messageNew() = assertArrayEquals(PE.buildMessagePacket('W', "Ravi", 2, true), s.messageFrame('W', "Ravi", 2, true))
    @Test fun messageLabel() = assertArrayEquals(PE.buildMessagePacket('W', "New message", 1, true), s.messageFrame('W', "", 1, true))
    @Test fun messageRecount() = assertArrayEquals(PE.buildMessagePacket('N', "", 0, false), s.messageFrame('N', "x", 0, false))
}
