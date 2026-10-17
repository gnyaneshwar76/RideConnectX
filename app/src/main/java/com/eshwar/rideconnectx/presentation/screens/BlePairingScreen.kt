package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.presentation.theme.mainButtonFill

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.wrapContentHeight
import com.eshwar.rideconnectx.presentation.theme.rememberAmbientTransition
import com.eshwar.rideconnectx.presentation.theme.animateAmbient
import com.eshwar.rideconnectx.presentation.theme.popIn
import androidx.compose.foundation.layout.offset
import com.eshwar.rideconnectx.presentation.theme.isGlass
import com.eshwar.rideconnectx.presentation.theme.glassButton
import com.eshwar.rideconnectx.presentation.theme.cardFill
import com.eshwar.rideconnectx.presentation.theme.cardSurface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import com.eshwar.rideconnectx.presentation.theme.enterRise
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.core.util.rememberSystemServices
import com.eshwar.rideconnectx.domain.model.BleDevice
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import com.eshwar.rideconnectx.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.components.RcxPhotoFill
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.viewmodel.ScanUiIntent
import com.eshwar.rideconnectx.presentation.viewmodel.pairActions
import com.eshwar.rideconnectx.presentation.viewmodel.ScanViewModel

/**
 * Screen 14 — BLE Pairing.
 *
 * Runs the real scanner via [ScanViewModel]; devices shown are actual BLE
 * advertisements filtered to supported vehicles. Scanning starts on entry and
 * is stopped when the screen leaves, so the radio isn't left running.
 */
