package com.eshwar.rideconnectx.data.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.launch

class LinkRulesTest {
    private fun decide(
        connected: Boolean = false, dormant: Boolean = false, user: Boolean = false,
        auto: Boolean = true, address: Boolean = true, busy: Boolean = false,
    ) = ReconnectPolicy.decide(connected, dormant, user, auto, address, busy)

    @Test
    fun `linked means sleep until the link drops`() =
        assertEquals(WatchAction.WAIT_FOR_DROP, decide(connected = true))

    @Test
    fun `app closed or rider disconnected means no timer at all`() {
        assertEquals(WatchAction.SLEEP_UNTIL_WOKEN, decide(dormant = true))
        assertEquals(WatchAction.SLEEP_UNTIL_WOKEN, decide(user = true))
    }

    @Test
    fun `auto connect off or nothing paired sleeps long`() {
        assertEquals(WatchAction.SLEEP_LONG, decide(auto = false))
        assertEquals(WatchAction.SLEEP_LONG, decide(address = false))
    }

    @Test
    fun `a connect in progress is not restarted`() =
        assertEquals(WatchAction.WAIT_SHORT, decide(busy = true))

    @Test
    fun `otherwise connect, with a gap that widens to 15 s`() {
        assertEquals(WatchAction.CONNECT, decide())
        assertEquals(6_000L, ReconnectPolicy.nextDelay(3_000))
        assertEquals(15_000L, ReconnectPolicy.nextDelay(12_000))
        assertEquals(15_000L, ReconnectPolicy.nextDelay(15_000))
    }

    @Test
    fun `the clear flag is sent for a few beats about a second apart, then back to normal`() {
        val b = AlertBurst(beats = 4)
        assertFalse(b.callFlag)
        assertEquals(AlertBurst.NORMAL_MS, b.intervalMs())

        b.onClear(isCall = true, isMessage = false)
        assertTrue(b.callFlag)
        assertFalse(b.messageFlag)
        assertEquals(AlertBurst.FAST_MS, b.intervalMs())

        repeat(3) { assertNull(b.afterBeat()); assertTrue(b.callFlag) }
        val end = b.afterBeat()!!
        assertTrue(end.call)
        assertFalse(end.message)
        assertFalse(b.callFlag)
        assertEquals(AlertBurst.NORMAL_MS, b.intervalMs())
        assertNull(b.afterBeat())
    }

    @Test
    fun `a new clear restarts the count but never stacks`() {
        val b = AlertBurst(beats = 4)
        b.onClear(false, true)
        b.afterBeat(); b.afterBeat()
        b.onClear(false, true)
        repeat(3) { assertNull(b.afterBeat()) }
        assertTrue(b.afterBeat() != null)
    }
}

class AlertLoopTest {
    @Test
    fun `message loop stops after its bursts and reports the count`() = kotlinx.coroutines.runBlocking {
        var bursts = 0
        var ended = -1
        AlertLoop.run(max = 3, periodMs = 450, onEnd = { ended = it }) { bursts++ }
        assertEquals(3, bursts)
        assertEquals(3, ended)
    }

    @Test
    fun `call loop repeats until cancelled and reports the count`() = kotlinx.coroutines.runBlocking {
        var bursts = 0
        var ended = -1
        // Cancel from inside the 5th burst: no wall-clock wait, so no timing flake.
        lateinit var job: kotlinx.coroutines.Job
        job = launch {
            AlertLoop.run(onEnd = { ended = it }) { if (++bursts == 5) job.cancel() }
        }
        job.join()
        assertEquals(5, bursts)
        assertEquals(bursts, ended)
    }

    @Test
    fun `call loop is capped at 30 bursts even if ringing never clears`() = kotlinx.coroutines.runBlocking {
        var bursts = 0
        var ended = -1
        AlertLoop.run(max = AlertLoop.CALL_MAX_BURSTS, periodMs = 0, onEnd = { ended = it }) { bursts++ }
        assertEquals(30, bursts)
        assertEquals(30, ended)
        assertEquals(1_000L, AlertLoop.PERIOD_MS)
        assertEquals(30_000L, AlertLoop.CALL_MAX_BURSTS * AlertLoop.PERIOD_MS)
        assertEquals(3, AlertLoop.MESSAGE_BURSTS)
    }

    @Test
    fun `reset form lights the clear flags for one beat only`() {
        val b = AlertBurst()
        b.onClear(isCall = true, isMessage = true, beats = 1)
        assertTrue(b.callFlag && b.messageFlag)
        b.afterBeat()
        assertFalse(b.callFlag || b.messageFlag)
    }

    @Test
    fun `message flag latches after both counts are zero until the next new message`() {
        val b = AlertBurst()
        b.setMessageLatch(true)
        assertTrue(b.messageFlag)
        repeat(5) { b.afterBeat() }
        assertTrue(b.messageFlag)
        b.setMessageLatch(false)
        assertFalse(b.messageFlag)
        b.setMessageLatch(true)
        b.reset()
        assertFalse(b.messageFlag)
    }
}
