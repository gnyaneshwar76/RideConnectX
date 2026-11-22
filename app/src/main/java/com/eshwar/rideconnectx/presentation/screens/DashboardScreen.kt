package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.presentation.components.safeDrawingCentred
import com.eshwar.rideconnectx.presentation.components.contentWidth
import com.eshwar.rideconnectx.presentation.theme.RollingText
import com.eshwar.rideconnectx.presentation.theme.runningBorder
import androidx.compose.runtime.saveable.rememberSaveable
import com.eshwar.rideconnectx.presentation.theme.rememberTilt
import com.eshwar.rideconnectx.presentation.theme.FlipCard
import com.eshwar.rideconnectx.presentation.theme.pressTilt
import com.eshwar.rideconnectx.presentation.theme.pressSquash
import com.eshwar.rideconnectx.domain.model.Vehicle
import com.eshwar.rideconnectx.domain.model.DistanceUnit
import com.eshwar.rideconnectx.presentation.viewmodel.DashboardUiState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.text.font.FontWeight
import com.eshwar.rideconnectx.presentation.theme.popIn
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawWithContent
import com.eshwar.rideconnectx.presentation.theme.isGlass
import com.eshwar.rideconnectx.presentation.theme.glassButton
import com.eshwar.rideconnectx.presentation.theme.cardFill
import com.eshwar.rideconnectx.presentation.theme.cardSurface
import com.eshwar.rideconnectx.presentation.theme.GlassLightField
import com.eshwar.rideconnectx.presentation.theme.LocalStyleMode
import com.eshwar.rideconnectx.presentation.theme.StyleMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import com.eshwar.rideconnectx.presentation.theme.RcxMotion
import com.eshwar.rideconnectx.presentation.theme.animatedCount
import com.eshwar.rideconnectx.presentation.theme.enterRise
import com.eshwar.rideconnectx.presentation.theme.rememberBreath
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import com.eshwar.rideconnectx.presentation.components.dockSpace
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.domain.model.ServiceStatus
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.components.RcxPhotoFill
import com.eshwar.rideconnectx.presentation.components.RcxSurface
import com.eshwar.rideconnectx.presentation.components.RiderAvatar
import com.eshwar.rideconnectx.presentation.components.VehicleArtwork
import com.eshwar.rideconnectx.presentation.components.VehicleStage
import com.eshwar.rideconnectx.presentation.theme.LocalDistanceUnit
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.viewmodel.DashboardViewModel
import java.util.Calendar

/**
 * Screen 13 — Dashboard.
 *
 * The app's hub. Reads live connection state and telemetry from
 * [DashboardViewModel], and the rider's name and chosen vehicle from the
 * setup flow. Values shown as "—" simply mean the cluster hasn't reported
 * that field yet; nothing here is faked.
 */
