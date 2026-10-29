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
import androidx.compose.ui.res.stringResource
import com.eshwar.rideconnectx.R
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

    // Back from the Notification access page and still off: on Android 13+ a sideloaded app's
    // switch is greyed out until "Allow restricted settings" is turned on, so say how.
    var showRestrictedGuide by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refresh()
        if (shouldShowRestrictedGuide(android.os.Build.VERSION.SDK_INT, context.installerPackage(), granted)) {
            showRestrictedGuide = true
        }
    }
    if (showRestrictedGuide) {
        AlertDialog(
            onDismissRequest = { showRestrictedGuide = false },
            title = { Text(stringResource(R.string.restricted_title)) },
            text = {
                Text(
                    listOf(
                        R.string.restricted_intro, R.string.restricted_step_1, R.string.restricted_step_2,
                        R.string.restricted_step_3, R.string.restricted_step_4,
                    ).map { stringResource(it) }.joinToString("\n"),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestrictedGuide = false
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.fromParts("package", context.packageName, null),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text(stringResource(R.string.restricted_open_app_info)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestrictedGuide = false }) { Text(stringResource(R.string.restricted_close)) }
            },
        )
    }

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
 * The "Switch greyed out?" guide: access still off after the Settings page, Android 13+ (restricted
 * settings), and not installed by the Play Store (which is never restricted).
 */
internal fun shouldShowRestrictedGuide(sdkInt: Int, installer: String?, granted: Boolean): Boolean =
    !granted && sdkInt >= 33 && installer != PLAY_STORE

private const val PLAY_STORE = "com.android.vending"

/** Who installed this app; null when unknown (adb, a file manager that does not say). */
private fun Context.installerPackage(): String? = runCatching {
    if (android.os.Build.VERSION.SDK_INT >= 30) packageManager.getInstallSourceInfo(packageName).installingPackageName
    else @Suppress("DEPRECATION") packageManager.getInstallerPackageName(packageName)
}.getOrNull()

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
