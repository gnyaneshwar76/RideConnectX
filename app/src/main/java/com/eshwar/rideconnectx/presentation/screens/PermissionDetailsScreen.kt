package com.eshwar.rideconnectx.presentation.screens

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.core.util.AppPermissions
import com.eshwar.rideconnectx.core.util.PermState
import com.eshwar.rideconnectx.core.util.rememberBackgroundRunAccess
import com.eshwar.rideconnectx.core.util.rememberNotificationAccess
import com.eshwar.rideconnectx.core.util.rememberPermissionsController
import com.eshwar.rideconnectx.core.util.rememberSystemServices
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.contentWidth
import com.eshwar.rideconnectx.presentation.components.safeDrawingCentred
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import com.eshwar.rideconnectx.presentation.theme.cardFill
import com.eshwar.rideconnectx.presentation.theme.innerFill
import com.eshwar.rideconnectx.presentation.theme.mainButtonFill
import com.eshwar.rideconnectx.presentation.theme.mainButtonText

/**
 * Permissions, reached from Settings. Three groups (Required, Recommended,
 * Optional); every row reads "Allowed" or shows one Allow button that does the
 * right thing for that permission. State is re-read on every return from system
 * settings (ON_RESUME). Handled: denied, permanently denied (opens the app's
 * settings page), Bluetooth absent or off, location services off, notifications
 * switched off in Android, and the greyed-out Notification access switch that
 * Android 13+ shows for apps installed outside the Play Store.
 */
