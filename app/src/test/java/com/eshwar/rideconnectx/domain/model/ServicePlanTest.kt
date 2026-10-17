package com.eshwar.rideconnectx.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Screen 18's schedule arithmetic, and the unit conversion Screen 20 drives.
 *
 * Both were found wrong on the emulator on 13 August 2026 — the Units setting
 * was stored and never honoured, and a mistyped service reading could not be
 * undone — so the behaviour is pinned here.
 */
class ServicePlanTest {

    private fun daysAgo(days: Long) =
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days)

    // ── ServiceStatus ─────────────────────────────────────────────

    @Test
    fun `no record means no schedule`() {
        val status = ServiceStatus()
        assertFalse(status.hasBaseline)
        assertNull(status.dueAtKm)
        assertNull(status.remainingKm)
        assertEquals(0f, status.progress, 0.001f)
    }

    @Test
    fun `remaining distance counts from the last service`() {
        val status = ServiceStatus(
            lastServiceOdometerKm = 1_602,
            lastServiceAt = daysAgo(0),
            currentOdometerKm = 1_602,
            intervalKm = 3_000,
            intervalDays = 90,
        )
        assertTrue(status.hasBaseline)
        assertEquals(4_602, status.dueAtKm)
        assertEquals(3_000, status.remainingKm)
        assertFalse(status.isOverdue)
    }

    @Test
    fun `distance overdue reports a negative remainder`() {
        val status = ServiceStatus(
            lastServiceOdometerKm = 1_000,
            lastServiceAt = daysAgo(1),
            currentOdometerKm = 4_500,
            intervalKm = 3_000,
        )
        assertEquals(-500, status.remainingKm)
        assertTrue(status.isOverdue)
    }

    @Test
    fun `a service overdue by time alone is still overdue`() {
        val status = ServiceStatus(
            lastServiceOdometerKm = 1_000,
            lastServiceAt = daysAgo(120),
            // Barely ridden, so distance says there is plenty left.
            currentOdometerKm = 1_100,
            intervalKm = 3_000,
            intervalDays = 90,
        )
        assertTrue(status.remainingKm!! > 0)
        assertTrue(status.remainingDays!! < 0)
        assertTrue(status.isOverdue)
    }

    @Test
    fun `progress follows whichever of distance and time is further along`() {
        // Half the distance used, nearly all the time.
        val status = ServiceStatus(
            lastServiceOdometerKm = 0,
            lastServiceAt = daysAgo(80),
            currentOdometerKm = 1_500,
            intervalKm = 3_000,
            intervalDays = 90,
        )
        assertTrue("time should win", status.progress > 0.85f)
    }

    @Test
    fun `progress never exceeds one`() {
        val status = ServiceStatus(
            lastServiceOdometerKm = 0,
            lastServiceAt = daysAgo(900),
            currentOdometerKm = 90_000,
            intervalKm = 3_000,
            intervalDays = 90,
        )
        assertEquals(1f, status.progress, 0.001f)
    }

    @Test
    fun `an unknown odometer leaves distance unknown rather than zero`() {
        val status = ServiceStatus(
            lastServiceOdometerKm = 1_000,
            lastServiceAt = daysAgo(1),
            currentOdometerKm = 0,
        )
        assertNull(status.remainingKm)
        // Time is still known, so the schedule is not blank.
        assertTrue(status.remainingDays!! > 0)
    }

    // ── DistanceUnit ──────────────────────────────────────────────

    @Test
    fun `kilometres are shown unchanged`() {
        assertEquals(3_000, DistanceUnit.KM.fromKm(3_000))
        assertEquals("3,000 km", DistanceUnit.KM.format(3_000))
    }

    @Test
    fun `miles convert at the defined ratio`() {
        // 1 mile = 1.609344 km exactly.
        assertEquals(1_864, DistanceUnit.MILES.fromKm(3_000))
        assertEquals(995, DistanceUnit.MILES.fromKm(1_602))
        assertEquals("1,864 mi", DistanceUnit.MILES.format(3_000))
    }

    @Test
    fun `zero and negative distances convert without blowing up`() {
        assertEquals(0, DistanceUnit.MILES.fromKm(0))
        assertEquals(-621, DistanceUnit.MILES.fromKm(-1_000))
    }

    // ── UpcomingTask ──────────────────────────────────────────────

    @Test
    fun `a task at or past its interval is due`() {
        fun task(remaining: Int?) =
            UpcomingTask(id = 1, label = "Engine Oil", everyKm = 3_000, remainingKm = remaining)

        assertTrue(task(0).isOverdue)
        assertTrue(task(-10).isOverdue)
        assertFalse(task(1).isOverdue)
        // Unknown is not the same as due.
        assertFalse(task(null).isOverdue)
    }
}
