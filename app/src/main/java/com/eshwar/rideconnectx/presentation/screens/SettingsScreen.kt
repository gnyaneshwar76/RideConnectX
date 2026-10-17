package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.presentation.theme.sheetEnter
import com.eshwar.rideconnectx.presentation.theme.sheetContainerColor
import com.eshwar.rideconnectx.presentation.theme.GlassSheetWindow
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import com.eshwar.rideconnectx.presentation.theme.cardSurface
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.BuildConfig
import com.eshwar.rideconnectx.domain.model.DistanceUnit
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.components.RiderAvatar
import com.eshwar.rideconnectx.presentation.components.SettingsChoiceRow
import com.eshwar.rideconnectx.presentation.components.SettingsDivider
import com.eshwar.rideconnectx.presentation.components.SettingsGroup
import com.eshwar.rideconnectx.presentation.components.SettingsLinkRow
import com.eshwar.rideconnectx.presentation.components.SettingsToggleRow
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.viewmodel.SettingsViewModel

/**
 * Screen 20 — Settings.
 *
 * The app's control centre. Everything saves the instant it changes; there is
 * no Save button, matching the design. Rows the app cannot honour yet are shown
 * and disabled rather than hidden — the design asks for unsupported settings to
 * be displayed, and a switch that silently does nothing is worse than one that
 * says why.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onAbout: () -> Unit,
    onPermissions: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onPairVehicle: () -> Unit,
    onProfile: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val context = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val serviceReminders by vm.serviceReminders.collectAsStateWithLifecycle()
    val connection by vm.connectionState.collectAsStateWithLifecycle()
    val vehicleName by vm.vehicleName.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val rideRecorder by vm.rideRecorder.collectAsStateWithLifecycle()
    val gpsCountdown by vm.gpsCountdown.collectAsStateWithLifecycle()

    var showUnits by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }

    val connected = connection is ConnectionState.Connected

    ScreenBackdrop(photo = R.drawable.img_permissions_hero, accent = c.blue) {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val headerList = androidx.compose.foundation.lazy.rememberLazyListState()
            BackHeader(title = stringResource(R.string.settings_title), onBack = onBack, lifted = headerList.canScrollBackward)

            LazyColumn(
                state = headerList,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ── Account ──────────────────────────────────────────
                // The rider's first question about this screen was "where is
                // the profile" — so it is the first thing on it now.
                item {
                    AccountCard(
                        name = account.name.ifBlank { stringResource(R.string.common_rider) },
                        email = account.email,
                        isGuest = account.isGuest,
                        onClick = onProfile,
                    )
                }

                // ── General ──────────────────────────────────────────
                item {
                    SettingsGroup(stringResource(R.string.settings_general), Icons.Filled.Tune) {
                        SettingsLinkRow(
                            title = stringResource(R.string.settings_appearance),
                            subtitle = stringResource(R.string.settings_appearance_sub),
                            onClick = onAppearance,
                        )
                        SettingsDivider()
                        SettingsLinkRow(
                            title = stringResource(R.string.settings_units),
                            subtitle = stringResource(R.string.settings_units_sub),
                            value = s.distanceUnit.short,
                            onClick = { showUnits = true },
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_autonav),
                            subtitle = stringResource(R.string.settings_autonav_sub),
                            checked = s.autoStartNavigation,
                            onCheckedChange = vm::setAutoStartNavigation,
                        )
                    }
                }

                // ── Bluetooth ────────────────────────────────────────
                item {
                    SettingsGroup(stringResource(R.string.settings_bluetooth), Icons.Filled.Bluetooth) {
                        ConnectedVehicleRow(
                            vehicleName = vehicleName,
                            connected = connected,
                            onPair = onPairVehicle,
                        )
                        SettingsDivider()
                        // One switch, not two. "Auto connect" and "auto
                        // reconnect" described the same behaviour, and there was
                        // no case where wanting one without the other made
                        // sense. Background connection moved to Permissions,
                        // where it belongs — it is a permission, not a setting.
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_autoconnect),
                            subtitle = "Connect to your last vehicle when the app opens, " +
                                "and reconnect on its own if the link drops",
                            checked = s.autoConnect,
                            onCheckedChange = vm::setAutoConnect,
                        )
                        SettingsDivider()
                        SettingsLinkRow(
                            title = stringResource(R.string.settings_forget),
                            subtitle = stringResource(R.string.settings_forget_sub),
                            accent = c.red,
                            chevron = false,
                            onClick = { confirmForget = true },
                        )
                    }
                }

                // The Navigation section is gone on purpose. Voice guidance,
                // traffic, tolls, highways and map type all belong to Google
                // Maps — setting them here changed nothing at all, which is
                // worse than not offering them.

                // ── Notifications ────────────────────────────────────
                item {
                    SettingsGroup(stringResource(R.string.settings_notifications), Icons.Filled.Notifications) {
                        // "Ride notifications" (summaries when a ride ends) is
                        // hidden until rides are recorded: nothing records one
                        // yet, so the switch could never do anything (AUD-4).
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_service_reminders),
                            subtitle = stringResource(R.string.settings_service_reminders_sub),
                            checked = serviceReminders,
                            onCheckedChange = vm::setServiceReminders,
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_connection_alerts),
                            subtitle = stringResource(R.string.settings_connection_alerts_sub),
                            checked = s.connectionAlerts,
                            onCheckedChange = vm::setConnectionAlerts,
                        )
                        // Marketing notifications removed — the app does not
                        // send any, so a switch for them was pure noise.
                    }
                }

                // ── Ride testing ─────────────────────────────────────
                // Records every instruction sent to the cluster (what Maps said,
                // the code, the exact bytes, delivered or not, reroutes), so a
                // ride can be checked against what the dashboard showed.
                item {
                    SettingsGroup("Ride testing", Icons.Filled.Tune) {
                        SettingsToggleRow(
                            title = "Ride recorder",
                            subtitle = "Save every instruction sent to your scooter during navigation. Saved only on this phone",
                            checked = rideRecorder,
                            onCheckedChange = vm::setRideRecorder,
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = "Live metre countdown",
                            subtitle = "Phone GPS counts the metres down between Maps updates (takes effect on the next route)",
                            checked = gpsCountdown,
                            onCheckedChange = vm::setGpsCountdown,
                        )
                        SettingsDivider()
                        SettingsLinkRow(
                            title = "Share ride log",
                            subtitle = "Send the recording to yourself or a tester. It contains caller/sender names and destinations",
                            onClick = {
                                val f = vm.rideLogFile()
                                android.widget.Toast.makeText(context, context.getString(R.string.ride_log_share_warning), android.widget.Toast.LENGTH_LONG).show()
                                if (f == null) {
                                    android.widget.Toast.makeText(context, "Nothing recorded yet", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context, context.packageName + ".files", f,
                                    )
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    context.startActivity(android.content.Intent.createChooser(send, "Share ride log"))
                                }
                            },
                        )
                        SettingsDivider()
                        SettingsLinkRow(
                            title = "Clear ride log",
                            subtitle = "Delete the recording from this phone",
                            onClick = {
                                vm.clearRideLog()
                                android.widget.Toast.makeText(context, "Ride log cleared", android.widget.Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }

                // ── Permissions ──────────────────────────────────────
                item {
                    SettingsGroup(stringResource(R.string.settings_permissions), Icons.Filled.Lock) {
                        SettingsLinkRow(
                            title = stringResource(R.string.settings_app_permissions),
                            subtitle = stringResource(R.string.settings_app_permissions_sub),
                            onClick = onPermissions,
                        )
                        SettingsDivider()
                        SettingsLinkRow(
                            title = stringResource(R.string.settings_app_info),
                            subtitle = stringResource(R.string.settings_app_info_sub),
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    )
                                )
                            },
                        )
                    }
                }

                // ── Privacy ──────────────────────────────────────────
                item {
                    SettingsGroup(stringResource(R.string.settings_privacy), Icons.Filled.Shield) {
                        SettingsLinkRow(title = stringResource(R.string.common_privacy_policy), onClick = onPrivacy)
                        SettingsDivider()
                        SettingsLinkRow(title = stringResource(R.string.settings_terms), onClick = onTerms)
                    }
                }

                // ── About ────────────────────────────────────────────
                item {
                    SettingsGroup(stringResource(R.string.settings_about), Icons.Filled.Info) {
                        SettingsLinkRow(
                            title = stringResource(R.string.settings_app_version),
                            value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            chevron = false,
                        )
                        SettingsDivider()
                        SettingsLinkRow(title = "About RideConnectX", onClick = onAbout)
                    }
                }

                // "Reset all settings" removed at the rider's request — with
                // this many options gone there is little left to reset, and it
                // was one destructive action too many on a settings screen.
            }
        }
    }

    if (showUnits) {
        ChoiceSheet(
            title = stringResource(R.string.settings_units),
            onDismiss = { showUnits = false },
        ) {
            DistanceUnit.entries.forEach { unit ->
                SettingsChoiceRow(
                    title = unit.label,
                    subtitle = "Distances shown in ${unit.short}",
                    selected = s.distanceUnit == unit,
                    onSelect = {
                        vm.setDistanceUnit(unit)
                        showUnits = false
                    },
                )
            }
        }
    }


    if (confirmForget) {
        ConfirmSheet(
            title = "Forget this vehicle?",
            body = "RideConnectX will disconnect and stop reconnecting on its own. " +
                "You can pair again at any time.",
            confirmLabel = "Forget",
            onDismiss = { confirmForget = false },
            onConfirm = {
                vm.forgetVehicle()
                confirmForget = false
            },
        )
    }
}

/**
 * Who is signed in, at the top of Settings, opening the Profile screen.
 *
 * Settings with no sign of the account was the reason Profile felt missing —
 * it existed, but nothing on the screen the rider was looking at pointed to it.
 */