@Composable
fun PermissionDetailsScreen(onBack: () -> Unit) {
    val c = Rcx.colors
    val context = LocalContext.current
    val perms = rememberPermissionsController()
    val notificationAccess = rememberNotificationAccess()
    val backgroundRun = rememberBackgroundRunAccess()
    val services = rememberSystemServices()

    // Granting happens in system UI, so the state is re-read on every return.
    // The tick re-reads the two checks that are not state-backed (notifications
    // switched off in Android, Bluetooth hardware).
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                perms.refresh()
                backgroundRun.refresh()
                resumes++
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val notificationsOn = remember(resumes) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val hasBluetooth = remember(resumes) { context.hasBluetoothAdapter() }

    // Same wording the rows use for the three states a runtime permission can be in.
    val hintBlocked = stringResource(R.string.perm_hint_blocked)
    val allow = stringResource(R.string.perm_allow)
    val openSettings = stringResource(R.string.perm_open_settings)
    val turnOn = stringResource(R.string.perm_turn_on)

    fun runtimeRow(
        p: com.eshwar.rideconnectx.core.util.AppPermission,
        icon: ImageVector,
        extraOk: Boolean = true,
        extraHint: String? = null,
        extraLabel: String = turnOn,
        extraAction: () -> Unit = {},
    ): PermRow {
        val state = perms.state(p)
        val blocked = state == PermState.PermanentlyDenied
        return when {
            state != PermState.Granted -> PermRow(
                icon = icon, label = "", desc = "", granted = false,
                actionLabel = if (blocked) openSettings else allow,
                onAction = if (blocked) perms::openAppSettings else { { perms.request(p) } },
                hint = if (blocked) hintBlocked else null,
                busy = state == PermState.Requesting,
                permission = p,
            )
            !extraOk -> PermRow(
                icon = icon, label = "", desc = "", granted = false,
                actionLabel = extraLabel, onAction = extraAction, hint = extraHint, permission = p,
            )
            else -> PermRow(icon = icon, label = "", desc = "", granted = true, permission = p)
        }
    }

    val bluetoothRow = if (!hasBluetooth) {
        PermRow(
            icon = Icons.Filled.Bluetooth, label = "", desc = "", granted = false,
            hint = stringResource(R.string.perm_hint_bt_none), unavailable = true,
            permission = AppPermissions.bluetooth,
        )
    } else runtimeRow(
        AppPermissions.bluetooth, Icons.Filled.Bluetooth,
        extraOk = services.bluetoothOn,
        extraHint = stringResource(R.string.perm_hint_bt_off),
        extraAction = services.turnOnBluetooth,
    )
    val locationRow = runtimeRow(
        AppPermissions.location, Icons.Filled.LocationOn,
        extraOk = services.locationOn,
        extraHint = stringResource(R.string.perm_hint_loc_off),
        extraAction = services.openLocationSettings,
    )
    // A granted runtime permission can still be overridden by the app's notification switch.
    val notificationsRow = runtimeRow(
        AppPermissions.notifications, Icons.Filled.Notifications,
        extraOk = notificationsOn,
        extraHint = stringResource(R.string.perm_hint_notif_off),
        extraLabel = openSettings,
        extraAction = perms::openAppSettings,
    )
    val restricted = !notificationAccess.granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val notifAccessRow = PermRow(
        icon = Icons.Filled.NotificationsActive,
        label = stringResource(R.string.perm_notif_access),
        desc = stringResource(R.string.perm_notif_access_desc),
        granted = notificationAccess.granted,
        actionLabel = allow,
        onAction = notificationAccess.openSettings,
        hint = if (restricted) stringResource(R.string.perm_hint_restricted) else null,
        onHint = if (restricted) perms::openAppSettings else null,
    )
    val bgRow = PermRow(
        icon = Icons.Filled.BatterySaver,
        label = stringResource(R.string.perm_bgrun),
        desc = stringResource(R.string.perm_bgrun_desc),
        granted = backgroundRun.granted,
        actionLabel = allow,
        onAction = backgroundRun.request,
    )

    ScreenBackdrop(photo = R.drawable.img_permissions_hero, accent = c.amber) {
        Column(
            Modifier
                .fillMaxHeight()
                .safeDrawingCentred()
                .contentWidth()
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val listState = rememberLazyListState()
            BackHeader(
                title = stringResource(R.string.perm_page_title),
                onBack = onBack,
                lifted = listState.canScrollBackward,
            )

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.perm_page_intro),
                        style = RcxType.BodySmall.copy(fontSize = 13.sp),
                        color = c.text.copy(alpha = 0.88f),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                item {
                    PermGroup(
                        stringResource(R.string.perm_group_required),
                        listOf(notifAccessRow, bluetoothRow, locationRow),
                    )
                }
                item {
                    PermGroup(
                        stringResource(R.string.perm_group_recommended),
                        listOf(notificationsRow, bgRow),
                    )
                }
                item {
                    PermGroup(
                        stringResource(R.string.perm_group_optional),
                        listOf(
                            runtimeRow(AppPermissions.contacts, Icons.Filled.Contacts),
                            runtimeRow(AppPermissions.photos, Icons.Filled.Image),
                            // Declared in the manifest, so it is listed here too: signal bars on the display.
                            runtimeRow(AppPermissions.phoneSignal, Icons.Filled.SignalCellularAlt),
                        ),
                    )
                }
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button, onClick = perms::openAppSettings),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            stringResource(R.string.perm_app_info),
                            style = RcxType.Label.copy(fontSize = 14.sp),
                            color = c.blue,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
                item { NotAskedCard() }
            }
        }
    }
}

/**
 * One row. A blank [label] and [desc] mean "take them from [permission]"; the
 * special-access rows (no AppPermission) pass their own text.
 */
private class PermRow(
    val icon: ImageVector,
    val label: String,
    val desc: String,
    val granted: Boolean,
    val actionLabel: String? = null,
    val onAction: () -> Unit = {},
    val hint: String? = null,
    val onHint: (() -> Unit)? = null,
    val busy: Boolean = false,
    val unavailable: Boolean = false,
    val permission: com.eshwar.rideconnectx.core.util.AppPermission? = null,
)

@Composable
private fun PermGroup(title: String, rows: List<PermRow>) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(18.dp)
    Column {
        Text(
            title.uppercase(),
            style = RcxType.MonoTiny.copy(fontSize = 11.sp),
            color = c.muted,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .cardFill()
                .border(1.dp, c.border, shape),
        ) {
            rows.forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                PermRowView(r)
            }
        }
    }
}

