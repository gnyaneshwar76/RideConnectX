package com.eshwar.rideconnectx.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** An overdue service reminds once per service cycle, never on every check (N9). */
class ServiceReminderRuleTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private fun status(daysAgo: Int, lastKm: Int = 2_000, nowKm: Int = 2_000) = ServiceStatus(
        lastServiceOdometerKm = lastKm,
        lastServiceAt = System.currentTimeMillis() - daysAgo * day,
        currentOdometerKm = nowKm,
    )

    @Test
    fun `a service 390 days old is overdue and owed a reminder`() {
        // The 26 Sep report: last service 1 Sep 2025, "300 days over".
        val s = status(daysAgo = 390)
        val key = ServiceReminderRule.reminderKey(s, enabled = true)
        assertTrue(key!!.startsWith("overdue@"))
        assertTrue(ServiceReminderRule.shouldNotify(key, lastNotified = ""))
        assertEquals("300 days over", ServiceReminderRule.detail(s))
    }

    @Test
    fun `the same overdue service is not reminded twice`() {
        val key = ServiceReminderRule.reminderKey(status(daysAgo = 390), enabled = true)
        assertFalse(ServiceReminderRule.shouldNotify(key, lastNotified = key!!))
    }

    @Test
    fun `a new service record starts a new cycle`() {
        val old = ServiceReminderRule.reminderKey(status(daysAgo = 390), enabled = true)
        val edited = ServiceReminderRule.reminderKey(status(daysAgo = 200), enabled = true)
        assertNotEquals(old, edited)
    }

    @Test
    fun `due soon, then overdue, are two reminders`() {
        val soon = ServiceReminderRule.reminderKey(status(daysAgo = 85), enabled = true)
        assertTrue(soon!!.startsWith("due_soon@"))
        val over = ServiceReminderRule.reminderKey(status(daysAgo = 85, nowKm = 5_100), enabled = true)
        assertTrue(over!!.startsWith("overdue@"))
    }

    @Test
    fun `nothing is owed when off, fresh, or with no record`() {
        assertNull(ServiceReminderRule.reminderKey(status(daysAgo = 390), enabled = false))
        assertNull(ServiceReminderRule.reminderKey(status(daysAgo = 10), enabled = true))
        assertNull(ServiceReminderRule.reminderKey(ServiceStatus(), enabled = true))
    }
}
