package com.eshwar.rideconnectx.data.nav

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import com.eshwar.rideconnectx.core.di.ServiceEntryPoint
import com.eshwar.rideconnectx.core.util.hasNotificationAccess
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * HyperOS and similar skins kill the notification listener while access stays
 * "on". Tracks whether it is bound and asks the system to rebind it when not.
 * Never toggle the component to force a rebind: on Android 16 disabling it leaves the
 * listener dead with access still "on", until access is reset by hand (tested on the AVD).
 */
object ListenerWatchdog {
    const val MIN_GAP_MS = 60_000L
    private const val WATCH_MS = 30_000L

    private val _connected = MutableStateFlow(false)
    /** True while the system has the listener bound. For a future dead-listener warning (rcx-ui). */
    val connected: StateFlow<Boolean> = _connected

    @Volatile private var lastRebindAt = 0L
    /** When the listener was first seen down (0 = up or unknown), for the N s log lines. */
    @Volatile private var downSince = 0L

    /** Whole seconds down; 0 if not down. */
    fun downSeconds(downSince: Long, now: Long): Long = if (downSince == 0L) 0L else maxOf(0L, (now - downSince) / 1000)

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var watch: Runnable? = null

    /** Pure decision: rebind only if access is granted, listener is down, and the last request is old enough. */
    fun shouldRebind(granted: Boolean, connected: Boolean, lastRebindAt: Long, now: Long): Boolean =
        granted && !connected && (lastRebindAt == 0L || now - lastRebindAt >= MIN_GAP_MS)

    fun onConnected(ctx: Context) {
        _connected.value = true
        val n = downSeconds(downSince, System.currentTimeMillis())
        if (downSince != 0L) log(ctx, "LISTENER reconnected after $n s")
        downSince = 0L
        log(ctx, "LISTENER connected")
    }

    fun onDisconnected(ctx: Context) {
        _connected.value = false
        if (downSince == 0L) downSince = System.currentTimeMillis()
        log(ctx, "LISTENER disconnected")
    }

    /** Safe to call often (app open, service start); rate-limited inside. */
    fun check(ctx: Context, now: Long = System.currentTimeMillis()) {
        val app = ctx.applicationContext
        if (_connected.value || !shouldRebind(app.hasNotificationAccess(), false, lastRebindAt, now)) return
        if (downSince == 0L) downSince = now
        lastRebindAt = now // also paces the log line: once per MIN_GAP_MS
        log(app, "LISTENER not connected for ${downSeconds(downSince, now)} s while link is up")
        log(app, "LISTENER rebind requested")
        runCatching {
            NotificationListenerService.requestRebind(ComponentName(app, MapsNotificationListener::class.java))
        }
    }

    /** Runs while the Bluetooth service runs: the listener can die at any moment of a ride. */
    fun startWatching(ctx: Context) {
        val app = ctx.applicationContext
        watch?.let(handler::removeCallbacks)
        watch = object : Runnable {
            override fun run() {
                check(app)
                handler.postDelayed(this, WATCH_MS)
            }
        }.also(handler::post)
    }

    fun stopWatching() {
        watch?.let(handler::removeCallbacks)
        watch = null
    }

    private fun log(ctx: Context, line: String) {
        runCatching {
            EntryPointAccessors.fromApplication(ctx.applicationContext, ServiceEntryPoint::class.java).rideLog().alert(line)
        }
    }
}