@Composable
private fun PermRowView(r: PermRow) {
    val c = Rcx.colors
    val label = r.permission?.takeIf { r.label.isEmpty() }?.let { stringResource(it.label) } ?: r.label
    val desc = r.permission?.takeIf { r.desc.isEmpty() }?.let { stringResource(it.description) } ?: r.desc
    val accent = if (r.granted) c.green else c.blue

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.11f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(r.icon, null, Modifier.size(18.dp), tint = accent)
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = RcxType.Label.copy(fontSize = 15.sp), color = c.text)
                Text(
                    desc,
                    style = RcxType.BodySmall.copy(fontSize = 12.sp),
                    color = c.text.copy(alpha = 0.72f),
                )
            }
            Box(Modifier.widthIn(min = 96.dp), contentAlignment = Alignment.CenterEnd) {
                when {
                    r.granted -> {
                        val allowedDesc = stringResource(R.string.perm_allowed_for, label)
                        Row(
                            Modifier.semantics(mergeDescendants = true) { contentDescription = allowedDesc },
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = c.green)
                            Text(
                                stringResource(R.string.perm_allowed),
                                style = RcxType.Label.copy(fontSize = 14.sp),
                                color = c.text,
                            )
                        }
                    }
                    r.unavailable -> Text(
                        stringResource(R.string.perm_unavailable),
                        style = RcxType.BodySmall.copy(fontSize = 13.sp),
                        color = c.text.copy(alpha = 0.72f),
                    )
                    r.actionLabel != null -> {
                        val buttonShape = RoundedCornerShape(12.dp)
                        Box(
                            Modifier
                                .widthIn(min = 96.dp)
                                .heightIn(min = 48.dp)
                                .clip(buttonShape)
                                .mainButtonFill(buttonShape)
                                .semantics { contentDescription = "${r.actionLabel}: $label" }
                                .clickable(enabled = !r.busy, role = Role.Button, onClick = r.onAction)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (r.busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = mainButtonText,
                                )
                            } else {
                                Text(
                                    r.actionLabel,
                                    style = RcxType.Label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                                    color = mainButtonText,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (r.hint != null) {
            val hintModifier = if (r.onHint != null) {
                Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = r.onHint)
            } else Modifier.padding(top = 6.dp)
            Box(hintModifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Text(
                    r.hint,
                    style = RcxType.BodySmall.copy(fontSize = 12.sp),
                    color = if (r.onHint != null) c.blue else c.amber,
                )
            }
        }
    }
}

/**
 * States what the app deliberately does not ask for. Every line is checked
 * against the manifest and docs/PRIVACY-POLICY.md: no SMS, call log, call,
 * microphone or camera permission is declared, and emergency contacts stay on
 * the phone.
 */
@Composable
private fun NotAskedCard() {
    val c = Rcx.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(shape)
            .innerFill()
            .border(1.dp, c.border, shape)
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.perm_never_title),
            style = RcxType.Label.copy(fontSize = 14.sp),
            color = c.text,
        )
        Spacer(Modifier.height(8.dp))
        listOf(
            R.string.perm_never_sms,
            R.string.perm_never_calllog,
            R.string.perm_never_calls,
            R.string.perm_never_mic,
            R.string.perm_never_contacts,
        ).forEach { line ->
            Row(
                Modifier.padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.padding(top = 6.dp).size(5.dp)
                        .clip(RoundedCornerShape(3.dp)).background(c.green)
                )
                Text(
                    stringResource(line),
                    style = RcxType.BodySmall.copy(fontSize = 12.sp),
                    color = c.text.copy(alpha = 0.72f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.perm_never_note),
            style = RcxType.BodySmall.copy(fontSize = 12.sp),
            color = c.text.copy(alpha = 0.72f),
        )
    }
}

private fun Context.hasBluetoothAdapter(): Boolean =
    (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter != null
