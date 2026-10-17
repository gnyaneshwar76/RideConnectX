package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.theme.innerFill
import com.eshwar.rideconnectx.presentation.theme.cardFill
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.material.icons.filled.Lock
import com.eshwar.rideconnectx.core.util.AppPermission
import com.eshwar.rideconnectx.core.util.rememberBackgroundRunAccess
import com.eshwar.rideconnectx.core.util.AppPermissions
import com.eshwar.rideconnectx.core.util.PermState
import com.eshwar.rideconnectx.core.util.rememberNotificationAccess
import com.eshwar.rideconnectx.core.util.rememberPermissionsController
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType

/**
 * Permission details — reached from Settings → Privacy → Permissions.
 *
 * Every permission the app can ask for, what it is actually used for, and
 * whether it is on right now. The introduction explains the essentials before
 * asking; this is the page the rider comes back to afterwards, to grant
 * something they skipped or to check what the app can see.
 *
 * Modelled on the original app's "Application Permission Requirements" page —
 * icon, name, and a plain sentence about what it enables — with the state and
 * a working control added, which the original does not have.
 */
@Composable
fun PermissionDetailsScreen(onBack: () -> Unit) {
    val c = Rcx.colors
    val perms = rememberPermissionsController()
    val notificationAccess = rememberNotificationAccess()
    val backgroundRun = rememberBackgroundRunAccess()

    // Granting happens in system UI, so the state is re-read on every return.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                perms.refresh()
                notificationAccess.refresh()
                backgroundRun.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    ScreenBackdrop(photo = R.drawable.img_permissions_hero, accent = c.amber) {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val headerList = androidx.compose.foundation.lazy.rememberLazyListState()
            BackHeader(title = "Permissions", onBack = onBack, lifted = headerList.canScrollBackward)

            LazyColumn(
                state = headerList,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "What RideConnectX asks for, and why. Nothing here is " +
                            "sent anywhere — every permission is used on this " +
                            "phone, to put information on your vehicle's cluster.",
                        style = RcxType.BodySmall.copy(fontSize = 13.sp),
                        color = c.muted,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                item { PermissionGroupHeader("Required") }
                items(AppPermissions.essential.filter { it.required }) { p ->
                    PermissionCard(
                        permission = p,
                        icon = p.icon(),
                        state = perms.state(p),
                        onGrant = { perms.request(p) },
                        onOpenSettings = perms::openAppSettings,
                    )
                }

                item { PermissionGroupHeader("Recommended") }
                items(
                    AppPermissions.essential.filterNot { it.required } + AppPermissions.optional
                ) { p ->
                    PermissionCard(
                        permission = p,
                        icon = p.icon(),
                        state = perms.state(p),
                        onGrant = { perms.request(p) },
                        onOpenSettings = perms::openAppSettings,
                    )
                }

                item { PermissionGroupHeader("Special access") }

                item {
                    // Not a runtime permission — it is granted in a dedicated
                    // system page, so there is no dialog to show, only a link.
                    SpecialAccessCard(
                        icon = Icons.Filled.NotificationsActive,
                        label = "Notification access",
                        why = stringResource(R.string.notif_access_why),
                        granted = notificationAccess.granted,
                        actionLabel = "Open notification access",
                        onAction = notificationAccess.openSettings,
                    )
                }

                item {
                    SpecialAccessCard(
                        icon = Icons.Filled.BatterySaver,
                        label = "Run in the background",
                        why = "Android may stop the connection while your phone " +
                            "is in your pocket. Exempting RideConnectX from " +
                            "battery optimisation keeps the link to your vehicle " +
                            "alive for the whole ride.",
                        // Reads the real exemption instead of always claiming
                        // it is off, and asks with the system's own one-tap
                        // dialog rather than dropping the rider on App info to
                        // find the battery page themselves.
                        granted = backgroundRun.granted,
                        actionLabel = "Allow background running",
                        onAction = backgroundRun.request,
                    )
                }


                item {
                    NotAskedCard()
                }
            }
        }
    }
}

