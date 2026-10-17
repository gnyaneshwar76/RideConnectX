package com.eshwar.rideconnectx.data.nav

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Whether the cluster should be showing a message indicator or a missed-call
 * indicator right now.
 *
 * The notification listener sets these; the 0x33 heartbeat reads them. They are
 * deliberately just two booleans — the cluster has two lamps, not a message
 * queue, and the official app treats them the same way (`i0` and `j0` on
 * `HomeScreenActivity`).
 */
object ClusterAlerts {

    /**
     * How long a lamp stays lit after the notification arrives.
     *
     * The lamp used to be cleared the moment a single heartbeat had carried it,
     * which at a 5s heartbeat meant it could be visible for well under a second
     * - effectively invisible to someone riding. The official app holds its flag
     * for three heartbeats (~15s); the rider asked for longer, so 30s.
     *
     * Expiry is by wall clock rather than by counting heartbeats, so a dropped
     * or delayed packet cannot leave a lamp latched on forever.
     */
    private const val HOLD_MS = 30_000L

    /**
     * One alert, with who it is from.
     *
     * The lamp alone is not enough. The rider's point, 18 August: a lamp with no
     * name means they pull the phone out of their pocket mid-road to see who it
     * is, which is the exact hazard the cluster display exists to prevent.
     */
    data class Alert(
        val appIdentifier: Char,
        val title: String,
        val text: String,
        val isCall: Boolean,
        /** Unread messages after this event (0x35 byte 4). */
        val unreadCount: Int = 0,
        /** true = new message ('Y'); false = recount after a removal ('N'), no blink. */
        val newAlert: Boolean = true,
        /** Recount left both message and missed counts at 0: rcx-ble sets heartbeat byte 14 'Y'. */
        val bothZero: Boolean = false,
    )

    enum class CallEnd { ANSWERED, MISSED, REJECTED }

    /** A call is ringing now. [sinceMs] is when this caller started; stays set until [CallEnded]. */
    data class CallRinging(val caller: String, val whatsApp: Boolean, val sinceMs: Long)

    data class CallEnded(val caller: String, val outcome: CallEnd)

    private val ringingCalls = LinkedHashMap<String, CallRinging>()
    private val _callRinging = MutableStateFlow<CallRinging?>(null)
    /** Latest ringing call, or null when none rings. rcx-ble repeats 0x32 while non-null. */
    val callRinging: StateFlow<CallRinging?> = _callRinging.asStateFlow()

    private val _callEnded = MutableSharedFlow<CallEnded>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val callEnded: SharedFlow<CallEnded> = _callEnded.asSharedFlow()

    private val _missedCount = MutableStateFlow(0)
    val missedCount: StateFlow<Int> = _missedCount.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _countsReset = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Fires on [resetCounts]: send 0x34 (0, "") and 0x35 ('N', 0), heartbeat flags. */
    val countsReset: SharedFlow<Unit> = _countsReset.asSharedFlow()

    /** Own timer: a ringing entry that never got its removal is dropped after [ringMaxMs]. */
    private val ringScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    @Volatile internal var ringMaxMs = 90_000L

    @Synchronized
    fun onCallRinging(caller: String, whatsApp: Boolean, nowMs: Long = System.currentTimeMillis()) {
        val key = cleanStoredText(caller)
        if (key in ringingCalls) return
        val r = CallRinging(key, whatsApp, nowMs)
        ringingCalls[key] = r
        _callRinging.value = r
        ringScope.launch {
            kotlinx.coroutines.delay(ringMaxMs)
            synchronized(this@ClusterAlerts) {
                if (ringingCalls[key] === r) {
                    ringingCalls.remove(key)
                    _callRinging.value = ringingCalls.values.lastOrNull()
                }
            }
        }
    }

    /** The call stopped ringing (answered / missed / rejected). Ignored if it was not ringing. */
    @Synchronized
    fun onCallEnded(caller: String, outcome: CallEnd) {
        val key = cleanStoredText(caller)
        ringingCalls.remove(key) ?: return
        _callRinging.value = ringingCalls.values.lastOrNull()
        _callEnded.tryEmit(CallEnded(key, outcome))
    }

    /** Silent removal (the notification changed to a missed-call or other non-ringing state). */
    @Synchronized
    fun clearRinging(caller: String) {
        if (ringingCalls.remove(cleanStoredText(caller)) != null) _callRinging.value = ringingCalls.values.lastOrNull()
    }

