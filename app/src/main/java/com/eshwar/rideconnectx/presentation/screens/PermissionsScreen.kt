package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.presentation.theme.mainButtonFill

import com.eshwar.rideconnectx.presentation.theme.popIn
import com.eshwar.rideconnectx.presentation.theme.cardFill
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.core.util.AppPermission
import com.eshwar.rideconnectx.core.util.AppPermissions
import com.eshwar.rideconnectx.core.util.PermState
import com.eshwar.rideconnectx.core.util.SetupProgress
import com.eshwar.rideconnectx.core.util.SetupStep
import com.eshwar.rideconnectx.core.util.SetupSteps
import com.eshwar.rideconnectx.core.util.rememberPermissionsController
import com.eshwar.rideconnectx.core.util.rememberSystemServices
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.components.RcxPhotoFill
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.viewmodel.AuthViewModel

/**
 * Screen 10 — Permissions.
 *
 * Drives the real Android permission dialogs. Bluetooth and Location are
 * required; Notifications is optional. A permission the user has permanently
 * denied switches to an "Open Settings" action, since Android will no longer
 * show the dialog.
 */
@Composable
fun PermissionsScreen(
    onContinue: () -> Unit,
    vm: AuthViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val perms = rememberPermissionsController()
    val services = rememberSystemServices()

    // Radios are prompted at most once per visit, like the permissions.
    var bluetoothPrompted by rememberSaveable { mutableStateOf(false) }
    var locationPrompted by rememberSaveable { mutableStateOf(false) }

    // One thing at a time, automatically (rider, 26 Sep): every dialog used to
    // fire at once; then each waited for a button tap. Now the next dialog
    // comes up by itself once the one before it is answered, allowed or not.
    // The button stays as the way back to anything refused.
    val step = SetupSteps.next(
        SetupProgress(
            notificationsAnswered = perms.state(AppPermissions.notifications) == PermState.Granted ||
                perms.answered(AppPermissions.notifications),
            bluetoothGranted = perms.state(AppPermissions.bluetooth) == PermState.Granted,
            bluetoothAnswered = perms.answered(AppPermissions.bluetooth),
            bluetoothOn = services.bluetoothOn,
            bluetoothPrompted = bluetoothPrompted,
            locationGranted = perms.state(AppPermissions.location) == PermState.Granted,
            locationAnswered = perms.answered(AppPermissions.location),
            locationOn = services.locationOn,
            locationPrompted = locationPrompted,
        )
    )
    fun ask(step: SetupStep) = when (step) {
        SetupStep.NOTIFICATIONS -> perms.request(AppPermissions.notifications)
        SetupStep.BLUETOOTH -> perms.request(AppPermissions.bluetooth)
        SetupStep.BLUETOOTH_ON -> { bluetoothPrompted = true; services.turnOnBluetooth() }
        SetupStep.LOCATION -> perms.request(AppPermissions.location)
        SetupStep.LOCATION_ON -> { locationPrompted = true; services.openLocationSettings() }
        SetupStep.DONE -> Unit
    }
    // A radio prompt is answered when the app comes back to the front.
    var awaitingRadio by remember { mutableStateOf(false) }
    val permissionOpen = AppPermissions.essential.any { perms.state(it) == PermState.Requesting }
    LaunchedEffect(step, permissionOpen, awaitingRadio) {
        if (permissionOpen || awaitingRadio || step == SetupStep.DONE) return@LaunchedEffect
        if (step == SetupStep.BLUETOOTH_ON || step == SetupStep.LOCATION_ON) awaitingRadio = true
        ask(step)
    }

    // Re-check on resume: the user may have granted permissions, or flipped a
    // radio on, from outside the app.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                perms.refresh()
                services.refresh()
                awaitingRadio = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (c.isDark) listOf(Color(0xFF070D1B), Color(0xFF0C1428))
                    else listOf(Color(0xFFE4EEFF), Color(0xFFEEF2FF))
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            // The title sits on the photograph rather than above it: this screen
            // has three cards and a button to fit, and a hero at its own 16:9
            // would take a third of the height on its own. A fixed band crops
            // the picture instead of squeezing the list.
            RcxPhotoFill(
                res = R.drawable.img_permissions_hero,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.35f to Color.Transparent,
                                1f to Color(0xFF040910).copy(alpha = 0.92f),
                            )
                        )
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                ) {
                    Text(
                        stringResource(R.string.perm_title),
                        style = RcxType.Wordmark.copy(fontSize = 20.sp),
                        color = Color.White,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.perm_subtitle),
                        style = RcxType.BodySmall.copy(fontSize = 12.sp),
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PermissionCard(AppPermissions.bluetooth, Icons.Default.Bluetooth, c.cyan, perms.state(AppPermissions.bluetooth),
                    onRequest = { perms.request(AppPermissions.bluetooth) }, onSettings = perms::openAppSettings)

                // Permission granted still does not mean the radio is on, so
                // this appears underneath it until the rider actually turns it on.
                if (!services.bluetoothOn) {
                    ServiceOffRow(
                        message = stringResource(R.string.perm_bt_off),
                        action = stringResource(R.string.perm_bt_turn_on),
                        icon = Icons.Default.Bluetooth,
                        onClick = services.turnOnBluetooth,
                    )
                }

                PermissionCard(AppPermissions.location, Icons.Default.LocationOn, c.blue, perms.state(AppPermissions.location),
                    onRequest = { perms.request(AppPermissions.location) }, onSettings = perms::openAppSettings)

                if (!services.locationOn) {
                    ServiceOffRow(
                        message = stringResource(R.string.perm_loc_off),
                        action = stringResource(R.string.perm_loc_open),
                        icon = Icons.Default.LocationOn,
                        onClick = services.openLocationSettings,
                    )
                }

                PermissionCard(AppPermissions.notifications, Icons.Default.Notifications, c.amber, perms.state(AppPermissions.notifications),
                    onRequest = { perms.request(AppPermissions.notifications) }, onSettings = perms::openAppSettings)
                Spacer(Modifier.height(4.dp))
            }

            // "Ready" now means permission *and* radio — otherwise the rider
            // continues believing everything is set up and hits a dead end at
            // pairing.
            val ready = perms.allRequiredGranted && services.bluetoothOn && services.locationOn
            PrimaryButton(
                label = when (step) {
                    SetupStep.NOTIFICATIONS -> stringResource(R.string.perm_step_notifications)
                    SetupStep.BLUETOOTH -> stringResource(R.string.perm_step_bluetooth)
                    SetupStep.BLUETOOTH_ON -> stringResource(R.string.perm_step_bluetooth_on)
                    SetupStep.LOCATION -> stringResource(R.string.perm_step_location)
                    SetupStep.LOCATION_ON -> stringResource(R.string.perm_step_location_on)
                    SetupStep.DONE -> if (ready) stringResource(R.string.common_continue)
                    else stringResource(R.string.perm_continue_anyway)
                },
                onClick = {
                    if (step != SetupStep.DONE) ask(step)
                    else { vm.markPermissionsCompleted(); onContinue() }
                },
                secondary = step == SetupStep.DONE && !ready,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = if (step != SetupStep.DONE || ready) Color.White else c.blue,
                    )
                },
            )
        }
    }
}

