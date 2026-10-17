package com.eshwar.rideconnectx.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ClusterAlertNameTest {
    @Test fun latinNameKept() = assertEquals("Ravi", clusterAlertName("Ravi", "New message"))
    @Test fun blankGivesLabel() = assertEquals("New message", clusterAlertName("", "New message"))
    @Test fun hindiGivesLabel() = assertEquals("Incoming call", clusterAlertName("रवि", "Incoming call"))
}
