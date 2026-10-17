package com.eshwar.rideconnectx.domain.model

import com.eshwar.rideconnectx.domain.repository.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Connection alerts" tells the rider about connects and drops only (AUD-2). */
class ConnectionAlertRuleTest {
    private val live = ConnectionState.Connected("AA", "Access")

    @Test
    fun `connecting is an alert`() {
        assertEquals(ConnectionAlert.CONNECTED, ConnectionAlertRule.alertFor(ConnectionState.Connecting("AA", "Access"), live))
    }

    @Test
    fun `a dropped link is an alert`() {
        assertEquals(ConnectionAlert.LOST, ConnectionAlertRule.alertFor(live, ConnectionState.Disconnected("timeout")))
        assertEquals(ConnectionAlert.LOST, ConnectionAlertRule.alertFor(live, ConnectionState.Failed("gatt 133")))
    }

    @Test
    fun `the rider's own disconnect and repeats are not`() {
        assertNull(ConnectionAlertRule.alertFor(live, ConnectionState.Disconnecting))
        assertNull(ConnectionAlertRule.alertFor(ConnectionState.Disconnecting, ConnectionState.Disconnected("user")))
        assertNull(ConnectionAlertRule.alertFor(live, live))
        // The real Disconnect goes straight from Connected (was alerted as "dropped").
        assertNull(ConnectionAlertRule.alertFor(live, ConnectionState.Disconnected("Disconnected", byUser = true)))
    }
}
