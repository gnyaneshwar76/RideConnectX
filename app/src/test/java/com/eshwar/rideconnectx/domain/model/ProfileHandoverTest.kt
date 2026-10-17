package com.eshwar.rideconnectx.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** The local profile only moves between accounts when the rider asked (N4). */
class ProfileHandoverTest {

    @Test
    fun `an account keeps its own profile`() {
        assertEquals(HandoverAction.KEEP, ProfileHandover.decide("uidA", "uidA", carry = false, accountHasProfile = true))
    }

    @Test
    fun `someone else's leftover profile is cleared, not carried`() {
        // The 26 Sep leak: a guest's name prefilled a different Google account.
        assertEquals(HandoverAction.CLEAR, ProfileHandover.decide("guest", "uidB", carry = false, accountHasProfile = false))
        assertEquals(HandoverAction.CLEAR, ProfileHandover.decide("uidA", "uidB", carry = false, accountHasProfile = false))
        // Unknown owner (saved before owners were recorded) is nobody's.
        assertEquals(HandoverAction.CLEAR, ProfileHandover.decide("", "uidB", carry = false, accountHasProfile = false))
    }

    @Test
    fun `a profile the rider chose to take goes to an empty account`() {
        assertEquals(HandoverAction.CARRY, ProfileHandover.decide("guest", "uidB", carry = true, accountHasProfile = false))
        assertEquals(HandoverAction.CARRY, ProfileHandover.decide("uidA", "uidB", carry = true, accountHasProfile = false))
    }

    @Test
    fun `an account's own profile wins over a carried one`() {
        assertEquals(HandoverAction.CLEAR, ProfileHandover.decide("uidA", "uidB", carry = true, accountHasProfile = true))
    }

    @Test
    fun `a guest meeting an account with a profile is asked, never switched silently`() {
        assertEquals(
            HandoverAction.ASK,
            ProfileHandover.decide("guest", "uidB", carry = true, accountHasProfile = true, localIsGuest = true),
        )
        // A guest going to an empty account needs no question.
        assertEquals(
            HandoverAction.CARRY,
            ProfileHandover.decide("guest", "uidB", carry = true, accountHasProfile = false, localIsGuest = true),
        )
    }

    @Test
    fun `an unfinished profile is never carried into another account`() {
        // N4 rework: a half-made guest profile must not become the account's.
        assertEquals(
            HandoverAction.CLEAR,
            ProfileHandover.decide("guest", "uidB", carry = true, accountHasProfile = false, localIsGuest = true, localComplete = false),
        )
    }
}