    /** Listener gone: drop every ring entry silently. */
    @Synchronized
    fun clearAllRinging() {
        ringingCalls.clear()
        _callRinging.value = null
    }

    /** Clear pressed (Notifications page): counts to 0, lamps off, [countsReset] fires. */
    fun resetCounts() {
        _missedCount.value = 0
        _unreadCount.value = 0
        clearNotification()
        clearMissedCall()
        _countsReset.tryEmit(Unit)
    }

    /**
     * A message notification was removed or the chat was read (official m9120l):
     * the listener recounted the phone's active notifications. [appCount] is the
     * removed app's own count (0x35 byte 4), [total] both apps (the page badge).
     * Sends 0x35 'N', no blink. [Alert.bothZero] tells rcx-ble to set heartbeat byte 14.
     */
    fun onMessagesRecounted(appIdentifier: Char, appCount: Int, total: Int) {
        val t = total.coerceIn(0, 99)
        if (_unreadCount.value == 0 && t == 0) return
        _unreadCount.value = t
        if (t == 0) clearNotification()
        _alerts.tryEmit(
            Alert(appIdentifier, "", "", isCall = false, unreadCount = appCount.coerceIn(0, 99), newAlert = false,
                bothZero = t == 0 && _missedCount.value == 0),
        )
    }

    private val _missedReset = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** The missed-call notification was removed (official m9116f): send 0x34 (0, "") x3, byte 15 'Y' one beat. */
    val missedReset: SharedFlow<Unit> = _missedReset.asSharedFlow()

    /** The phone's missed-call notification is gone: count 0, lamp off, [missedReset] fires. */
    fun onMissedNotificationRemoved() {
        _missedCount.value = 0
        clearMissedCall()
        _missedReset.tryEmit(Unit)
    }

    /**
     * Emitted the instant a notification arrives, so the link can push it
     * immediately instead of waiting for the next 5s heartbeat tick.
     */
    private val _alerts = MutableSharedFlow<Alert>(
        replay = 1,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val alerts: SharedFlow<Alert> = _alerts.asSharedFlow()

    /** A ringing call ended unanswered: one 0x34 packet (count, caller). */
    data class MissedCall(val count: Int, val caller: String, val whatsApp: Boolean = false)

    private val _missedCalls = MutableSharedFlow<MissedCall>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val missedCalls: SharedFlow<MissedCall> = _missedCalls.asSharedFlow()

    /** Emits one [MissedCall]; the BLE link writes it as 0x34 once, no repeats. */
    fun onCallMissed(count: Int, caller: String, whatsApp: Boolean = false) {
        _missedCount.value = count
        _missedCalls.tryEmit(MissedCall(count, caller, whatsApp))
    }

    private val _notificationPending = MutableStateFlow(false)
    val notificationPending: StateFlow<Boolean> = _notificationPending.asStateFlow()

    private val _missedCallPending = MutableStateFlow(false)
    val missedCallPending: StateFlow<Boolean> = _missedCallPending.asStateFlow()

    @Volatile private var notificationUntil = 0L
    @Volatile private var missedCallUntil = 0L

    fun onNotification(appIdentifier: Char = 'N', title: String = "", text: String = "") {
        notificationUntil = System.currentTimeMillis() + HOLD_MS
        _notificationPending.value = true
        _unreadCount.value = (_unreadCount.value + 1).coerceAtMost(99)
        _alerts.tryEmit(Alert(appIdentifier, title, text, isCall = false, unreadCount = _unreadCount.value))
    }

    fun onMissedCall(title: String = "", text: String = "") {
        missedCallUntil = System.currentTimeMillis() + HOLD_MS
        _missedCallPending.value = true
        _alerts.tryEmit(Alert('C', title, text, isCall = true))
    }

    /**
     * Drops any lamp whose hold has elapsed.
     *
     * Called by the heartbeat before it reads the flags, so the cluster keeps
     * being told 'alert' for the whole [HOLD_MS] and then stops.
     */
    fun expireStale() {
        val now = System.currentTimeMillis()
        if (_notificationPending.value && now >= notificationUntil) {
            _notificationPending.value = false
        }
        if (_missedCallPending.value && now >= missedCallUntil) {
            _missedCallPending.value = false
        }
    }

    /** Force a lamp out early - the rider opened the message, say. */
    fun clearNotification() {
        notificationUntil = 0L
        _notificationPending.value = false
    }

    fun clearMissedCall() {
        missedCallUntil = 0L
        _missedCallPending.value = false
    }

    /** A fresh connection starts with a clean slate. */
    fun reset() {
        notificationUntil = 0L
        missedCallUntil = 0L
        _notificationPending.value = false
        _missedCallPending.value = false
        _missedCount.value = 0
        _unreadCount.value = 0
        synchronized(this) { ringingCalls.clear(); _callRinging.value = null }
    }
}

/**
 * Stops Android's re-posts of one notification (a ringing call is re-posted
 * 2-7 times) from reaching the cluster each time (N31). A repeat of the same
 * content under the same key is dropped; changed content goes through. A call
 * stays "seen" until [forget] (the call ended); a message repeat is let through
 * again after [windowMs], so the same words sent twice still show twice.
 */
class AlertDedup(private val windowMs: Long = 30_000L, private val callGraceMs: Long = 8_000L) {
    private val seen = HashMap<String, Pair<String, Long>>()
    private val ended = HashMap<String, Long>()

