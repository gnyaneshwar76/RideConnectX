package com.eshwar.rideconnectx.data.repository

import com.eshwar.rideconnectx.core.di.ApplicationScope
import com.eshwar.rideconnectx.domain.ProtocolEngine
import com.eshwar.rideconnectx.domain.ClusterFamily
import com.eshwar.rideconnectx.domain.VehicleProfile
import com.eshwar.rideconnectx.domain.model.NavManeuver
import com.eshwar.rideconnectx.domain.model.NavState
import com.eshwar.rideconnectx.domain.repository.BleRepository
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the live navigation session and mirrors it to the vehicle cluster.
 *
 * This is the seam between Google Maps and the bike. A NotificationListener-
 * Service will call [onManeuver] for each instruction Maps posts; everything
 * downstream — the Navigation screen and the BLE packets — reads from here, so
 * that service can be added later without touching the UI.
 */
@Singleton
class NavigationRelay @Inject constructor(
    private val bleRepository: BleRepository,
    private val rideLog: com.eshwar.rideconnectx.data.nav.RideLog,
    private val appSettings: com.eshwar.rideconnectx.data.local.AppSettingsStore,
    @ApplicationScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow<NavState>(NavState.Inactive)
    val state: StateFlow<NavState> = _state.asStateFlow()

    private companion object {
        const val TAG = "RCX-Nav"
        /** Rider's ask: clear 20-30 s after reaching the destination. */
        const val ARRIVAL_CLEAR_MS = 25_000L
        /** Maps silent this long mid-route = frozen. Tunable; see [armWatchdog]. */
        const val STALE_CLEAR_MS = 90_000L
        const val ARRIVAL_METRES = 50
        const val REPEAT_SKIP_MS = 1_000L
        /** How often the GPS countdown refreshes the cluster (twice a second). */
        const val TICK_MS = 500L
        const val TURN_JUMP_M = 150
    }

    /**
     * Everything that changes the cluster, handled one at a time in arrival
     * order: Maps updates, GPS fixes and countdown ticks. One consumer means an
     * older frame can never overtake a newer one, and the countdown state is
     * only ever touched from one place. Sending is quick (the BLE queue keeps
     * only the newest navigation frame), so nothing piles up here.
     */
    private sealed interface Update {
        data class Turn(val maneuver: NavManeuver) : Update
        data class Reroute(val what: String) : Update
        data class Fix(val fix: GpsCountdown.Fix) : Update
        object Tick : Update
        object HoldEnd : Update
        data class Link(val connected: Boolean) : Update
    }
    private val updates = Channel<Update>(Channel.UNLIMITED)

    private fun linked() = bleRepository.connectionState.value is ConnectionState.Connected

    /** Link lost: GPS and high priority off. Link back mid-route: both on again. */
    private suspend fun onLink(connected: Boolean) {
        if (!connected) {
            idleLink()
        } else if (_state.value is NavState.Active) {
            resumeLink()
        }
    }

    private var lowLatencyOn = false

    private suspend fun resumeLink() {
        if (!linked()) return
        if (!lowLatencyOn) {
            lowLatencyOn = true
            bleRepository.setLowLatency(true)
        }
        startGps()
    }

    /** Nothing to show or nowhere to send: GPS and high priority off. */
    private fun idleLink() {
        stopGps()
        if (lowLatencyOn) {
            lowLatencyOn = false
            bleRepository.setLowLatency(false)
        }
    }

    /**
     * The phone's own GPS counting the metres down between Maps updates.
     *
     * The 30 Sep ride log: Maps' notification - the only thing the app can read
     * - changed its distance only every ~2 s near a turn and every ~3.5 s with
     * the screen off, so the cluster always trailed the scooter. Maps still
     * sets every number; this only fills the gaps between its updates.
     */
    // Never up on the same turn unless Maps is 150 m higher: Maps posts only every 5-6 s with the
    // screen off, ~100 m of ride at 60 km/h, so a smaller gap is Maps being stale, not us over-counting.
    private val countdown = GpsCountdown(::metresBetween, neverUp = true, jumpM = TURN_JUMP_M)

    private fun metresBetween(a: GpsCountdown.Fix, b: GpsCountdown.Fix): Double {
        val out = FloatArray(1)
        android.location.Location.distanceBetween(a.lat, a.lon, b.lat, b.lon, out)
        return out[0].toDouble()
    }

    /**
     * The same count for the whole journey's remaining distance, beside the ETA
     * (the rider's ask after the 1 Oct ride: the turn metres were live, this
     * figure still moved only when Maps spoke). Keyed per route, so a reroute
     * starts again from Maps' new figure.
     */
    private val remainingRule = RemainingRule(::metresBetween)
    private var holdJob: Job? = null
    private val turnHold = UnknownTurnHold()
    private var countdownOn = false
    private var locationCallback: LocationCallback? = null
    private var tickJob: Job? = null
    private var fixCount = 0
    private var fixWindowAt = 0L

    @SuppressLint("MissingPermission")
    private suspend fun startGps() {
        if (locationCallback != null || !linked()) return
        countdownOn = appSettings.gpsCountdown.first()
        if (!countdownOn) return
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            countdownOn = false
            rideLog.diag("GPS countdown off - location permission not granted")
            return
        }
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // All of them, stamped with the fix's own time: with the screen off the
                // fused provider can deliver several at once (ride log 4: counts froze 8-45 s).
                fixCount += result.locations.size
                for (l in result.locations) updates.trySend(Update.Fix(GpsCountdown.Fix(
                    lat = l.latitude, lon = l.longitude, accuracyM = l.accuracy,
                    speedMps = if (l.hasSpeed()) l.speed else 0f,
                    atMs = l.elapsedRealtimeMillis,
                )))
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TICK_MS)
            .setMinUpdateIntervalMillis(TICK_MS)
            .build()
        runCatching {
            LocationServices.getFusedLocationProviderClient(context)
                .requestLocationUpdates(request, callback, Looper.getMainLooper())
        }.onFailure {
            Log.w(TAG, "GPS countdown could not start", it)
            countdownOn = false
            return
        }
        locationCallback = callback
        fixCount = 0
        fixWindowAt = 0L
        tickJob = appScope.launch {
            while (true) {
                delay(TICK_MS)
                updates.trySend(Update.Tick)
                // Shows in the ride log whether fixes stop with the screen off. Counts only, no places.
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - fixWindowAt >= 10_000L) {
                    if (_state.value is NavState.Active && fixWindowAt != 0L) rideLog.diag("GPS fixes=$fixCount in ${(now - fixWindowAt) / 1000}s")
                    fixCount = 0
                    fixWindowAt = now
                }
            }
        }
    }

    private fun stopGps() {
        tickJob?.cancel()
        tickJob = null
        locationCallback?.let {
            runCatching { LocationServices.getFusedLocationProviderClient(context).removeLocationUpdates(it) }
        }
        locationCallback = null
        countdownOn = false
    }

    /** Between Maps updates: send the counted-down metres when they change. */
    private suspend fun onTick() {
        if (!countdownOn || rerouting) return
        val active = _state.value as? NavState.Active ?: return
        relay(active.maneuver, fromGps = true)
    }

    fun submit(maneuver: NavManeuver) { updates.trySend(Update.Turn(maneuver)) }

    fun submitReroute(what: String) { updates.trySend(Update.Reroute(what)) }

    /** The last navigation frame written, to skip Maps' back-to-back repeats. */
    private var lastPacket: ByteArray? = null
    private var lastPacketAt = 0L
    /** What [lastPacket] was built from: a GPS tick with the same maneuver and metres skips the build. */
    private var lastFrameManeuver: NavManeuver? = null
    private var lastFrameDistance = -1
    private var lastFrameRemaining = -1
    /** The ETA clock, formatted once per maneuver rather than on every GPS tick. */
    private var clockManeuver: NavManeuver? = null
    private var clockText = ""

    private fun etaClockFor(m: NavManeuver): String {
        if (m !== clockManeuver) { clockManeuver = m; clockText = m.etaClock ?: etaClock(m.etaMinutes) }
        return clockText
    }

    private val _clusterLinked = MutableStateFlow(false)

    /** True while maneuvers are actually reaching the cluster over BLE. */
    val clusterLinked: StateFlow<Boolean> = _clusterLinked.asStateFlow()

    /** Called when the rider hands off to Google Maps. */
    fun awaitMaps() {
        if (_state.value is NavState.Inactive) {
            _state.value = NavState.AwaitingMaps
            rideLog.sessionStart()
        }
    }

    /** Called for every maneuver update read from the Maps notification. */
    suspend fun onManeuver(fresh: NavManeuver) {
        // Settings → "Auto start navigation" off: a route started in Maps alone
        // stays off the cluster until the rider starts it from the app
        // ([awaitMaps]). The switch was saved but never read (AUD-3).
        if (_state.value is NavState.Inactive && !appSettings.settings.first().autoStartNavigation) return
        // Also restarts GPS after a stale-clear or a link drop (CHK-20).
        resumeLink()
        // A post with no distance ("Head toward X") keeps the turn and its count; only ETA and
        // remaining are taken from it. Otherwise the cluster flipped between two states (ride log 4).
        val prev = (_state.value as? NavState.Active)?.maneuver
        // A new route never inherits the last route's remaining distance.
        if (prev == null) { remainingRule.newRoute(); turnHold.reset() }
        val carried = prev != null && keepsTurn(prev, fresh)
        val maneuver = if (carried) carryTurn(prev!!, fresh) else fresh
        _state.value = NavState.Active(maneuver)
        rerouting = false
        val now = SystemClock.elapsedRealtime()
        // Unknown turn distance (N101): never anchor the counter at a made-up 0.
        if (!carried && maneuver.distanceKnown()) countdown.onMaps(
            key = maneuver.instruction to maneuver.maneuverId,
            metres = maneuver.distanceMetres(),
            nowMs = now,
        )
        // This post's own figure only (not one carried from the previous post); unknown = keep counting.
        remainingRule.onMaps(fresh.remainingMetres(), now)
        relay(maneuver)
        armWatchdog(arrived = maneuver.isArrival())
    }

    /**
     * Clears the cluster when Maps stops talking. Two cases seen on the bike,
     * 19 Sep 2026: the route ended but the ETA/km stayed on screen, and Maps
     * froze with its notification still posted, so no "ended" event ever came.
     *
     * Every update re-arms it, so it only fires on silence:
     *  - arrived: [ARRIVAL_CLEAR_MS] after the last update, the rider's ask.
     *  - otherwise: [STALE_CLEAR_MS]. Longer on purpose - Maps posts nothing
     *    while the rider waits at a red light, and a long Indian signal must
     *    not blank a live route. If Maps speaks again, the next update simply
     *    draws the arrow back.
     */
    private var watchdog: Job? = null

    private fun armWatchdog(arrived: Boolean) {
        watchdog?.cancel()
        watchdog = appScope.launch {
            delay(if (arrived) ARRIVAL_CLEAR_MS else STALE_CLEAR_MS)
            Log.d(TAG, "No Maps update for a while (arrived=$arrived) - clearing cluster")
            if (arrived) stop() else {
                clearCluster()
                // Nothing to show: stop GPS until Maps speaks again (CHK-20).
                lastPacket = null
                remainingRule.newRoute()
                idleLink()
            }
        }
    }

    /**
     * Maps is recalculating because the rider left the planned route.
     *
     * The cluster has no reroute icon (none photographed, none in the official
     * app's set), so the rider is shown a "working" frame instead: the arrow
     * blanked (46, the official app's own blank) and "----" where the turn
     * distance goes, ETA and remaining distance kept. The next real Maps
     * instruction overwrites it. Sent once per reroute, not per notification.
     */
    private var rerouting = false

    // Last on purpose: the init block must run after every property below it is initialised.
    init {
        appScope.launch {
            for (u in updates) when (u) {
                is Update.Turn -> onManeuver(u.maneuver)
                is Update.Reroute -> onReroute(u.what)
                is Update.Fix -> {
                    countdown.onFix(u.fix)
                    remainingRule.onFix(u.fix)
                    // Do not wait for the next tick: its timer stops when the CPU sleeps.
                    onTick()
                }
                Update.Tick -> onTick()
                Update.HoldEnd -> (_state.value as? NavState.Active)?.let { relay(it.maneuver) }
                is Update.Link -> onLink(u.connected)
            }
        }
        // GPS only runs while the cluster is connected (CHK-20). The state flow
        // is rcx-ble's; here it is just turned into a queued event.
        appScope.launch {
            bleRepository.connectionState.collect {
                updates.trySend(Update.Link(it is ConnectionState.Connected))
            }
        }
    }

    suspend fun onReroute(what: String) {
        val active = _state.value as? NavState.Active ?: return
        if (rerouting) return
        rerouting = true
        remainingRule.reroute()
        // The counted-down figure, not Maps' older one (CHK-20).
        val remaining = remainingNow()
        val packet = ProtocolEngine.buildNavigationPacket(
            clusterCode = ProtocolEngine.Maneuver.FIRST_BLANK,
            distanceMetres = 0,
            clock = active.maneuver.etaClock ?: etaClock(active.maneuver.etaMinutes),
            remainingMetres = remaining,
            distanceText = "----M",
        )
        val delivered = runCatching { bleRepository.sendPacket(packet).first() }.getOrDefault(false)
        Log.d(TAG, "Reroute frame sent=$delivered ($what)")
        rideLog.reroute(what, delivered, packet)
        armWatchdog(arrived = false)
    }

    private fun keepsTurn(prev: NavManeuver, fresh: NavManeuver) =
        keepsTurnRule(prev.distanceMetres(), fresh.distanceMetres(), fresh.distanceKnown(), fresh.isArrival())

    private fun NavManeuver.distanceKnown() = distanceToTurn.isNotBlank()

    /** Maps says "Arrive at…" / "…destination…", or under ~50 m remain. */
    private fun NavManeuver.isArrival(): Boolean {
        val text = instruction.lowercase()
        if ("arriv" in text || "destination" in text) return true
        return remainingMetres() in 1..ARRIVAL_METRES
    }

    fun stop() {
        watchdog?.cancel()
        if (_state.value !is NavState.Inactive) {
            rideLog.sessionEnd()
            clearCluster()
        }
        _state.value = NavState.Inactive
        _clusterLinked.value = false
        rerouting = false
        lastPacket = null
        remainingRule.newRoute()
        turnHold.reset()
        idleLink()
    }

    /**
     * Blanks the arrow when navigation ends.
     *
     * The cluster holds the last navigation frame until told otherwise, so the
     * final arrow stayed on the dashboard after Maps was closed — the rider
     * caught it on 11 Sep 2026. Code 46 is the official app's own deliberate
     * blank (it sends it while GPS is not locked) and drew nothing in our
     * 11 Aug sweep, so source and hardware agree.
     */
    private fun clearCluster() {
        val packet = ProtocolEngine.buildNavigationPacket(
            clusterCode = ProtocolEngine.Maneuver.FIRST_BLANK,
            distanceMetres = 0,
            clock = clockNow(),
            remainingMetres = ProtocolEngine.REMAINING_UNKNOWN,
            // The field that actually ends navigation. Blanking the arrow and
            // zeroing the distances was not enough: navActive defaults to '1',
            // so the cluster was told "still navigating, with empty values" and
            // kept the rider's last ETA and remaining-km frozen on screen after
            // the route ended. Observed on the bike, 19 Sep 2026.
            navActive = '0',
        )
        appScope.launch {
            val sent = runCatching { bleRepository.sendPacket(packet).first() }.getOrDefault(false)
            Log.d(TAG, "Navigation ended — cluster blanked (code 46) sent=$sent")
        }
        _clusterLinked.value = false
    }

    /**
     * Pushes the maneuver to the cluster. Failures are swallowed on purpose:
     * navigation must keep working on the phone even when the bike is out of
     * range, which is what the spec calls degrading to phone-only.
     */
    private suspend fun relay(maneuver: NavManeuver, fromGps: Boolean = false) {
        val distance = if (countdownOn && maneuver.distanceKnown()) countdown.shown(SystemClock.elapsedRealtime())
            else maneuver.distanceMetres()
        fun holdFrame() {
            if (holdJob?.isActive != true) holdJob = appScope.launch {
                delay(RemainingRule.HOLD_MS + 50)
                updates.trySend(Update.HoldEnd)
            }
        }
        // No turn distance and no earlier turn (N101): wait up to 3 s for a real one, never send 0 m.
        val turnUnknown = !maneuver.distanceKnown()
        if (!turnHold.mayShow(!turnUnknown, SystemClock.elapsedRealtime())) { holdFrame(); return }
        // Nothing real yet at route start: wait up to 3 s for the first figure, then send with FF x5.
        val remaining = remainingRule.frame(SystemClock.elapsedRealtime()) ?: run { holdFrame(); return }
        // Counted metres unchanged since the last frame of this maneuver: same bytes, nothing to send.
        if (fromGps && lastPacket != null && maneuver === lastFrameManeuver &&
            distance == lastFrameDistance && remaining == lastFrameRemaining) return
        // `maneuverId` already holds the cluster code. The official app maps the
        // Mappls maneuver id onto these values in ViewOnClickListenerC4857A0,
        // and the app's own Maneuver constants are that table's output side.
        val packet = ProtocolEngine.buildNavigationPacket(
            clusterCode = laneCodeFor(ProtocolEngine.profile, maneuver.maneuverId),
            // Beside the arrow: how far to the next turn.
            distanceMetres = distance,
            // The cluster prints this field under the label ETA, so it wants the
            // ARRIVAL time, not the current one. It had been fed the phone's
            // clock since the field was first written, which the rider spotted
            // on 21 August: the dashboard read 0858Pm while the clock beside it
            // read 8:59. Maps states the journey time in its subText
            // ("47 min - 22 km - 9:35 pm ETA") and the parser already extracts
            // it, so the arrival time is simply now plus that.
            //
            // The 0x33 heartbeat clock is a different field and is confirmed
            // correct on the dashboard - it keeps the real time.
            clock = etaClockFor(maneuver),
            // Beside the clock: how far is left of the whole journey. This was
            // never sent, so that slot showed the turn distance instead - which
            // is what the rider spotted on 19 August.
            remainingMetres = remaining,
            // Still unknown after the hold: blank field. shortcut: FF x5 cannot be written in this
            // field without a ProtocolEngine change (rcx-protocol); spaces are unverified on the cluster.
            distanceText = if (turnUnknown) "     " else null,
        )

        // Maps often posts the same frame twice in a second; the copy would only
        // queue in front of the next real update. A GPS tick that changes
        // nothing on the cluster (standing still) is never sent.
        val now = System.currentTimeMillis()
        if (lastPacket?.contentEquals(packet) == true && (fromGps || now - lastPacketAt < REPEAT_SKIP_MS)) return
        lastPacket = packet
        lastPacketAt = now
        lastFrameManeuver = maneuver
        lastFrameDistance = distance
        lastFrameRemaining = remaining

        val delivered = runCatching { bleRepository.sendPacket(packet).first() }
            .getOrDefault(false)
        if (fromGps) {
            rideLog.gpsCount(distance, maneuver.distanceMetres(), remaining, delivered)
            _clusterLinked.value = delivered
            return
        }
        val delayMs = if (maneuver.postedAt > 0) System.currentTimeMillis() - maneuver.postedAt else -1L

        Log.d(
            TAG,
            "Relay ${maneuver.instruction} code=${maneuver.maneuverId} " +
                "dist=${maneuver.distanceMetres()}m delivered=$delivered",
        )
        // Also to a file: a real ride happens with no laptop attached, and
        // logcat will not survive it.
        rideLog.maneuver(
            instruction = maneuver.instruction,
            code = maneuver.maneuverId,
            distanceMetres = distance,
            delivered = delivered,
            phraseRecognised = maneuver.phraseRecognised,
            iconName = maneuver.iconName,
            iconCode = maneuver.iconManeuverId,
            codeSource = maneuver.codeSource.name,
            screenOn = maneuver.screenOn,
            packet = packet,
            delayMs = delayMs,
        )
        _clusterLinked.value = delivered
    }

    /** Metres left of the whole journey, from Maps' subText ("38 km", "850 m"). */
    private fun NavManeuver.remainingMetres(): Int =
        com.eshwar.rideconnectx.data.nav.MapsNotificationParser.distanceToMetres(remainingDistance)
            ?: ProtocolEngine.REMAINING_UNKNOWN

    /** Counted-down remaining; unknown until Maps gave a value (the counter starts at 0). */
    private fun remainingNow(): Int = remainingRule.shown(SystemClock.elapsedRealtime())

    private fun NavManeuver.distanceMetres(): Int = metres(distanceToTurn)

    private fun metres(text: String): Int =
        com.eshwar.rideconnectx.data.nav.MapsNotificationParser.distanceToMetres(text) ?: 0

    private fun clockNow(): String =
        SimpleDateFormat("hhmma", Locale.US).format(Date()).uppercase()

    /**
     * The time the rider is expected to arrive, for the cluster's ETA field.
     *
     * Falls back to the current time when Maps has not stated a journey
     * duration - showing the clock is wrong, but showing nothing at all is
     * worse, and a blank field looks like a fault rather than a gap.
     */
    private fun etaClock(etaMinutes: Int?): String {
        if (etaMinutes == null || etaMinutes <= 0) return clockNow()
        val arrival = Date(System.currentTimeMillis() + etaMinutes * 60_000L)
        return SimpleDateFormat("hhmma", Locale.US).format(arrival).uppercase()
    }
}