@Composable
private fun PermissionGroupHeader(title: String) {
    Text(
        title.uppercase(),
        style = RcxType.MonoTiny,
        color = Rcx.colors.muted,
        modifier = Modifier.padding(top = 10.dp, start = 2.dp, bottom = 2.dp),
    )
}

@Composable
private fun PermissionCard(
    permission: AppPermission,
    icon: ImageVector,
    state: PermState,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(18.dp)
    val granted = state == PermState.Granted
    val blocked = state == PermState.PermanentlyDenied
    val accent = when {
        granted -> c.green
        blocked -> c.amber
        permission.required -> c.blue
        else -> c.muted
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardFill()
            .border(1.dp, if (granted) accent.copy(alpha = 0.24f) else c.border, shape)
            .padding(16.dp),
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
                Icon(icon, null, Modifier.size(18.dp), tint = accent)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(permission.label),
                    style = RcxType.Label.copy(fontSize = 15.sp),
                    color = c.text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        granted -> "Allowed"
                        blocked -> "Blocked — change it in Settings"
                        permission.required -> "Required"
                        else -> "Optional"
                    },
                    style = RcxType.BodySmall.copy(fontSize = 11.sp),
                    color = accent,
                )
            }
            if (granted) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = c.green)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(permission.why),
            style = RcxType.BodySmall.copy(fontSize = 12.sp),
            color = c.muted,
        )

        if (!granted) {
            Spacer(Modifier.height(12.dp))
            PermissionAction(
                label = if (blocked) "Open Settings" else "Allow",
                accent = if (blocked) c.amber else c.blue,
                onClick = if (blocked) onOpenSettings else onGrant,
            )
        }
    }
}

@Composable
private fun SpecialAccessCard(
    icon: ImageVector,
    label: String,
    why: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    showState: Boolean = true,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(18.dp)
    val accent = if (granted) c.green else c.blue

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardFill()
            .border(1.dp, if (granted) accent.copy(alpha = 0.24f) else c.border, shape)
            .padding(16.dp),
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
                Icon(icon, null, Modifier.size(18.dp), tint = accent)
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = RcxType.Label.copy(fontSize = 15.sp), color = c.text)
                if (showState) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (granted) "Allowed" else "Not allowed yet",
                        style = RcxType.BodySmall.copy(fontSize = 11.sp),
                        color = accent,
                    )
                }
            }
            if (granted) {
                Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = c.green)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(why, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.muted)

        if (!granted) {
            Spacer(Modifier.height(12.dp))
            PermissionAction(label = actionLabel, accent = c.blue, onClick = onAction)
        }
    }
}

/**
 * States what the app deliberately does *not* ask for.
 *
 * Worth a card of its own: a rider comparing this app with the official one
 * will notice three permissions missing, and the honest answer is that they are
 * not needed rather than forgotten.
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
            "What RideConnectX never asks for",
            style = RcxType.Label.copy(fontSize = 14.sp),
            color = c.text,
        )
        Spacer(Modifier.height(8.dp))
        listOf(
            "Reading your SMS messages",
            "Reading your call history",
            "Placing phone calls",
        ).forEach { line ->
            Row(
                Modifier.padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.padding(top = 6.dp).size(5.dp)
                        .clip(RoundedCornerShape(3.dp)).background(c.green)
                )
                Text(line, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.muted)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "The cluster's message and missed-call lamps are driven by " +
                "notification access instead, and the Safety screen opens your " +
                "dialer rather than calling for you.",
            style = RcxType.BodySmall.copy(fontSize = 11.sp),
            color = c.muted.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun PermissionAction(label: String, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = 0.09f))
            .border(1.dp, accent.copy(alpha = 0.24f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = RcxType.Label.copy(fontSize = 13.sp), color = accent)
    }
}

private fun AppPermission.icon(): ImageVector = when (id) {
    "bluetooth" -> Icons.Filled.Bluetooth
    "location" -> Icons.Filled.LocationOn
    "notifications" -> Icons.Filled.Notifications
    "phone_signal" -> Icons.Filled.SignalCellularAlt
    else -> Icons.Filled.Check
}
