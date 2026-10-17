package com.eshwar.rideconnectx.core.ble

import com.eshwar.rideconnectx.core.ble.CommandQueueCore.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The write queue must not lose a command (CHK-17). */
class CommandQueueCoreTest {
    private var clock = 0L
    private val ran = mutableListOf<String>()
    private val results = mutableMapOf<String, Outcome>()
    private var refuse = 0 // how many next executes the "stack" refuses (busy)

    private fun queue() = CommandQueueCore<String>(
        execute = { c -> if (refuse > 0) { refuse--; false } else { ran += c; true } },
        replaceKey = { if (it.startsWith("nav")) "nav" else null },
        retryable = { it != "mtu" },
        onResult = { c, o -> results[c] = o },
        now = { clock },
    )

    @Test
    fun `runs one at a time and the next only after completion`() {
        val q = queue()
        q.add("a"); q.add("b")
        assertEquals(listOf("a"), ran)
        q.completed()
        assertEquals(listOf("a", "b"), ran)
        assertEquals(Outcome.ACCEPTED, results["b"])
    }

    @Test
    fun `a refused write is retried on the next completion, not dropped`() {
        val q = queue()
        q.add("a")
        refuse = 1
        q.add("0x06")           // queued behind a, a still in flight
        q.completed()           // a done -> 0x06 refused (busy) -> kept
        assertEquals(listOf("a"), ran)
        assertEquals(null, results["0x06"])
        q.completed()           // late completion -> retried and accepted
        assertEquals(listOf("a", "0x06"), ran)
        assertEquals(Outcome.ACCEPTED, results["0x06"])
    }

    @Test
    fun `gives up after three refusals and says so`() {
        val q = queue()
        refuse = 99
        q.add("w"); q.add("w"); q.add("w")
        assertEquals(Outcome.FAILED, results["w"])
        assertTrue(ran.isEmpty())
    }

    @Test
    fun `a command that is not retryable fails at once and the queue moves on`() {
        val q = queue()
        refuse = 1
        q.add("mtu")
        assertEquals(Outcome.FAILED, results["mtu"])
        q.add("next")
        assertEquals(listOf("next"), ran)
    }

    @Test
    fun `a newer navigation frame replaces the queued older one`() {
        val q = queue()
        q.add("a")
        q.add("nav1"); q.add("nav2")
        assertEquals(Outcome.SUPERSEDED, results["nav1"])
        q.completed()
        assertEquals(listOf("a", "nav2"), ran)
    }

    @Test
    fun `a completion that never arrives does not freeze the queue`() {
        val q = queue()
        q.add("a")
        q.add("b")
        clock += 1_500
        q.add("c")
        assertEquals(listOf("a", "b"), ran)
    }

    /**
     * 14 Oct 2026: alert bursts every 1 s (3 writes 200 ms apart) must not hold
     * up navigation. Fake clock, 30 s, every write takes [ci] ms to complete
     * (one BALANCED connection interval, the slow case). Call and message loops,
     * a 1 s heartbeat and the profile repeat all run; a nav frame arrives each
     * second at a different offset, added last in its millisecond (worst place).
     */
    @Test
    fun `nav waits well under 300 ms behind 1 s alert bursts`() {
        val ci = 50L
        var t = 0L
        var inFlight: String? = null
        var startedAt = 0L
        val addedAt = mutableMapOf<String, Long>()
        var maxNavWait = 0L
        val q = CommandQueueCore<String>(
            execute = { c ->
                inFlight = c; startedAt = t
                if (c.startsWith("nav")) maxNavWait = maxOf(maxNavWait, t - addedAt.getValue(c))
                true
            },
            now = { t },
        )
        while (t < 30_000) {
            if (inFlight != null && t - startedAt >= ci) { inFlight = null; q.completed() }
            val s = t / 1000
            val inPeriod = t % 1000
            fun add(c: String) { addedAt[c] = t; q.add(c) }
            if (inPeriod in setOf(0L, 200L, 400L)) add("call$t")
            if (inPeriod in setOf(100L, 300L, 500L)) add("msg$t")
            if (inPeriod == 0L) { add("hb$t"); add("profile$t") }
            if (inPeriod == (s * 37) % 1000) add("nav$t")
            t++
        }
        assertTrue("nav waited $maxNavWait ms", maxNavWait < 300)
        // At most: the rest of the write in flight + one write per other sender (call, hb, profile).
        assertTrue("nav waited $maxNavWait ms", maxNavWait <= 4 * ci)
    }

    @Test
    fun `clear fails whatever is waiting`() {
        val q = queue()
        q.add("a"); q.add("b")
        q.clear()
        assertEquals(Outcome.FAILED, results["b"])
        assertEquals(0, q.pending)
    }
}