@Composable
fun BlePairingScreen(
    onBack: () -> Unit,
    onPaired: (address: String) -> Unit,
    onSkip: () -> Unit,
    vm: ScanViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val state by vm.uiState.collectAsStateWithLifecycle()
    val services = rememberSystemServices()

    DisposableEffect(Unit) {
        vm.onIntent(ScanUiIntent.StartScan)
        onDispose { vm.onIntent(ScanUiIntent.StopScan) }
    }

    val connected = state.connectionState as? ConnectionState.Connected

    val scanning = state.connectionState is ConnectionState.Scanning
    val ring = if (connected != null) c.green else c.blue

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
            BackHeader(title = "Pair Your Vehicle", onBack = onBack)

            Text(
                "Ensure your vehicle's ignition is on",
                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                color = c.muted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            RadarPulse(
                active = scanning,
                ring = ring,
                connected = connected != null,
                found = state.devices.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
            )

            Text(
                text = when {
                    !state.isBluetoothEnabled -> "Bluetooth is off"
                    connected != null -> "Paired Successfully!"
                    state.connectionState is ConnectionState.Connecting -> "Connecting…"
                    // Failures used to be invisible; the rider saw nothing
                    // happen at all. They are now stated on screen.
                    state.connectionState is ConnectionState.UnsupportedDevice ->
                        "That device isn't a supported vehicle"
                    state.connectionState is ConnectionState.Failed ->
                        (state.connectionState as ConnectionState.Failed).reason
                    state.connectionState is ConnectionState.Disconnected ->
                        "Disconnected — tap Connect to try again"
                    state.devices.isNotEmpty() -> "${state.devices.size} vehicle${if (state.devices.size > 1) "s" else ""} found"
                    scanning -> "Scanning for nearby vehicles…"
                    state.scanCompleted -> "No vehicles found"
                    else -> "Ready to scan"
                },
                style = RcxType.Label.copy(fontSize = 14.sp),
                color = if (connected != null) c.green else c.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(16.dp))

            when {
                // These used to be dead text. They now do the thing they ask
                // for — the app cannot flip the radio itself, so it opens the
                // system UI that can.
                !state.isBluetoothEnabled -> SystemWarning(
                    message = androidx.compose.ui.res.stringResource(R.string.perm_bt_off),
                    action = androidx.compose.ui.res.stringResource(R.string.perm_bt_turn_on),
                    onAction = services.turnOnBluetooth,
                )
                !state.isLocationEnabled -> SystemWarning(
                    message = androidx.compose.ui.res.stringResource(R.string.pair_loc_off),
                    action = androidx.compose.ui.res.stringResource(R.string.perm_loc_open),
                    onAction = services.openLocationSettings,
                )
                // A finished scan with nothing to show used to leave a blank
                // panel and a spinner that had already stopped. Say what
                // happened, say what to check, and offer to look again.
                state.devices.isEmpty() && state.scanCompleted && connected == null ->
                    NothingFound(modifier = Modifier.weight(1f))

                else -> LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.devices, key = { it.address }) { device ->
                        // Devices appear as they are found rather than popping in.
                        Box(Modifier.animateItem().enterRise()) {
                        DeviceRow(
                            device = device,
                            connecting = (state.connectionState as? ConnectionState.Connecting)
                                ?.address == device.address,
                            connected = connected?.address == device.address,
                            onConnect = { vm.onIntent(ScanUiIntent.SelectDevice(device.address)) },
                        )
                        }
                    }
                }
            }

            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PrimaryButton(
                    label = "Go to Dashboard",
                    onClick = { connected?.let { onPaired(it.address) } },
                    enabled = connected != null,
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward, null,
                            Modifier.size(17.dp), tint = androidx.compose.material3.LocalContentColor.current,
                        )
                    },
                )
                // Once connected there was no way back off the link short of
                // killing the app; this is that way.
                val actions = pairActions(state)
                if (actions.stop) {
                    PrimaryButton(
                        label = "Stop scan",
                        onClick = { vm.onIntent(ScanUiIntent.StopScan) },
                        secondary = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (actions.connectLast) {
                    PrimaryButton(
                        label = "Connect",
                        onClick = { state.lastDeviceAddress?.let { vm.onIntent(ScanUiIntent.SelectDevice(it)) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (actions.scanAgain) {
                    PrimaryButton(
                        label = "Scan again",
                        onClick = { vm.onIntent(ScanUiIntent.StartScan) },
                        secondary = true,
                        modifier = Modifier.fillMaxWidth(),
                        icon = { Icon(Icons.Filled.Refresh, null, Modifier.size(17.dp), tint = c.blue) },
                    )
                }
                if (connected != null) {
                    PrimaryButton(
                        label = "Disconnect",
                        onClick = { vm.onIntent(ScanUiIntent.Disconnect) },
                        secondary = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Shown when a full 30-second scan finished and found nothing.
 *
 * The checklist is the three things that actually cause it, in the order they
 * are worth checking — every one of them is in `PROJECT-STATUS.md` as a trap
 * that has already cost a session.
 */
@Composable
private fun NothingFound(
    modifier: Modifier = Modifier,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier
            .fillMaxWidth()
            // This sits in a weighted slot with no scroller of its own, so on a
            // short screen the illustration would push "Scan again" off the
            // bottom — the one control the rider came here for.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .cardSurface(shape)
                .padding(20.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.SearchOff, null,
                    Modifier.size(20.dp), tint = c.amber,
                )
                Text(
                    "Nothing found nearby",
                    style = RcxType.Label.copy(fontSize = 15.sp),
                    color = c.text,
                )
            }
            Spacer(Modifier.height(14.dp))

            // Illustration only, and deliberately without a caption naming a
            // button or a sequence: how the Access cluster is put into pair
            // mode is not documented anywhere in this project, and a made-up
            // instruction on this screen would cost the rider a trip to the
            // scooter. The three checks below are the ones we can stand behind.
            RcxPhotoFill(
                res = R.drawable.img_pairing_cluster_select,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .clip(RoundedCornerShape(14.dp)),
                // The only daylight photograph in the set, so on the dark theme
                // it arrives as a bright rectangle in the middle of the card.
                // Knocked back just enough to sit on the surface; any further
                // and the switchgear it is here to show stops being legible.
                darken = 0.18f,
            )

            Spacer(Modifier.height(14.dp))
            listOf(
                "Turn the scooter's ignition on — the cluster only advertises when it is powered.",
                "Stand within a few metres of the vehicle.",
                "Close the official app. It holds the cluster exclusively, and only one app can have it.",
            ).forEach { line ->
                Row(
                    Modifier.padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.padding(top = 6.dp).size(5.dp)
                            .clip(RoundedCornerShape(3.dp)).background(c.blue)
                    )
                    Text(
                        line,
                        style = RcxType.BodySmall.copy(fontSize = 12.sp),
                        color = c.muted,
                    )
                }
            }
        }

    }
}

/** Concentric pulsing rings around a Bluetooth badge, as in the design. */
@Composable
private fun RadarPulse(
    active: Boolean,
    ring: Color,
    connected: Boolean,
    modifier: Modifier = Modifier,
    /** Scooters found so far: each one pops in as a dot on a ring. */
    found: Int = 0,
) {
    val c = Rcx.colors
    val transition = rememberAmbientTransition("radar")

    Box(modifier, contentAlignment = Alignment.Center) {
        repeat(3) { i ->
            val progress by transition.animateAmbient(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(durationMillis = 1500 + i * 500, delayMillis = i * 350),
                    RepeatMode.Restart,
                ),
                label = "ring$i",
            )
            val size = (72 + i * 44).dp
            Box(
                Modifier
                    .size(size)
                    .scale(if (active) 0.85f + progress * 0.35f else 1f)
                    .alpha(if (active) (1f - progress) * 0.6f else 0.35f)
                    .clip(CircleShape)
                    .border(1.dp, ring.copy(alpha = 0.16f), CircleShape)
            )
        }

        if (connected) com.eshwar.rideconnectx.presentation.theme.Burst(ring, Modifier.size(170.dp))

        // One dot per scooter found, each on its own ring and bearing.
        repeat(found.coerceAtMost(3)) { i ->
            val angle = Math.toRadians((35 + i * 125).toDouble())
            val radius = (36 + i * 22).dp
            androidx.compose.runtime.key(i) {
                Box(
                    Modifier
                        .offset(x = radius * kotlin.math.cos(angle).toFloat(), y = radius * kotlin.math.sin(angle).toFloat())
                        .popIn()
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(c.green)
                        .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                )
            }
        }

        Box(
            Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(
                    if (c.isDark) Brush.linearGradient(listOf(Color(0xFF0E1E3D), Color(0xFF162848)))
                    else Brush.linearGradient(listOf(Color(0xFFD8E8FF), Color.White))
                )
                .border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (connected) Icons.Default.Check else Icons.Default.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = ring,
            )
        }
    }
}

@Composable
private fun SystemWarning(message: String, action: String, onAction: () -> Unit) {
    val c = Rcx.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.amber.copy(alpha = 0.06f))
            .border(1.dp, c.amber.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.BluetoothDisabled, null, Modifier.size(16.dp), tint = c.amber)
            Text(message, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.muted)
        }

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.amber)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onAction),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                action,
                style = RcxType.Label.copy(fontSize = 14.sp),
                color = Color(0xFF1A1A2E),
            )
        }
    }
}

