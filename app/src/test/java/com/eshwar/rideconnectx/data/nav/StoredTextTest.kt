package com.eshwar.rideconnectx.data.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class StoredTextTest {
    @Test fun stripsBidiMarksAndTrims() {
        assertEquals("Amma", cleanStoredText("‪ Amma‬‎ "))
        assertEquals("Ravi K", cleanStoredText("⁦Ravi K⁩‏"))
        assertEquals("", cleanStoredText("‎ ‏"))
    }
}

class RingingCallKeyTest {
    @org.junit.Test
    fun bidiAndPlainCallerAreOneRingingEntry() {
        ClusterAlerts.reset()
        ClusterAlerts.onCallRinging("‪+91 98765 43210‬", false)
        ClusterAlerts.onCallRinging("+91 98765 43210", false)
        org.junit.Assert.assertEquals("+91 98765 43210", ClusterAlerts.callRinging.value?.caller)
        ClusterAlerts.onCallEnded("+91 98765 43210", ClusterAlerts.CallEnd.MISSED)
        org.junit.Assert.assertNull(ClusterAlerts.callRinging.value)
        ClusterAlerts.reset()
    }

    @org.junit.Test
    fun staleRingingEntryIsClearedByTimer() {
        ClusterAlerts.reset()
        val old = ClusterAlerts.ringMaxMs
        ClusterAlerts.ringMaxMs = 50
        try {
            ClusterAlerts.onCallRinging("Amma", false)
            org.junit.Assert.assertNotNull(ClusterAlerts.callRinging.value)
            Thread.sleep(400)
            org.junit.Assert.assertNull(ClusterAlerts.callRinging.value)
        } finally { ClusterAlerts.ringMaxMs = old; ClusterAlerts.reset() }
    }
}
