package com.eshwar.rideconnectx.data.repository

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.eshwar.rideconnectx.core.di.ApplicationScope
import com.eshwar.rideconnectx.data.local.LatLon
import com.eshwar.rideconnectx.data.local.OwnerScope
import com.eshwar.rideconnectx.data.local.RideDraft
import com.eshwar.rideconnectx.data.local.RideDraftStore
import com.eshwar.rideconnectx.data.local.RoutePolyline
import com.eshwar.rideconnectx.data.local.db.RideEntity
import com.eshwar.rideconnectx.domain.repository.BleRepository
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What the rides page shows while the scooter is connected. */
data class LiveRide(
    val distanceM: Int,
    val speedKmh: Int,
    val avgKmh: Int,
    val maxKmh: Int,
    val durationMs: Long,
    val route: List<LatLon>,
    /** False when no usable fix (accuracy <= 20 m) arrived in the last 10 s. */
    val gpsOk: Boolean,
    val startedAt: Long,
)

/**
 * Turns GPS fixes into one ride. Pure Kotlin, no Android types.
 *
 * Jitter rules (numbers from the G1 design): a fix is dropped when it has no
 * accuracy, accuracy > 20 m, is older than 10 s, or is not after the last fix.
 * Distance counts only when the move from the last accepted point is at least
 * 3 m and at least accuracy / 2 (standing still adds nothing), and the implied
 * speed is at most 120 km/h. Max speed is the highest median of the last 3
 * speed samples, so one spike cannot set a record.
 */
class GpsRideAccumulator(private val minMetres: Int = 200) {
    private class Pt(val p: LatLon, val t: Long)

    private var anchor: Pt? = null
    private var lastFixAt = 0L
    private var lastGoodAt = 0L
    private var teleports = 0
    private var dist = 0.0
    private val speeds = ArrayDeque<Double>()
    private var curKmh = 0.0
    private val route = ArrayList<LatLon>()
    private var routeView: List<LatLon> = emptyList()
    private var routeDirty = false

    var startedAt = 0L
        private set
    var lastMoveAt = 0L
        private set
    var maxKmh = 0
        private set
    val metres: Int get() = dist.roundToInt()

    /** True when the fix moved the ride. [nowMs] is the phone clock, [timeMs] the fix time. */
    fun onFix(
        lat: Double, lon: Double, accuracyM: Float?, speedMps: Float?, timeMs: Long,
        nowMs: Long = timeMs, speedAccM: Float? = null,
    ): Boolean {
        if (accuracyM == null || accuracyM > MAX_ACCURACY_M) return false
        if (nowMs - timeMs > MAX_AGE_MS || timeMs <= lastFixAt) return false
        lastFixAt = timeMs; lastGoodAt = nowMs
        val here = Pt(LatLon(lat, lon), timeMs)
        val a = anchor
        if (a == null) {
            if (startedAt == 0L) route.clear()   // no ride yet: never keep a stale point
            anchor = here; addRoute(here.p, force = true); return false
        }

        val d = a.p.distanceTo(here.p)
        if (d < max(MIN_MOVE_M, accuracyM / 2.0)) return false
        val doppler = speedMps != null && (speedAccM == null || speedAccM <= 2f)
        // Doppler speed says stopped: the position is drifting, not moving.
        if (doppler && speedMps!! < 0.5f) return false
        val dt = (timeMs - a.t) / 1000.0
        val impliedKmh = d / dt * 3.6
        if (impliedKmh > MAX_PLAUSIBLE_KMH) {
            // A bad anchor must not freeze the ride: after 5 in a row, start over from here.
            if (++teleports >= 5) {
                // The old route ends at the bad anchor: drop it so no line jumps across the gap.
                anchor = here; teleports = 0; route.clear(); addRoute(here.p, force = true)
            }
            return false
        }
        teleports = 0

        dist += d
        if (startedAt == 0L) startedAt = timeMs
        lastMoveAt = timeMs
        curKmh = if (doppler) speedMps!! * 3.6 else impliedKmh
        speeds.addLast(curKmh)
        if (speeds.size > 3) speeds.removeFirst()
        if (speeds.size == 3) maxKmh = max(maxKmh, min(speeds.sorted()[1], MAX_PLAUSIBLE_KMH.toDouble()).roundToInt())
        anchor = here
        addRoute(here.p, force = false)
        return true
    }

