package com.eshwar.rideconnectx.presentation.viewmodel

import com.eshwar.rideconnectx.data.local.db.NotificationEntity
import com.eshwar.rideconnectx.data.local.db.NotificationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class NotificationGroupingTest {
    private val utc = ZoneOffset.UTC
    private fun at(day: Int, hour: Int) = LocalDate.of(2026, 10, day).atTime(hour, 0).toInstant(utc).toEpochMilli()
    private fun n(id: Long, t: Long, kind: NotificationKind, title: String, body: String, unread: Boolean = false) =
        NotificationEntity(id, "o", t, title, body, kind, unread)

    @Test
    fun `same sender same day collapses, newest first, unread if any`() {
        val s = groupNotifications(
            listOf(
                n(1, at(9, 8), NotificationKind.MESSAGE, "Ravi", "New message", unread = true),
                n(2, at(9, 10), NotificationKind.MESSAGE, "Ravi", "New message"),
                n(3, at(9, 9), NotificationKind.MESSAGE, "Asha", "New message"),
            ), utc,
        )
        assertEquals(1, s.size)
        assertEquals(listOf(2L, 3L), s[0].groups.map { it.latest.id })
        assertEquals(2, s[0].groups[0].count)
        assertTrue(s[0].groups[0].unread)
    }

    @Test
    fun `missed call shows one Missed call row that covers the Call row`() {
        val s = groupNotifications(
            listOf(
                n(1, at(9, 8), NotificationKind.CALL, "Ravi", "Call", unread = true),
                n(2, at(9, 9), NotificationKind.CALL, "Ravi", "Missed call"),
                n(3, at(9, 10), NotificationKind.CALL, "Asha", "Call"),
            ), utc,
        )
        // N44: Ravi's ringing row is hidden behind his missed call; Asha's answered call stays.
        assertEquals(listOf("Call", "Missed call"), s[0].groups.map { it.latest.body })
        val missed = s[0].groups[1]
        assertEquals(listOf(2L, 1L), missed.ids)
        assertEquals(1, missed.count)
        assertTrue(missed.unread)
    }

    @Test
    fun `bidi marks in the ringing title do not split the missed call group`() {
        val s = groupNotifications(
            listOf(
                n(1, at(9, 8), NotificationKind.CALL, "‪555-8888‬", "Call"),
                n(2, at(9, 9), NotificationKind.CALL, "555-8888", "Missed call"),
            ), utc,
        )
        assertEquals(1, s[0].groups.size)
        assertEquals(listOf(2L, 1L), s[0].groups[0].ids)
    }

    @Test
    fun `Call on another day is not hidden by a missed call`() {
        val s = groupNotifications(
            listOf(
                n(1, at(8, 8), NotificationKind.CALL, "Ravi", "Call"),
                n(2, at(9, 9), NotificationKind.CALL, "Ravi", "Missed call"),
            ), utc,
        )
        assertEquals(2, s.size)
        assertEquals(1, s[1].groups.size)
    }

    @Test
    fun `other day is its own section, newest day first`() {
        val s = groupNotifications(
            listOf(
                n(1, at(8, 8), NotificationKind.MESSAGE, "Ravi", "New message"),
                n(2, at(9, 8), NotificationKind.MESSAGE, "Ravi", "New message"),
            ), utc,
        )
        assertEquals(listOf(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 8)), s.map { it.day })
    }
}