/**
 * Counts the metres to the next turn down from the phone's own GPS, between
 * the moments Maps updates its notification.
 *
 * Maps' figure is the truth; this only moves the number on while Maps is
 * silent. The shown value never goes up for the same turn (a stale Maps repeat
 * must not push the cluster back), never below 0, and is re-anchored to Maps
 * when Maps is lower (we were slow) or much higher (we over-counted). Standing
 * still, or a poor fix, counts nothing, so GPS jitter at a signal cannot move it.
 */
internal class GpsCountdown(
    private val metresBetween: (Fix, Fix) -> Double,
    /**
     * For the whole-journey figure: on the same key Maps may lower the number
     * but never raise it, so the cluster never jumps up (N29). Only a jump of
     * [NEW_ROUTE_JUMP_M] or more counts as a new route (a reroute Maps did not
     * announce); a new key (announced reroute) always re-anchors.
     */
    private val neverUp: Boolean = false,
    private val jumpM: Int = NEW_ROUTE_JUMP_M,
) {

    data class Fix(val lat: Double, val lon: Double, val accuracyM: Float, val speedMps: Float, val atMs: Long)

    private var key: Any? = null
    private var anchor = 0
    private var anchorAt = 0L
    private var moved = 0.0
    private var last: Fix? = null
    private var shown = 0

    fun onMaps(key: Any, metres: Int, nowMs: Long) {
        val counted = shown(nowMs)
        val tolerance = maxOf(MIN_TOLERANCE_M, metres * 15 / 100)
        val tooHigh = metres - counted > (if (neverUp) jumpM else tolerance)
        if (key != this.key || metres < counted || tooHigh) {
            this.key = key
            anchor = metres
            anchorAt = nowMs
            moved = 0.0
            shown = metres
        }
    }

    val anchored: Boolean get() = key != null

    /** New route: forget the figure (a new route never inherits the old one). */
    fun clear() { key = null; anchor = 0; anchorAt = 0L; moved = 0.0; shown = 0 }

    fun onFix(f: Fix) {
        val prev = last
        last = f
        if (prev == null || !usable(f) || f.atMs <= prev.atMs) return
        // Only the part of this stretch ridden after Maps last set the number.
        val from = maxOf(prev.atMs, anchorAt)
        if (f.atMs <= from) return
        val share = (f.atMs - from).toDouble() / (f.atMs - prev.atMs)
        moved += metresBetween(prev, f) * share
    }

    fun shown(nowMs: Long): Int {
        var ahead = 0.0
        val l = last
        if (l != null && usable(l)) {
            val since = nowMs - maxOf(l.atMs, anchorAt)
            if (since in 0..MAX_AHEAD_MS) ahead = l.speedMps * since / 1000.0
        }
        val counted = (anchor - moved - ahead).roundToInt().coerceAtLeast(0)
        if (counted < shown) shown = counted
        return shown
    }

    private fun usable(f: Fix) = f.accuracyM <= MAX_ACCURACY_M && f.speedMps >= MIN_SPEED_MPS

    private companion object {
        /** Maps rounds to 10 m and trails by a second or two; within this, keep counting. */
        const val MIN_TOLERANCE_M = 30
        const val NEW_ROUTE_JUMP_M = 1_000
        const val MAX_ACCURACY_M = 30f
        /** Under ~4 km/h is standing still: count nothing. */
        const val MIN_SPEED_MPS = 1f
        /** Predict from speed for at most this long after the last fix (GPS lost = stop). With the screen off fixes arrive in bursts 2-8 s apart (ride log 4); shortcut: may over-count a hard stop by a few metres. */
        const val MAX_AHEAD_MS = 4_000L
    }
}