/**
 * Shown when a permission is granted but the underlying system service is off.
 * Tapping opens the system UI — the app cannot flip these switches itself.
 */
@Composable
private fun ServiceOffRow(
    message: String,
    action: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(16.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.amber.copy(alpha = 0.06f))
            .border(1.dp, c.amber.copy(alpha = 0.22f), shape)
            .padding(14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Warning, null, Modifier.size(15.dp), tint = c.amber)
            Text(
                message,
                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                color = c.muted,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.amber)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, Modifier.size(14.dp), tint = Color(0xFF1A1A2E))
                Text(
                    action,
                    style = RcxType.Label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF1A1A2E),
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    permission: AppPermission,
    icon: ImageVector,
    accent: Color,
    state: PermState,
    onRequest: () -> Unit,
    onSettings: () -> Unit,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(18.dp)
    val borderColor by androidx.compose.animation.animateColorAsState(
        when (state) {
            PermState.Granted -> c.green.copy(alpha = 0.55f)
            PermState.PermanentlyDenied -> c.red.copy(alpha = 0.19f)
            else -> c.border
        },
        androidx.compose.animation.core.tween(450),
        label = "permBorder",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardFill()
            .border(1.5.dp, borderColor, shape)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.08f))
                    .border(1.dp, accent.copy(alpha = 0.19f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = accent)
            }
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(permission.label),
                        style = RcxType.Label.copy(fontSize = 14.sp),
                        color = c.text,
                    )
                    if (permission.required) {
                        Text(
                            "REQUIRED",
                            style = RcxType.Mono.copy(fontSize = 9.sp),
                            color = c.blue,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(c.blue.copy(alpha = 0.08f))
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    stringResource(permission.description),
                    style = RcxType.BodySmall.copy(fontSize = 12.sp),
                    color = c.muted,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state == PermState.PermanentlyDenied) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.red.copy(alpha = 0.047f))
                    .border(1.dp, c.red.copy(alpha = 0.133f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Warning, null, Modifier.size(13.dp), tint = c.red)
                Text(
                    stringResource(R.string.perm_perm_denied),
                    style = RcxType.BodySmall.copy(fontSize = 11.sp),
                    color = c.muted,
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        val enabled = state != PermState.Granted && state != PermState.Requesting
        val onButton = com.eshwar.rideconnectx.presentation.theme.mainButtonText
        val label = when (state) {
            PermState.Idle -> stringResource(R.string.perm_enable)
            PermState.Requesting -> stringResource(R.string.perm_requesting)
            PermState.Granted -> stringResource(R.string.perm_granted)
            PermState.Denied -> stringResource(R.string.perm_denied_retry)
            PermState.PermanentlyDenied -> stringResource(R.string.perm_open_settings)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                // Retry is not destructive, so not red: the warning line above says it was denied.
                .then(
                    if (state == PermState.Granted) Modifier.background(accent.copy(alpha = 0.08f))
                    else Modifier.mainButtonFill(RoundedCornerShape(12.dp))
                )
                .then(
                    if (state == PermState.Granted)
                        Modifier.border(1.dp, accent.copy(alpha = 0.19f), RoundedCornerShape(12.dp))
                    else Modifier
                )
                .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button) {
                    if (state == PermState.PermanentlyDenied) onSettings() else onRequest()
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (state) {
                    PermState.Granted -> Icon(Icons.Default.Check, null, Modifier.size(14.dp).popIn(), tint = accent)
                    PermState.Requesting -> CircularProgressIndicator(
                        modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = onButton,
                    )
                    PermState.PermanentlyDenied -> Icon(Icons.Default.Settings, null, Modifier.size(14.dp), tint = onButton)
                    else -> Unit
                }
                Text(
                    label,
                    style = RcxType.Label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = if (state == PermState.Granted) accent else onButton,
                )
            }
        }
    }
}