/** One discovered device, with live signal strength. */
@Composable
private fun DeviceRow(
    device: BleDevice,
    connecting: Boolean,
    connected: Boolean,
    onConnect: () -> Unit,
) {
    val c = Rcx.colors
    val accent by animateColorAsState(if (connected) c.green else c.blue, tween(500), label = "deviceAccent")
    val shape = RoundedCornerShape(20.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardFill()
            .border(1.dp, if (connected) accent.copy(alpha = 0.21f) else c.border, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Bluetooth, null, Modifier.size(22.dp), tint = accent)
        }

        Column(Modifier.weight(1f)) {
            Text(
                device.name ?: androidx.compose.ui.res.stringResource(R.string.pair_unknown_vehicle),
                style = RcxType.Label.copy(fontSize = 14.sp),
                color = c.text,
            )
            // The reference app lists a vehicle by name only — no MAC, no dBm.
            // Those are debug details and read as clutter to a rider.
            Text(
                when {
                    connected -> androidx.compose.ui.res.stringResource(R.string.pair_connected)
                    connecting -> androidx.compose.ui.res.stringResource(R.string.pair_connecting)
                    else -> androidx.compose.ui.res.stringResource(R.string.pair_ready)
                },
                style = RcxType.BodySmall.copy(fontSize = 11.sp),
                color = c.muted,
            )
        }

        // Connect -> spinner -> tick, each swapping in with a small pop.
        AnimatedContent(
            targetState = when { connected -> 2; connecting -> 1; else -> 0 },
            transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut()) },
            label = "deviceAction",
        ) { step ->
        when (step) {
            2 -> Box(
                Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.09f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Check, null, Modifier.size(15.dp), tint = accent) }

            1 -> CircularProgressIndicator(
                modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = c.blue,
            )

            else -> {
                val connectDesc = androidx.compose.ui.res.stringResource(R.string.pair_connect_named, device.name ?: androidx.compose.ui.res.stringResource(R.string.pair_unknown_vehicle))
                Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .mainButtonFill(RoundedCornerShape(12.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onConnect)
                    .semantics { contentDescription = connectDesc }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.pair_connect), style = RcxType.Label.copy(fontSize = 12.sp), color = com.eshwar.rideconnectx.presentation.theme.mainButtonText)
            }
            }
        }
        }
    }
}