/**
 * The remaining distance of the whole journey, rule: Maps' figure on every post that has one
 * (progressMax - progress, or the km in subText), else the last one counted down by GPS; never up
 * on the same route; a reroute re-anchors even if higher; never carried into a new route; never made up.
 * [frame] says what the navigation frame may carry now: a metre value, [ProtocolEngine.REMAINING_UNKNOWN]
 * (FF x5, only after waiting [HOLD_MS] with nothing real), or null = hold the frame back.
 */
internal class RemainingRule(metresBetween: (GpsCountdown.Fix, GpsCountdown.Fix) -> Double) {
    private val count = GpsCountdown(metresBetween, neverUp = true)
    private var route = 0
    private var waitingSince = -1L

    fun newRoute() { count.clear(); waitingSince = -1L }
    fun reroute() { route++ }
    fun onFix(f: GpsCountdown.Fix) = count.onFix(f)
    /** [metres] < 0 = this post had no figure: keep counting. */
    fun onMaps(metres: Int, nowMs: Long) { if (metres >= 0) count.onMaps(route, metres, nowMs) }
    fun shown(nowMs: Long): Int = if (count.anchored) count.shown(nowMs) else ProtocolEngine.REMAINING_UNKNOWN
    fun frame(nowMs: Long): Int? {
        if (count.anchored) { waitingSince = -1L; return count.shown(nowMs) }
        if (waitingSince < 0) waitingSince = nowMs
        return if (nowMs - waitingSince < HOLD_MS) null else ProtocolEngine.REMAINING_UNKNOWN
    }