    /** True when this should be sent to the cluster. */
    @Synchronized
    fun shouldSend(key: String, content: String, nowMs: Long, isCall: Boolean): Boolean {
        val prev = seen[key]
        // A ringing call is cancelled and re-posted in bursts (1-2 s apart in the
        // 9 Oct logs): a repeat within callGraceMs of forget() is still the same call.
        val callRepeat = isCall && (ended[key]?.let { nowMs - it < callGraceMs } ?: true)
        if (prev != null && prev.first == content && (callRepeat || (!isCall && nowMs - prev.second < windowMs))) return false
        ended.remove(key)
        seen[key] = content to nowMs
        if (seen.size > 50) seen.entries.removeIf { nowMs - it.value.second > windowMs && it.key != key }
        return true
    }

    /** The notification was removed. A call re-posted soon after is still suppressed. */
    @Synchronized
    fun forget(key: String, nowMs: Long = System.currentTimeMillis()) { if (key in seen) ended[key] = nowMs }
}

/**
 * Decides whether a call that stopped ringing was missed. Ringing then gone with
 * no answer seen = missed, once. Answered = not. A reject cannot be told from a
 * miss by notification alone, so a ring shorter than [minRingMs] counts as a
 * reject (safer: no false "missed" for a call the rider turned down at once).
 */
class MissedCallTracker(private val minRingMs: Long = 3_000L) {
    private val ringing = HashMap<String, Long>()
    private val answered = HashSet<String>()
    private var count = 0

    @Synchronized fun onRinging(key: String, nowMs: Long) { if (key !in ringing) ringing[key] = nowMs }

    @Synchronized fun onAnswered(key: String) { if (key in ringing) answered.add(key) }

    /** The call is gone for good. Returns the missed count to send, or null. Fires once per call. */
    @Synchronized
    fun onEnded(key: String, nowMs: Long): Int? {
        if (resolve(key, nowMs) != ClusterAlerts.CallEnd.MISSED) return null
        count = (count + 1).coerceAtMost(99)
        return count
    }

    /** Outcome of a call that went away at [nowMs] (the removal time, not after any wait). Null = unknown key. */
    @Synchronized
    fun resolve(key: String, nowMs: Long): ClusterAlerts.CallEnd? {
        val start = ringing.remove(key) ?: return null
        return when {
            answered.remove(key) -> ClusterAlerts.CallEnd.ANSWERED
            nowMs - start < minRingMs -> ClusterAlerts.CallEnd.REJECTED
            else -> ClusterAlerts.CallEnd.MISSED.also { missedAt[key] = nowMs }
        }
    }

    /**
     * The dialer posted a missed-call notification. True = count one missed call now.
     * False for a call answered, or already counted within [DEDUPE_MS] (re-post, or the removal path).
     */
    @Synchronized
    fun onMissedPost(key: String, nowMs: Long): Boolean {
        val answeredCall = answered.remove(key)
        ringing.remove(key)
        if (answeredCall) return false
        val last = missedAt[key]
        if (last != null && nowMs - last < DEDUPE_MS) return false
        missedAt[key] = nowMs
        return true
    }

    private val missedAt = HashMap<String, Long>()

    private companion object { const val DEDUPE_MS = 60_000L }
}
