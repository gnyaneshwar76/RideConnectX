package com.eshwar.rideconnectx.data.ble

/**
 * What the reconnect watcher should do next. Pure, so the rules that decide
 * whether the app keeps searching (battery) are unit tested.
 */
enum class WatchAction {
    /** Linked: sleep until the link drops. No timer. */
    WAIT_FOR_DROP,

    /** App closed by the rider, or they pressed Disconnect: sleep until woken. No timer. */
    SLEEP_UNTIL_WOKEN,

    /** Auto-connect off or nothing paired: sleep, but look again after a while (a setting may change). */
    SLEEP_LONG,

    /** A connect is already running: leave it alone, look again soon. */
    WAIT_SHORT,

    /** Start a connect now. */
    CONNECT,
}

object ReconnectPolicy {
    const val MIN_MS = 3_000L
    const val MAX_MS = 15_000L
    const val IDLE_RECHECK_MS = 30_000L

    fun decide(
        connected: Boolean,
        dormant: Boolean,
        userDisconnected: Boolean,
        autoConnect: Boolean,
        hasAddress: Boolean,
        connectInProgress: Boolean,
    ): WatchAction = when {
        connected -> WatchAction.WAIT_FOR_DROP
        dormant || userDisconnected -> WatchAction.SLEEP_UNTIL_WOKEN
        !autoConnect || !hasAddress -> WatchAction.SLEEP_LONG
        connectInProgress -> WatchAction.WAIT_SHORT
        else -> WatchAction.CONNECT
    }

    /** Gap before the next try; widens while attempts keep failing. */
    fun nextDelay(current: Long): Long = (current * 2).coerceAtMost(MAX_MS)
}

/**
 * Heartbeat bytes 14/15 are CLEAR flags (official app: 'N' after an alert,
 * 'Y' only when it is cleared). Idle and during an alert they stay 'N'; when
 * an alert ends, [onClear] makes the flag 'Y' for [beats] beats about a second
 * apart, then it is back to 'N'. Flag true here means 'Y' on the wire.
 */
class AlertBurst(private val beats: Int = BEATS) {
    class End(val call: Boolean, val message: Boolean)

    private var remaining = 0
    private var call = false
    private var message = false

    @Synchronized
    fun onClear(isCall: Boolean, isMessage: Boolean, beats: Int = this.beats) {
        remaining = beats
        if (isCall) call = true
        if (isMessage) message = true
    }

    val active: Boolean @Synchronized get() = remaining > 0
    val callFlag: Boolean @Synchronized get() = remaining > 0 && call
    /** Official i0: byte 14 stays 'Y' after both counts hit 0, until the next new message. */
    private var messageLatch = false

    @Synchronized
    fun setMessageLatch(on: Boolean) { messageLatch = on }

    val messageFlag: Boolean @Synchronized get() = messageLatch || (remaining > 0 && message)

    /** Call after each beat was written. Returns what to clear when the burst just finished. */
    @Synchronized
    fun afterBeat(): End? {
        if (remaining == 0) return null
        remaining--
        if (remaining > 0) return null
        val end = End(call, message)
        call = false
        message = false
        return end
    }

    @Synchronized
    fun reset() {
        remaining = 0
        call = false
        message = false
        messageLatch = false
    }

    /** Gap to the next beat: fast while an alert is on, normal otherwise. */
    fun intervalMs(): Long = if (active) FAST_MS else NORMAL_MS

    companion object {
        const val BEATS = 3
        const val FAST_MS = 1_000L
        const val NORMAL_MS = 5_000L
    }
}

/**
 * Repeats one alert burst (3 writes) on a timer, for the call and message
 * blink. One burst every 1 s (owner, 14 Oct 2026: "1 sec ... 30 sec is
 * enough"): about 400 ms of writes, 600 ms rest. Official app sends one burst
 * only; this repeat is ours.
 */
object AlertLoop {
    const val PERIOD_MS = 1_000L
    /** New message: 3 bursts x 1 s = about 3 s. */
    const val MESSAGE_BURSTS = 3

    /** Safety cap for a ringing call: 30 bursts x 1 s = 30 s, even if the ringing state sticks. */
    const val CALL_MAX_BURSTS = 30

    /** Nominal time inside one burst (2 gaps of 200 ms), so the period is start to start. */
    private const val BURST_MS = 400L

    /**
     * Runs [burst] up to [max] times, [periodMs] apart; ends when [max] is
     * reached or the caller cancels. [onEnd] gets the number of finished bursts.
     */
    suspend fun run(
        max: Int = Int.MAX_VALUE,
        periodMs: Long = PERIOD_MS,
        onEnd: (Int) -> Unit = {},
        burst: suspend () -> Unit,
    ) {
        var n = 0
        try {
            while (n < max) {
                burst()
                n++
                if (n < max) kotlinx.coroutines.delay((periodMs - BURST_MS).coerceAtLeast(0))
            }
        } finally {
            onEnd(n)
        }
    }
}
