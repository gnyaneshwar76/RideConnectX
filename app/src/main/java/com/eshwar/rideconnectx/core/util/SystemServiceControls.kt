package com.eshwar.rideconnectx.core.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the Bluetooth radio and location services are actually switched on.
 *
 * This is a different question from "did the rider grant the permission", and
 * conflating the two was confusing: the permission card said Granted, the rider
 * assumed Bluetooth had been turned on, and pairing then reported it was off
 * with no way to act on that. Granting permission never turns a radio on — only
 * the rider can, from the system UI, which is what [turnOnBluetooth] and
 * [openLocationSettings] open.
 */
class SystemServices(
    val bluetoothOn: Boolean,
    val locationOn: Boolean,
    val turnOnBluetooth: () -> Unit,
    val openLocationSettings: () -> Unit,
    /** Straight to Android's Location page — for after the dialog was declined. */
    val openLocationPage: () -> Unit,
    val refresh: () -> Unit,
)

@Composable
fun rememberSystemServices(onLocationDeclined: () -> Unit = {}): SystemServices {
    val context = LocalContext.current

    var bluetoothOn by remember { mutableStateOf(context.isBluetoothOn()) }
    var locationOn by remember { mutableStateOf(context.isLocationOn()) }

    val refresh = {
        bluetoothOn = context.isBluetoothOn()
        locationOn = context.isLocationOn()
    }

    // Bluetooth or location switched in the shade or Settings while the app waits:
    // re-read on every return so the screen never shows a stale "on".
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // The enable dialog reports its own result, so the state is correct the
    // moment it closes rather than waiting for the next resume.
    val enableBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }

    // The resolution is an IntentSender, not an Intent — different contract.
    val openLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        refresh()
        // "No thanks" on the dialog. Callers used to keep saying "turning on
        // location…" with nothing happening (rider, 24 Sep).
        if (result.resultCode != android.app.Activity.RESULT_OK) onLocationDeclined()
    }

    /** Last-resort route to the Location page in Settings. */
    val openLocationPage = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh() }

    return SystemServices(
        bluetoothOn = bluetoothOn,
        locationOn = locationOn,
        turnOnBluetooth = {
            // The system enable dialog is the least disruptive route. Where it
            // is unavailable — some OEM builds refuse it — fall back to the
            // Bluetooth settings page so the rider is never left stuck.
            val ok = runCatching {
                enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }.isSuccess
            if (!ok) {
                runCatching {
                    enableBluetooth.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
            }
        },
        openLocationSettings = {
            // Play Services can put the switch in a dialog on top of the app —
            // the same one Maps shows — so the rider taps once and stays here.
            // Sending them to the Settings app to find the toggle themselves is
            // the fallback, not the first move.
            val request = LocationSettingsRequest.Builder()
                .addLocationRequest(
                    LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        REFRESH_INTERVAL_MS,
                    ).build()
                )
                .setAlwaysShow(true)
                .build()

            LocationServices.getSettingsClient(context)
                .checkLocationSettings(request)
                .addOnSuccessListener { refresh() }
                .addOnFailureListener { error ->
                    val resolution = (error as? ResolvableApiException)?.resolution
                    val launched = resolution != null && runCatching {
                        openLocation.launch(
                            IntentSenderRequest.Builder(resolution).build()
                        )
                    }.isSuccess

                    if (!launched) {
                        runCatching {
                            openLocationPage.launch(
                                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            )
                        }
                    }
                }
        },
        openLocationPage = {
            runCatching { openLocationPage.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        },
        refresh = refresh,
    )
}

/** Only used to describe the accuracy the dialog is asking to enable. */
private const val REFRESH_INTERVAL_MS = 10_000L

fun Context.isBluetoothOn(): Boolean =
    (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
        ?.adapter?.isEnabled == true

/**
 * True when any provider can give a fix. GPS alone is too strict — indoors the
 * network provider is often the only one available, and the rider would be told
 * location was off while it plainly was not.
 */
fun Context.isLocationOn(): Boolean {
    val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}
