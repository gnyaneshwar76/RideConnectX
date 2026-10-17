package com.eshwar.rideconnectx.data.repository

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.eshwar.rideconnectx.MainActivity
import com.eshwar.rideconnectx.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import com.eshwar.rideconnectx.data.local.AppSettingsStore
import com.eshwar.rideconnectx.data.local.SafetyPreferencesStore
import com.eshwar.rideconnectx.data.local.db.NotificationKind
import com.eshwar.rideconnectx.domain.model.ConnectionAlert
import com.eshwar.rideconnectx.domain.model.ConnectionAlertRule
import com.eshwar.rideconnectx.domain.repository.BleRepository
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings → "Connection alerts": tell me when the vehicle connects or drops.
 * The switch was saved but nothing read it (AUD-2). Also Safety's helmet
 * reminder, which fires on the same event.
 */
@Singleton
class ConnectionAlerts @Inject constructor(
    private val ble: BleRepository,
    private val settings: AppSettingsStore,
    private val safety: SafetyPreferencesStore,
    private val notifications: NotificationRepository,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        appScope.launch {
            var previous: ConnectionState = ble.connectionState.value
            ble.connectionState.collect { current ->
                val alert = ConnectionAlertRule.alertFor(previous, current)
                previous = current
                // Safety → Helmet reminder: "when a ride starts". The scooter
                // connecting is the ignition coming on. The switch was saved but
                // never read (AUD-8).
                if (alert == ConnectionAlert.CONNECTED && safety.helmetReminder.first()) {
                    notifications.postToPhone(
                        HELMET_CHANNEL, "Helmet reminder", HELMET_ID,
                        "Helmet on?", "Your scooter is on. Strap your helmet before you ride.",
                        NotificationKind.SAFETY,
                    )
                }
                if (alert == null || !settings.settings.first().connectionAlerts) return@collect
                if (alert == ConnectionAlert.CONNECTED) {
                    notifications.notify(NotificationKind.VEHICLE, "Scooter connected", "Your scooter is linked.")
                    postOne("Scooter connected", "Your scooter is linked.")
                } else {
                    notifications.notify(NotificationKind.VEHICLE, "Scooter disconnected", "The link to your scooter dropped.")
                    postOne("Scooter disconnected", "The link to your scooter dropped. RideConnectX will keep trying to reconnect.")
                }
            }
        }
    }

    /** One shade slot, replaced each time (connect replaces lost and back), swipe-away-able. */
    private fun postOne(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Connection alerts", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(NotificationRepository.EXTRA_KIND, NotificationKind.VEHICLE.name)
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.eshwar.rideconnectx.R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
    }

    private companion object {
        const val CHANNEL = "connection_alerts"
        const val NOTIFICATION_ID = 3
        const val HELMET_CHANNEL = "helmet_reminder"
        const val HELMET_ID = 4
    }
}
