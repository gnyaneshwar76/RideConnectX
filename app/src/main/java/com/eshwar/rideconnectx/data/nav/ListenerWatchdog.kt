package com.eshwar.rideconnectx.data.nav

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import com.eshwar.rideconnectx.core.di.ServiceEntryPoint
import com.eshwar.rideconnectx.core.util.hasNotificationAccess
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * HyperOS and similar skins kill the notification listener while access stays
 * "on". Tracks whether it is bound and asks the system to rebind it when not.
 */
object ListenerWatchdog {
    const val MIN_GAP_MS = 60_000L

    private val _connected = MutableStateFlow(false)
    /** True while the system has the listener bound. For a future dead-listener warning (rcx-ui). */
    val connected: StateFlow<Boolean> = _connected

    @Volatile private var lastRebindAt = 0L

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
        if (!shouldRebind(app.hasNotificationAccess(), _connected.value, lastRebindAt, now)) return
        lastRebindAt = now
        log(app, "LISTENER rebind requested")
        runCatching {
            NotificationListenerService.requestRebind(ComponentName(app, MapsNotificationListener::class.java))
        }
    }

    private fun log(ctx: Context, line: String) {
        runCatching {
            EntryPointAccessors.fromApplication(ctx.applicationContext, ServiceEntryPoint::class.java).rideLog().alert(line)
        }
    }
}
