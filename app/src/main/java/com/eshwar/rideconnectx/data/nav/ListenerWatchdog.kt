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

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var watch: Runnable? = null

    /** Pure decision: rebind only if access is granted, listener is down, and the last request is old enough. */
    fun shouldRebind(granted: Boolean, connected: Boolean, lastRebindAt: Long, now: Long): Boolean =
        granted && !connected && (lastRebindAt == 0L || now - lastRebindAt >= MIN_GAP_MS)

    fun onConnected(ctx: Context) {
        _connected.value = true
        log(ctx, "LISTENER connected")
    }

    fun onDisconnected(ctx: Context) {
        _connected.value = false
        log(ctx, "LISTENER disconnected")
    }

    /** Safe to call often (app open, service start); rate-limited inside. */
    fun check(ctx: Context, now: Long = System.currentTimeMillis()) {
        val app = ctx.applicationContext
        if (_connected.value || !shouldRebind(app.hasNotificationAccess(), false, lastRebindAt, now)) return
        lastRebindAt = now
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
