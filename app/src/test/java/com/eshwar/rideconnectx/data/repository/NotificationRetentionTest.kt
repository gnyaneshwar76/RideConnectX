package com.eshwar.rideconnectx.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/** N91 default: keep 90 days or the newest 500 notification rows. */
class NotificationRetentionTest {
    @Test
    fun `retention is 90 days and 500 rows`() {
        assertEquals(90L * 24 * 3600 * 1000, NotificationRepository.RETENTION_MS)
        assertEquals(500, NotificationRepository.KEEP_NEWEST)
    }
}
