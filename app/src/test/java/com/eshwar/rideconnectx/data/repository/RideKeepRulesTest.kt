package com.eshwar.rideconnectx.data.repository

import com.eshwar.rideconnectx.data.local.db.KeepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideKeepRulesTest {
    private val day = 24L * 3600 * 1000

    @Test fun expiresThirtyDaysAfterEnd() {
        assertEquals(1_000L + 30 * day, RideKeepRules.expiresAt(1_000L))
    }

    @Test fun purgePicksOnlyPendingAndExpired() {
        val now = 100 * day
        assertTrue(RideKeepRules.isPurgeable(KeepState.PENDING, now - 1, now))
        assertTrue(RideKeepRules.isPurgeable(KeepState.PENDING, now, now))
        assertFalse(RideKeepRules.isPurgeable(KeepState.PENDING, now + 1, now))
        assertFalse(RideKeepRules.isPurgeable(KeepState.KEPT, null, now))
        assertFalse(RideKeepRules.isPurgeable(KeepState.KEPT, now - 1, now))
        assertFalse(RideKeepRules.isPurgeable(KeepState.DELETED, now - 1, now))
        assertFalse(RideKeepRules.isPurgeable(KeepState.PENDING, null, now))
    }

    @Test fun slotPickerTakesLowestFreeAndStopsAtTen() {
        assertEquals("r0", RideKeepRules.pickSlot(emptySet()))
        assertEquals("r2", RideKeepRules.pickSlot(setOf("r0", "r1", "r5")))
        assertEquals("r1", RideKeepRules.pickSlot(setOf("r0", "r2")))
        assertNull(RideKeepRules.pickSlot((0..9).map { "r$it" }.toSet()))
    }

    @Test fun dedupeByStartedAt() {
        val slots = mapOf("r0" to 111L, "r3" to 222L)
        assertEquals("r3", RideKeepRules.slotWithStart(slots, 222L))
        assertNull(RideKeepRules.slotWithStart(slots, 333L))
    }
}