    private fun addRoute(p: LatLon, force: Boolean) {
        val last = route.lastOrNull()
        if (force && last != null && last.distanceTo(p) < 1.0) return
        if (force || last == null || last.distanceTo(p) >= ROUTE_STEP_M) {
            route.add(p); routeDirty = true
            // Memory cap: thin the live route when it grows past 3000 points.
            if (route.size > MAX_LIVE_ROUTE) { val t = RoutePolyline.simplifyCapped(route, 8.0); route.clear(); route.addAll(t) }
        }
    }

    /** The link dropped and came back: the next fix is a new anchor, the gap is not counted. */
    fun breakTrack() { anchor = null; speeds.clear(); curKmh = 0.0 }

    fun live(nowMs: Long): LiveRide {
        if (routeDirty) { routeView = route.toList(); routeDirty = false }
        val moving = nowMs - lastMoveAt <= STILL_AFTER_MS && curKmh >= 2.0
        val duration = if (startedAt == 0L) 0L else max(0L, nowMs - startedAt)
        val avg = if (lastMoveAt > startedAt) avgKmh(dist, lastMoveAt - startedAt) else 0
        return LiveRide(
            distanceM = metres, speedKmh = if (moving) curKmh.roundToInt() else 0, avgKmh = avg,
            maxKmh = maxKmh, durationMs = duration, route = routeView,
            gpsOk = nowMs - lastGoodAt <= MAX_AGE_MS && lastGoodAt > 0, startedAt = startedAt,
        )
    }

    /** What to keep on disk so a ride survives the app being killed. */
    /** The draft with the route given already encoded, so encoding can run off the main thread. */
    fun draft(encodedRoute: String) = RideDraft(startedAt, lastMoveAt, metres, maxKmh, encodedRoute)
    fun draft() = draft(RoutePolyline.encode(route))
    fun routeSnapshot(): List<LatLon> = route.toList()

    fun restore(d: RideDraft) {
        startedAt = d.startedAt; lastMoveAt = d.lastMoveAt
        dist = d.metres.toDouble(); maxKmh = d.topSpeedKmh
        route.clear(); route.addAll(RoutePolyline.decode(d.route)); routeDirty = true
        anchor = null
    }

    /** The ride so far if it is at least [minMetres] long, then starts afresh. */
    fun finish(ownerId: String): RideEntity? {
        val ride = if (metres >= minMetres && lastMoveAt > startedAt) {
            val duration = lastMoveAt - startedAt
            val avg = avgKmh(dist, duration)
            // The last accepted point always ends the route.
            val end = anchor?.p
            val pts = if (end != null && route.lastOrNull() != end) route + end else route.toList()
            RideEntity(
                ownerId = ownerId,
                startedAt = startedAt,
                endedAt = lastMoveAt,
                distanceMeters = metres,
                durationMillis = duration,
                topSpeedKmh = max(maxKmh, avg),
                avgSpeedKmh = avg,
                title = titleFor(startedAt),
                route = RoutePolyline.encode(RoutePolyline.simplifyCapped(pts)),
                expiresAt = RideKeepRules.expiresAt(lastMoveAt),
            )
        } else null
        anchor = null; lastFixAt = 0L; lastGoodAt = 0L; teleports = 0
        dist = 0.0; speeds.clear(); curKmh = 0.0
        route.clear(); routeView = emptyList(); routeDirty = false
        startedAt = 0L; lastMoveAt = 0L; maxKmh = 0
        return ride
    }

    private fun avgKmh(metres: Double, ms: Long): Int =
        if (ms <= 0) 0 else (metres / 1000.0 / (ms / 3_600_000.0)).roundToInt()

    private fun titleFor(at: Long): String =
        when (Calendar.getInstance().apply { timeInMillis = at }.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Morning ride"
            in 12..16 -> "Afternoon ride"
            in 17..20 -> "Evening ride"
            else -> "Night ride"
        }

    companion object {
        /** The Access 125 cannot do more; anything above is a timing glitch. */
        const val MAX_PLAUSIBLE_KMH = 120
        const val MAX_ACCURACY_M = 20f
        const val MAX_AGE_MS = 10_000L
        const val MIN_MOVE_M = 3.0
        const val ROUTE_STEP_M = 10.0
        const val STILL_AFTER_MS = 3_000L
        const val MAX_LIVE_ROUTE = 3000
    }
}