    companion object { const val HOLD_MS = 3_000L }
}

/** Keep the old turn when the new post has no (or a 0 m) distance, is not arrival, and the old one had metres. */
internal fun keepsTurnRule(prevMetres: Int, freshMetres: Int, freshKnown: Boolean, freshArrival: Boolean) =
    (!freshKnown || freshMetres == 0) && !freshArrival && prevMetres > 0

/** A frame with an unknown turn distance may wait [HOLD_MS], then goes out with the field blank. */
internal class UnknownTurnHold {
    private var since = -1L
    fun reset() { since = -1L }
    fun mayShow(known: Boolean, nowMs: Long): Boolean {
        if (known) { since = -1L; return true }
        if (since < 0) since = nowMs
        return nowMs - since >= RemainingRule.HOLD_MS
    }
}

/** The previous turn and its distance, with the ETA and remaining distance of the newer post. */
internal fun carryTurn(prev: NavManeuver, fresh: NavManeuver): NavManeuver = prev.copy(
    etaMinutes = fresh.etaMinutes, etaClock = fresh.etaClock ?: prev.etaClock,
    remainingDistance = fresh.remainingDistance.ifBlank { prev.remainingDistance },
    screenOn = fresh.screenOn, postedAt = fresh.postedAt,
)

/**
 * Official lane ids 74/58 differ per model. The Access / A-family keeps the code
 * unchanged (owner has not said yes); other models use their profile's code.
 */
internal fun laneCodeFor(profile: VehicleProfile, code: Int): Int =
    if (profile.family == ClusterFamily.ACCESS_LCD) code else profile.officialLaneCode(code) ?: code
