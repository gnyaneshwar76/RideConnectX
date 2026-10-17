package com.eshwar.rideconnectx.core.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eshwar.rideconnectx.data.nav.MapsNotificationListener

/** Plain words for what notification access lets the app read. */
object NotificationDisclosure {
    const val TITLE = "Notification access"
    const val BODY = "Android is about to ask you to let RideConnectX read notifications. " +
        "Here is what the app does with them.\n\n" +
        "Google Maps: reads the distance, instruction, street and arrival time to draw the turn on your cluster.\n\n" +
        "Calls and messages: the caller or sender name is shown on your cluster and kept in the Notifications page on this phone. The message text is never shown or kept. " +
        "Notifications of the call or message kind from any app can reach the cluster, not only WhatsApp and Google Messages.\n\n" +
        "Other notifications are looked at only to skip them. " +
        "Nothing you read here is uploaded; it stays on your phone and goes to your scooter by Bluetooth."
}

/**
 * Notification access — the permission that lets RideConnectX read Google Maps'
 * turn-by-turn notification.
 *
 * There is no runtime dialog for this one: Android only grants it from a
 * dedicated Settings page, which is why this is a screen-opener rather than a
 * request.
 */

class NotificationAccess(
    val granted: Boolean,
    val openSettings: () -> Unit,
    val refresh: () -> Unit,
)

@Composable
fun rememberNotificationAccess(): NotificationAccess {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasNotificationAccess()) }

    val refresh = { granted = context.hasNotificationAccess() }

    // Re-read on every return to the screen: the rider can revoke access in
    // Settings while the app sits in the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }

    // Prominent disclosure before the system page (CHK-3): says what is read,
    // what is sent, and that nothing is uploaded.
    var showDisclosure by remember { mutableStateOf(false) }
    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(NotificationDisclosure.TITLE) },
            text = { Text(NotificationDisclosure.BODY) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    runCatching {
                        launcher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("Not now") } },
        )
    }

    return NotificationAccess(
        granted = granted,
        openSettings = { showDisclosure = true },
        refresh = refresh,
    )
}

/**
 * Reads the system's list of enabled listeners rather than asking the service
 * whether it is running — the service is only constructed once access has been
 * granted, so asking it directly would always say no.
 */
fun Context.hasNotificationAccess(): Boolean {
    val component = ComponentName(this, MapsNotificationListener::class.java)
    val enabled = Settings.Secure.getString(
        contentResolver,
        "enabled_notification_listeners",
    ).orEmpty()

    return enabled.split(':').any {
        runCatching { ComponentName.unflattenFromString(it) }.getOrNull() == component
    }
}