@Composable
fun DashboardScreen(
    onNavigate: () -> Unit,
    onPairVehicle: () -> Unit,
    onStatistics: () -> Unit,
    onNotifications: () -> Unit,
    onService: () -> Unit,
    onSafety: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit,
    vm: DashboardViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val state by vm.uiState.collectAsStateWithLifecycle()
    val serviceStatus by vm.serviceStatus.collectAsStateWithLifecycle()
    val distanceUnit by vm.distanceUnit.collectAsStateWithLifecycle()

    val glassMode = LocalStyleMode.current == StyleMode.GLASS
    Box(
        Modifier
            .fillMaxSize()
            .background(
                // Same colour the photo fades into, or a seam shows where the photo ends.
                Brush.verticalGradient(listOf(c.bg, c.bg))
            )
    ) {
        // The scooter picture on the card stays; only the backdrop photo gives way to Glow.
        if (com.eshwar.rideconnectx.presentation.theme.isGlow) com.eshwar.rideconnectx.presentation.theme.GlowField()
        else if (glassMode) GlassLightField(photo = R.drawable.img_onboarding_intelligence, accent = c.blue)
        Column(
            Modifier
                .fillMaxHeight()
                // The cap has to come *before* the width is filled. After a
                // fillMaxSize() the minimum width is already the whole screen,
                // so widthIn(max) cannot shrink it and the column ran the full
                // width of a tablet. Invisible on a phone, where the screen is
                // narrower than 600dp to begin with.
                .contentWidth()
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                // Insets go inside the cap so the column is centred on the screen, not on the
                // area left after a one-sided cutout (landscape margin was uneven).
                .safeDrawingCentred()
                .dockSpace()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Greeting + connection pill ──────────────────────────
            Row(
                Modifier.fillMaxWidth().enterRise(0),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The greeting is the way into Profile — the rider's own name
                // and face are the most natural thing to tap for their account.
                val profileSrc = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .pressSquash(profileSrc)
                        // The tap area reaches 4 dp past the circle on the left, like it does above and below.
                        .offset(x = (-4).dp)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(profileSrc, androidx.compose.foundation.LocalIndication.current, role = androidx.compose.ui.semantics.Role.Button, onClick = onProfile)
                        .padding(start = 4.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RiderAvatar(name = state.nickname.ifBlank { state.riderName }, size = 40.dp)
                    Column {
                        val hello = greeting()
                        var typed by rememberSaveable { mutableStateOf(false) }
                        val letters = remember { androidx.compose.animation.core.Animatable(if (typed) hello.length.toFloat() else 0f) }
                        LaunchedEffect(Unit) {
                            if (!typed) {
                                letters.animateTo(hello.length.toFloat(), tween(hello.length * 45, delayMillis = 250, easing = androidx.compose.animation.core.LinearEasing))
                                typed = true
                            }
                        }
                        // The full text holds the width; only the shown letters are painted.
                        Box {
                            Text(hello, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = Color.Transparent)
                            Text(hello.take(letters.value.toInt()), style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.muted)
                        }
                        Text(
                            state.nickname.ifBlank { state.riderName },
                            style = RcxType.Wordmark.copy(fontSize = 18.sp),
                            color = c.text,
                            maxLines = 1,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Only the bell up here. Two 34dp icon buttons plus the
                    // connection pill left the gear squeezed against the screen
                    // edge and too small to hit; Settings is not something the
                    // rider reaches for mid-glance, so it moved down into Quick
                    // Actions where there is room for a proper target.
                    // A short buzz the moment the scooter links, so it is felt
                    // with the phone in a pocket. Not on opening already linked.
                    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
                    var wasConnected by remember { mutableStateOf(state.isConnected) }
                    LaunchedEffect(state.isConnected) {
                        if (state.isConnected && !wasConnected) {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                        }
                        wasConnected = state.isConnected
                    }
                    HomeShieldButton(onSafety)
                    val unread by vm.unreadCount.collectAsStateWithLifecycle()
                    HeaderIconButton(Icons.Default.Notifications, stringResource(R.string.dash_notifications), onNotifications, badge = unread)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Vehicle hero ────────────────────────────────────────
            val vehicle = state.vehicle
            if (vehicle == null) {
                Box(Modifier.enterRise(1)) { NoVehicleCard(onPairVehicle) }
            } else {
                val accent = vehicle.accent
                // Tap to turn the card over; it also leans with the phone.
                var flipped by rememberSaveable { mutableStateOf(false) }
                val tilt = rememberTilt()
                FlipCard(
                    flipped = flipped,
                    modifier = Modifier
                        .enterRise(1)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "Turn the card over",
                            role = androidx.compose.ui.semantics.Role.Button,
                        ) { flipped = !flipped },
                    tilt = tilt,
                    back = { VehicleCardBack(state, vehicle, distanceUnit) },
                ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(accent.copy(alpha = 0.10f), c.card)
                            )
                        )
                        .border(1.dp, accent.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
                        .runningBorder(
                            state.connectionState is ConnectionState.Scanning ||
                                state.connectionState is ConnectionState.Connecting,
                            c.cyan, 22.dp,
                        )
                        .padding(16.dp),
                ) {
                    // The vehicle is the subject of this screen, so it gets the
                    // width of the card and the name sits under it. It used to
                    // be a 130dp thumbnail in the right-hand third of a row,
                    // with "Connected Vehicle" — a label saying nothing the
                    // rider does not already know — taking the larger half.
                    // The hero: about 40% of the screen height.
                    val heroHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.4f)
                        .coerceIn(240f, 420f).dp
                    Box {
                        VehicleStage(
                            vehicle = vehicle,
                            colorway = state.vehicleColor,
                            accent = accent,
                            modifier = Modifier.clip(RoundedCornerShape(18.dp)),
                            height = heroHeight,
                            live = state.isConnected,
                            scanning = state.connectionState is ConnectionState.Scanning ||
                                state.connectionState is ConnectionState.Connecting,
                            rollIn = true,
                            tilt = tilt,
                        )
                        Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                            ConnectionPill(connected = state.isConnected, onClick = onPairVehicle)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                vehicle.name,
                                style = RcxType.Wordmark.copy(fontSize = 20.sp),
                                color = c.text,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Text(
                                state.vehicleColor?.name ?: vehicle.subtitle,
                                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                                color = c.muted,
                            )
                        }
                    }
                }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Readings: Odometer, Fuel, Trip A, Next service ──────
            CompositionLocalProvider(LocalDistanceUnit provides distanceUnit) {
                Box(Modifier.enterRise(2)) {
                    HomeReadings(
                        odometerKm = state.odometer,
                        tripAKm = state.tripAKm,
                        fuelSegments = state.fuelSegments,
                        unit = distanceUnit,
                        service = serviceStatus,
                        onService = onService,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/** The back of the vehicle card: what it is and what it last reported. */
@Composable
private fun VehicleCardBack(state: DashboardUiState, vehicle: Vehicle, unit: DistanceUnit) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Brush.linearGradient(listOf(vehicle.accent.copy(alpha = 0.16f), c.card)))
            .border(1.dp, vehicle.accent.copy(alpha = 0.16f), shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text("VEHICLE DETAILS", style = RcxType.MonoTiny.copy(fontSize = 10.sp), color = c.muted)
        listOf(
            "Model" to vehicle.name,
            "Colour" to (state.vehicleColor?.name ?: "—"),
            "Cluster" to (if (state.isConnected) state.deviceName else "Not connected"),
            "Odometer" to (state.odometer?.let { unit.format(it) } ?: "—"),
            "Trip A" to (state.tripAKm?.let { "%.1f %s".format(unit.fromKm(it), unit.short) } ?: "—"),
        ).forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.muted, modifier = Modifier.weight(1f))
                Text(value, style = RcxType.Label.copy(fontSize = 14.sp), color = c.text)
            }
        }
        Text("Tap to turn back", style = RcxType.BodySmall.copy(fontSize = 11.sp), color = c.muted)
    }
}

/** Square icon button in the dashboard header — bell, gear. */
@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    badge: Int = 0,
) {
    val c = Rcx.colors
    // The bell swings once each time something new arrives.
    val swing = remember { androidx.compose.animation.core.Animatable(0f) }
    var seen by remember { mutableStateOf(badge) }
    LaunchedEffect(badge) {
        if (badge > seen) swing.animateTo(0f, androidx.compose.animation.core.keyframes {
            durationMillis = 620
            -18f at 90; 15f at 200; -11f at 310; 7f at 420; -3f at 520; 0f at 620
        })
        seen = badge
    }
    val src = remember { MutableInteractionSource() }
    // The tap area is 48 dp; the button you see stays 36 dp inside it.
    Box(
        Modifier
            .pressSquash(src)
            .size(48.dp)
            .clip(CircleShape)
            .clickable(src, androidx.compose.foundation.LocalIndication.current, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
    Box(Modifier.size(36.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .cardSurface(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, contentDescription,
                Modifier.size(16.dp).graphicsLayer {
                    rotationZ = swing.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                },
                tint = if (badge > 0) c.text else c.muted,
            )
        }
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .popIn()
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.red)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (badge > 9) "9+" else badge.toString(),
                    style = RcxType.Mono.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
    }
    }
}

@Composable
private fun greeting(): String =
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> stringResource(R.string.dash_greeting_morning)
        in 12..16 -> stringResource(R.string.dash_greeting_afternoon)
        in 17..21 -> stringResource(R.string.dash_greeting_evening)
        // After 10 pm and before 5 am "Good evening" is wrong; keep it calm.
        else -> stringResource(R.string.dash_greeting_night)
    }