/** When to back GPS off while Connected but not moving, and what wakes it. Pure. */
object GpsIdle {
    const val AFTER_MS = 120_000L
    const val SLOW_MS = 10_000L

    /** No real move for 2 minutes. */
    fun shouldSlow(nowMs: Long, lastActiveMs: Long) = nowMs - lastActiveMs >= AFTER_MS

    /** A real move (per the jitter filter), or Doppler speed of 2 m/s (7 km/h) or more. */
    fun wakes(moved: Boolean, speedMps: Float?) = moved || (speedMps != null && speedMps >= 2f)
}

/** A one-shot "the phone moved" trigger (Android: TYPE_SIGNIFICANT_MOTION). */
interface MotionTrigger {
    fun arm(onMotion: () -> Unit)
    fun cancel()
}

/** Slow mode arms the trigger, leaving it cancels. No trigger (no sensor): stays on the 10 s rate. */
class SlowWake(private val trigger: MotionTrigger?) {
    fun enterSlow(onMotion: () -> Unit) { trigger?.arm(onMotion) }
    fun leaveSlow() { trigger?.cancel() }
}

/**
 * Records GPS rides (G2): GPS runs only while the scooter is connected, in the
 * application scope (the BLE foreground service keeps the app alive with the
 * screen off). A dropped link is held 60 s: a reconnect inside that time
 * continues the same ride; after it the ride is saved as PENDING.
 *
 * No location permission means no ride and [locationOff] is true. Coordinates
 * are never logged. It also caches the odometer for the Service screen.
 */