@Composable
private fun AccountCard(
    name: String,
    email: String,
    isGuest: Boolean,
    onClick: () -> Unit,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(20.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardSurface(shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RiderAvatar(name = name, size = 52.dp)

        Column(Modifier.weight(1f)) {
            Text(name, style = RcxType.Section.copy(fontSize = 17.sp), color = c.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(
                when {
                    isGuest -> "Guest — saved only on this phone"
                    email.isNotBlank() -> email
                    else -> "Tap to manage your profile"
                },
                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                color = c.muted,
            )
        }

        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
            Modifier.size(16.dp), tint = c.muted.copy(alpha = 0.44f),
        )
    }
}

/** The Bluetooth section's summary row: which vehicle, and is it live. */
@Composable
private fun ConnectedVehicleRow(
    vehicleName: String,
    connected: Boolean,
    onPair: () -> Unit,
) {
    val c = Rcx.colors
    val accent = if (connected) c.green else c.muted
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onPair)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(accent.copy(alpha = 0.094f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Bluetooth, null, Modifier.size(16.dp), tint = accent)
        }
        Column(Modifier.weight(1f)) {
            Text(
                vehicleName.ifBlank { "No vehicle selected" },
                style = RcxType.Label.copy(fontSize = 14.sp),
                color = c.text,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (connected) "Connected" else "Not connected",
                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                color = if (connected) c.green else c.muted,
            )
        }
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = Rcx.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetContainerColor(),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.sheetEnter()) {
        GlassSheetWindow()
        Column(Modifier.padding(horizontal = 8.dp).padding(bottom = 32.dp)) {
            Text(
                title,
                style = RcxType.Section.copy(fontSize = 17.sp),
                color = c.text,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
            content()
        }
    }
    }
}

/** Shared confirmation, used wherever the design says "confirm before…". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    /** Optional content between the body and the buttons, e.g. a password field. */
    extra: (@Composable () -> Unit)? = null,
    /** Red only when the action deletes, clears or discards; otherwise the accent button. */
    destructive: Boolean = true,
) {
    val c = Rcx.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetContainerColor(),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.sheetEnter()) {
        GlassSheetWindow()
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(title, style = RcxType.Section.copy(fontSize = 17.sp), color = c.text)
            Spacer(Modifier.height(8.dp))
            Text(body, style = RcxType.BodySmall.copy(fontSize = 13.sp), color = c.muted)
            extra?.let {
                Spacer(Modifier.height(16.dp))
                it()
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Cancel", onDismiss, secondary = true, modifier = Modifier.weight(1f))
                if (!destructive) PrimaryButton(confirmLabel, onConfirm, modifier = Modifier.weight(1f))
                else Box(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.red.copy(alpha = 0.094f))
                        .border(1.5.dp, c.red.copy(alpha = 0.31f), RoundedCornerShape(16.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onConfirm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        confirmLabel,
                        style = RcxType.Button,
                        color = c.red,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
    }
}
