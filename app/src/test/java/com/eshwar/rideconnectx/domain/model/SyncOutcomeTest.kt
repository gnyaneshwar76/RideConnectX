package com.eshwar.rideconnectx.domain.model

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

/** Sign-in stays a success whatever the cloud write did; this only words the cloud side. */
class SyncOutcomeTest {

    @Test
    fun `no error is synced, io error is offline, anything else failed`() {
        assertEquals(SyncOutcome.SYNCED, SyncOutcome.of(null))
        assertEquals(SyncOutcome.OFFLINE, SyncOutcome.of(IOException("timeout")))
        assertEquals(SyncOutcome.FAILED, SyncOutcome.of(IllegalStateException("denied")))
    }

    @Test
    fun `plain success defaults to synced`() {
        assertEquals(SyncOutcome.SYNCED, AuthResult.Success(UserSession()).sync)
    }

    @Test
    fun `password message follows the rule`() {
        assertEquals(true, PasswordRules.RULE_TEXT.contains("${PasswordRules.MIN} characters"))
    }
}