@Composable
private fun ConnectionPill(connected: Boolean, onClick: () -> Unit) {
    val c = Rcx.colors
    val tint by animateColorAsState(if (connected) c.green else c.muted, tween(500), label = "pillTint")
    val ping by rememberBreath(1600, "pillPing", active = connected)
    // The outer box gives a 48dp touch target; the pill keeps its look.
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressSquash(src)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(src, androidx.compose.foundation.LocalIndication.current, onClickLabel = stringResource(R.string.dash_pair_vehicle_label), role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
    Row(
        Modifier
            .clip(CircleShape)
            // Solid card colour under the tint so the pill reads over the picture (no outer halo).
            .background(c.card)
            .background(tint.copy(alpha = 0.07f))
            .border(1.dp, tint.copy(alpha = 0.16f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // A soft ring pulses out of the dot while the link is live.
            if (connected) Box(
                Modifier.size(12.dp)
                    .graphicsLayer { scaleX = 0.5f + ping; scaleY = 0.5f + ping; alpha = 1f - ping }
                    .border(1.dp, tint, CircleShape)
            )
            Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
        }
        Text(
            if (connected) stringResource(R.string.dash_connected)
            else stringResource(R.string.dash_offline),
            style = RcxType.Mono.copy(fontSize = 10.sp),
            color = tint,
        )
    }
    }
}

@Composable
private fun NoVehicleCard(onPair: () -> Unit) {
    val c = Rcx.colors
    val src = remember { MutableInteractionSource() }
    Column(
        Modifier
            .pressSquash(src)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .cardSurface(RoundedCornerShape(22.dp))
            .clickable(src, androidx.compose.foundation.LocalIndication.current, role = androidx.compose.ui.semantics.Role.Button, onClick = onPair)
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.dash_no_vehicle), style = RcxType.Label.copy(fontSize = 15.sp), color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(
            // Names only what the scooter actually sends. Speed is not in the
            // telemetry frame, and phone battery belongs on the cluster.
            stringResource(R.string.dash_no_vehicle_body),
            style = RcxType.BodySmall.copy(fontSize = 12.sp),
            color = c.muted,
        )
    }
}
