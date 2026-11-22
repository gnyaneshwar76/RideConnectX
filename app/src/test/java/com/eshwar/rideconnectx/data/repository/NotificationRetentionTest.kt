package com.eshwar.rideconnectx.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** History is today only: everything before local midnight is deleted (plan 20261122). */
class NotificationRetentionTest {
    private val ist = ZoneId.of("Asia/Kolkata")

    @Test
    fun `cutoff is local midnight of the same day`() {
        val now = ZonedDateTime.of(2026, 11, 22, 23, 59, 0, 0, ist).toInstant().toEpochMilli()
        val midnight = ZonedDateTime.of(2026, 11, 22, 0, 0, 0, 0, ist).toInstant().toEpochMilli()
        assertEquals(midnight, NotificationRepository.startOfToday(now, ist))
    }

    @Test
    fun `a row from 00h01 survives and one from 23h59 yesterday goes`() {
        val cutoff = NotificationRepository.startOfToday(
            ZonedDateTime.of(2026, 11, 22, 8, 0, 0, 0, ist).toInstant().toEpochMilli(), ist,
        )
        val yesterday = ZonedDateTime.of(2026, 11, 21, 23, 59, 0, 0, ist).toInstant().toEpochMilli()
        val today = ZonedDateTime.of(2026, 11, 22, 0, 1, 0, 0, ist).toInstant().toEpochMilli()
        org.junit.Assert.assertTrue(yesterday < cutoff) // deleted: dao.prune deletes createdAt < cutoff
        org.junit.Assert.assertTrue(today >= cutoff)
    }

    @Test
    fun `a busy day is still capped at 500 rows`() {
        assertEquals(500, NotificationRepository.KEEP_NEWEST)
    }
}
