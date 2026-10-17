package com.eshwar.rideconnectx.core.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.eshwar.rideconnectx.R

/** Lifecycle of a single permission, mirroring the design's card states. */
enum class PermState { Idle, Requesting, Granted, Denied, PermanentlyDenied }

/**
 * A permission as the user sees it — one card may cover several manifest entries.
 *
 * The three pieces of copy are string *resources*, not strings. They used to be
 * literals baked into this object, which meant the permission cards stayed in
 * English no matter what language the rest of the app was in — the object is
 * built once at class-load time, long before there is a Context to resolve a
 * locale against.
 */
data class AppPermission(
    val id: String,
    @StringRes val label: Int,
    @StringRes val description: Int,
    val manifest: List<String>,
    val required: Boolean,
    /**
     * The full sentence shown on the permission-details screen: what the app
     * does with it, in the rider's terms. The original app explains
     * every permission this way before asking, and it is the reason people say
     * yes rather than guess.
     */
    @StringRes val why: Int = description,
)

/**
 * Every permission RideConnectX asks for, and why.
 *
 * **What is deliberately absent**, because the app does not need it:
 * `READ_SMS` and `READ_CALL_LOG`. The official app takes both. Ours lights the
 * cluster's message and missed-call lamps from **notification access** instead
 * — the same information, one less invasive permission, and it avoids two
 * Play-restricted permissions that would need a special declaration to ship.
 * `CALL_PHONE` is absent for the same reason: the Safety screen opens the
 * dialer with `ACTION_DIAL` rather than placing calls itself.
 */
object AppPermissions {

    val bluetooth = AppPermission(
        id = "bluetooth",
        label = R.string.perm_bluetooth,
        description = R.string.perm_bluetooth_desc,
        manifest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Pre-12 Bluetooth permissions are install-time, so nothing to request.
            emptyList()
        },
        required = true,
        why = R.string.perm_bluetooth_why,
    )

    val location = AppPermission(
        id = "location",
        label = R.string.perm_location,
        description = R.string.perm_location_desc,
        manifest = listOf(Manifest.permission.ACCESS_FINE_LOCATION),
        required = true,
        why = R.string.perm_location_why,
    )

    val notifications = AppPermission(
        id = "notifications",
        label = R.string.perm_notifications,
        description = R.string.perm_notifications_desc,
        manifest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        },
        required = false,
        why = R.string.perm_notifications_why,
    )

    /**
     * Puts the phone's signal bars on the instrument cluster, next to the
     * Bluetooth glyph — the same thing the official app shows there.
     *
     * Optional on purpose: refused, the heartbeat reports zero bars and
     * everything else carries on working.
     */
    val phoneSignal = AppPermission(
        id = "phone_signal",
        label = R.string.perm_signal,
        description = R.string.perm_signal_desc,
        manifest = listOf(Manifest.permission.READ_PHONE_STATE),
        required = false,
        why = R.string.perm_signal_why,
    )

    /**
     * The three asked for during setup, one at a time, in this order — see
     * [SetupSteps].
     *
     * Notifications first because its dialog is the least alarming, then the
     * two the app genuinely cannot work without.
     */
    val essential = listOf(notifications, bluetooth, location)

    /**
     * Emergency contacts, and putting a caller's name on the cluster.
     *
     * Requested at the moment the rider taps "Choose from contacts", so the
     * system dialog is the answer to their own tap rather than a prompt out of
     * nowhere during setup.
     */
    val contacts = AppPermission(
        id = "contacts",
        label = R.string.perm_contacts,
        description = R.string.perm_contacts_desc,
        manifest = listOf(Manifest.permission.READ_CONTACTS),
        required = false,
        why = R.string.perm_contacts_why,
    )

    /**
     * The rider's profile picture.
     *
     * On Android 14+ the pair of media permissions is what produces the
     * three-way dialog — Allow all / Select photos / Don't allow. Asking for
     * `READ_MEDIA_IMAGES` alone gets an all-or-nothing prompt, which is the
     * one thing the rider explicitly did not want.
     */
    val photos = AppPermission(
        id = "photos",
        label = R.string.perm_photos,
        description = R.string.perm_photos_desc,
        manifest = when {
            Build.VERSION.SDK_INT >= 34 -> listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                listOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        },
        required = false,
        why = R.string.perm_photos_why,
    )

    /** Asked for later, in context, or from the permission details screen. */
    val optional = listOf(contacts, photos, phoneSignal)

    val all = essential + optional

}

/**
 * Drives the real Android permission dialogs.
 *
 * Distinguishing "denied once" from "denied permanently" is the fiddly part:
 * Android only reports it indirectly. After a request comes back denied, if the
 * system also says a rationale should no longer be shown, the user has ticked
 * "don't ask again" and the only way forward is App Settings.
 */
class PermissionsController internal constructor(
    private val context: Context,
    private val states: MutableMap<String, PermState>,
    private val requested: MutableSet<String>,
    private val launch: (Array<String>) -> Unit,
) {
    internal var pending: AppPermission? = null

    fun state(p: AppPermission): PermState = states[p.id] ?: PermState.Idle

    val allRequiredGranted: Boolean
        get() = AppPermissions.all.filter { it.required }
            .all { states[it.id] == PermState.Granted }

    /** Re-reads the real system state; call on first show and after returning from Settings. */
    fun refresh() {
        AppPermissions.all.forEach { p ->
            if (p.manifest.isEmpty() || p.manifest.all { granted(it) }) {
                states[p.id] = PermState.Granted
            } else if (states[p.id] == PermState.Granted) {
                states[p.id] = PermState.Idle       // revoked while we were away
            }
        }
    }

    fun request(p: AppPermission) {
        if (p.manifest.isEmpty()) { states[p.id] = PermState.Granted; return }
        if (states[p.id] == PermState.Granted) return
        pending = p
        states[p.id] = PermState.Requesting
        requested += p.id
        launch(p.manifest.toTypedArray())
    }

    /**
     * True once [p] has been asked for in this run, whatever the answer. Setup
     * uses it to move to the next step rather than asking the same thing twice.
     */
    fun answered(p: AppPermission): Boolean = p.id in requested

    /** Opens this app's page in system Settings, for permanently denied permissions. */
    fun openAppSettings() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    internal fun onResult(result: Map<String, Boolean>) {
        val p = pending ?: return
        pending = null
        states[p.id] = when {
            // An interrupted request (rotation, dialog dismissed by the system) returns an
            // empty map; all{} on it is true, which used to read as "granted".
            result.isNotEmpty() && result.values.all { it } -> PermState.Granted
            // Denied and the system will no longer show a rationale => "don't ask again".
            p.id in requested && !shouldShowRationale(p) -> PermState.PermanentlyDenied
            else -> PermState.Denied
        }
    }

    private fun granted(perm: String) =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    private fun shouldShowRationale(p: AppPermission): Boolean {
        val activity = context.findActivity() ?: return false
        return p.manifest.any { activity.shouldShowRequestPermissionRationale(it) }
    }
}

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun rememberPermissionsController(): PermissionsController {
    val context = LocalContext.current
    val states = remember { mutableStateMapOf<String, PermState>() }
    val requested = remember { mutableSetOf<String>() }
    var controller by remember { mutableStateOf<PermissionsController?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> controller?.onResult(result) }

    return remember {
        PermissionsController(context, states, requested) { launcher.launch(it) }
            .also { controller = it; it.refresh() }
    }
}