@Singleton
class RideTracker @Inject constructor(
    private val ble: BleRepository,
    private val rides: RideRepository,
    private val service: ServiceRepository,
    private val drafts: RideDraftStore,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val acc = GpsRideAccumulator()
    private var started = false
    private var connected = false
    private var updating = false
    private var graceJob: Job? = null
    private var lastDraftAt = 0L
    @Volatile private var slow = false          // idle backoff: 10 s balanced requests
    @Volatile private var lastActive = 0L       // last real move (or connect)
    private var ticker: Job? = null

    private val wake = SlowWake(
        (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
            ?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)?.let { sensor ->
                object : MotionTrigger {
                    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                    private val listener = object : TriggerEventListener() {
                        override fun onTrigger(event: TriggerEvent?) { cb?.invoke() }
                    }
                    private var cb: (() -> Unit)? = null
                    override fun arm(onMotion: () -> Unit) { cb = onMotion; sm.requestTriggerSensor(listener, sensor) }
                    override fun cancel() { cb = null; sm.cancelTriggerSensor(listener, sensor) }
                }
            },
    )

    private fun leaveSlow() { slow = false; wake.leaveSlow() }

    private fun enterSlow() {
        slow = true; startUpdates()
        wake.enterSlow {
            appScope.launch {
                if (slow) { lastActive = System.currentTimeMillis(); leaveSlow(); startUpdates() }
            }
        }
    }

    private val _live = MutableStateFlow<LiveRide?>(null)
    /** Null when not connected. Updates on every fix and once a second. */
    val live: StateFlow<LiveRide?> = _live.asStateFlow()

    private val _locationOff = MutableStateFlow(false)
    /** True while connected but fine location is not granted (or the request was refused). */
    val locationOff: StateFlow<Boolean> = _locationOff.asStateFlow()

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val now = System.currentTimeMillis()
            val nowReal = android.os.SystemClock.elapsedRealtimeNanos()
            var woke = false
            var draftParts: Pair<RideDraft, List<LatLon>>? = null
            synchronized(acc) {
                for (l in result.locations) {
                    // Age from the monotonic clock: a wrong phone clock must not drop every fix.
                    val ageMs = ((nowReal - l.elapsedRealtimeNanos) / 1_000_000L).coerceAtLeast(0L)
                    val moved = acc.onFix(
                        l.latitude, l.longitude,
                        if (l.hasAccuracy()) l.accuracy else null,
                        if (l.hasSpeed()) l.speed else null,
                        now - ageMs, now,
                        if (l.hasSpeedAccuracy()) l.speedAccuracyMetersPerSecond else null,
                    )
                    if (GpsIdle.wakes(moved, if (l.hasSpeed()) l.speed else null)) woke = true
                }
                if (acc.metres > 0 && now - lastDraftAt >= DRAFT_EVERY_MS) {
                    lastDraftAt = now
                    draftParts = acc.draft("") to acc.routeSnapshot()
                }
                publish(now)
            }
            // Encoding a long route is not main-thread work.
            draftParts?.let { (d, pts) ->
                appScope.launch(Dispatchers.Default) { drafts.save(d.copy(route = RoutePolyline.encode(pts))) }
            }
            if (woke) {
                lastActive = now
                if (slow) { leaveSlow(); startUpdates() }
            }
        }
    }

    /** Only builds the live state (and its route copy) while something is reading it. */
    private fun publish(now: Long) {
        if (_live.subscriptionCount.value > 0) _live.value = acc.live(now)
    }

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (updating) appScope.launch { startUpdates() }
        }
    }

    fun start() {
        if (started) return
        started = true
        ContextCompat.registerReceiver(
            context, powerSaveReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        appScope.launch {
            // A ride cut short by the app being killed last time.
            drafts.load()?.let { d ->
                val old = GpsRideAccumulator().apply { restore(d) }
                val ride = old.finish(OwnerScope.DRAFT)
                // The draft stays until the ride is stored, so a failed write is retried next start.
                if (ride == null) drafts.clear()
                else runCatching { rides.recordRide(ride) }.onSuccess { drafts.clear() }
            }
            ble.telemetry.collect { t ->
                if (t.isValid) runCatching { service.recordOdometer(t.odometerKm) }
            }
        }
        appScope.launch {
            var wasConnected = false
            ble.connectionState.collect { s ->
                val isConnected = s is ConnectionState.Connected
                if (!wasConnected && isConnected) {
                    graceJob?.cancel(); graceJob = null
                    // Back inside the grace: same ride, new anchor.
                    synchronized(acc) { acc.breakTrack(); connected = true }
                    leaveSlow(); lastActive = System.currentTimeMillis()
                    startUpdates()
                    startTicker()
                } else if (wasConnected && !isConnected) {
                    synchronized(acc) { connected = false }
                    ticker?.cancel(); ticker = null
                    leaveSlow(); stopUpdates()
                    _live.value = null; _locationOff.value = false
                    graceJob = appScope.launch { delay(GRACE_MS); save() }
                }
                wasConnected = isConnected
            }
        }
    }

    /** Runs only while Connected: the duration clock, a permission granted late, and the idle backoff. */
    private fun startTicker() {
        ticker?.cancel()
        ticker = appScope.launch {
            while (true) {
                delay(1000)
                val now = System.currentTimeMillis()
                if (!updating) startUpdates()
                else if (!slow && GpsIdle.shouldSlow(now, lastActive)) enterSlow()
                synchronized(acc) { publish(now) }
            }
        }
    }

    private fun hasLocation() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    /** Battery Saver: 3 s / 5 m, still high accuracy (balanced fixes would all fail the 20 m filter). */
    private fun request(): LocationRequest {
        // Idle (scooter probably off, link still up): slow and cheap until a real move.
        if (slow) return LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, GpsIdle.SLOW_MS)
            .setMinUpdateIntervalMillis(GpsIdle.SLOW_MS).build()
        val saver = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, if (saver) 3000L else 1000L)
            .setMinUpdateIntervalMillis(if (saver) 3000L else 1000L)
            .apply { if (saver) setMinUpdateDistanceMeters(5f) }
            .build()
    }

    private fun startUpdates() {
        if (updating) stopUpdates()
        if (!hasLocation()) { _locationOff.value = true; return }
        runCatching { client.requestLocationUpdates(request(), callback, Looper.getMainLooper()) }
            .onSuccess { updating = true; _locationOff.value = false }
            .onFailure { _locationOff.value = true }   // SecurityException
    }

    private fun stopUpdates() {
        if (updating) runCatching { client.removeLocationUpdates(callback) }
        updating = false
    }

    private suspend fun save() {
        val ride = synchronized(acc) { acc.finish(OwnerScope.DRAFT) }
        if (ride == null) { drafts.clear(); return }
        // recordRide stamps the real owner (account or guest). The draft stays
        // until the ride is stored, so a failed write is retried on next start.
        runCatching { rides.recordRide(ride) }.onSuccess { drafts.clear() }
    }

    private companion object {
        const val GRACE_MS = 60_000L
        const val DRAFT_EVERY_MS = 15_000L
    }
}
