package com.eshwar.rideconnectx.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.domain.model.DistanceUnit
import com.eshwar.rideconnectx.domain.model.ServiceStatus
import com.eshwar.rideconnectx.presentation.components.RcxSurface
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.pressSquash
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.theme.RollingText
import com.eshwar.rideconnectx.presentation.theme.runningBorder

/** What the Next service tile says. Pure, so it can be unit tested. */
enum class NextServiceKind { NO_BASELINE, OVERDUE, DUE_SOON, KM_LEFT, DAYS_LEFT }

data class NextServiceLine(val kind: NextServiceKind, val amount: Int = 0)

fun nextServiceLine(s: ServiceStatus): NextServiceLine {
    val km = s.remainingKm
    val days = s.remainingDays
    return when {
        !s.hasBaseline -> NextServiceLine(NextServiceKind.NO_BASELINE)
        s.isOverdue -> NextServiceLine(NextServiceKind.OVERDUE)
        s.isDueSoon -> NextServiceLine(NextServiceKind.DUE_SOON)
        km != null && km > 0 -> NextServiceLine(NextServiceKind.KM_LEFT, km)
        days != null && days > 0 -> NextServiceLine(NextServiceKind.DAYS_LEFT, days)
        else -> NextServiceLine(NextServiceKind.NO_BASELINE)
    }
}

/** Calm red shield: opens Safety. 48dp touch area. */
@Composable
fun HomeShieldButton(onClick: () -> Unit) {
    val red = Rcx.colors.red
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressSquash(src)
            .size(48.dp)
            .clip(CircleShape)
            .background(red.copy(alpha = 0.14f))
            .border(1.dp, red.copy(alpha = 0.45f), CircleShape)
            .clickable(src, LocalIndication.current, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Shield, stringResource(R.string.dash_safety), Modifier.size(26.dp), tint = red)
    }
}

@Composable
private fun HomeTile(
    label: String,
    description: String,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    // The squash scales the whole tile; the click (and its ripple) sits INSIDE
    // the surface so it is clipped to the rounded corners, not a square box.
    val squash = if (onClick != null) Modifier.pressSquash(src) else Modifier
    val tapped = if (onClick != null) Modifier.clickable(src, LocalIndication.current, role = Role.Button, onClick = onClick) else Modifier
    RcxSurface(modifier.then(squash).semantics(mergeDescendants = true) { contentDescription = description }) {
        Column(Modifier.fillMaxSize().then(tapped).padding(16.dp)) {
            Text(label, style = RcxType.MonoTiny.copy(fontSize = 12.sp), color = Rcx.colors.muted)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun BigValue(text: String, color: Color) =
    Text(text, style = RcxType.Wordmark.copy(fontSize = 28.sp), color = color)

/** Odometer, Fuel, Trip A, Trip B: a 2x2 grid that grows with the font size. */
@Composable
fun HomeReadings(
    odometerKm: Int?,
    tripAKm: Float?,
    tripBKm: Float?,
    fuelSegments: Int?,
    unit: DistanceUnit,
) {
    val c = Rcx.colors
    val none = "—"
    val tile = Modifier.fillMaxHeight()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val odoLabel = stringResource(R.string.dash_odo)
            val odo = odometerKm?.let { "%d".format(unit.fromKm(it)) }
            HomeTile(odoLabel, "$odoLabel, ${odo?.let { "$it ${unit.short}" } ?: none}", tile.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingText(odo ?: none, style = RcxType.Wordmark.copy(fontSize = 28.sp), color = c.blue)
                    Text(" ${unit.short}", style = RcxType.Mono.copy(fontSize = 12.sp), color = c.muted)
                }
            }
            FuelTile(fuelSegments, tile.weight(1f))
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TripTile(stringResource(R.string.dash_trip_a), tripAKm, unit, tile.weight(1f))
            TripTile(stringResource(R.string.dash_trip_b), tripBKm, unit, tile.weight(1f))
        }
    }
}

@Composable
private fun TripTile(label: String, km: Float?, unit: DistanceUnit, modifier: Modifier) {
    val c = Rcx.colors
    val none = "—"
    val trip = km?.let { "%.1f".format(unit.fromKm(it)) }
    HomeTile(label, "$label, ${trip?.let { "$it ${unit.short}" } ?: none}", modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            RollingText(trip ?: none, style = RcxType.Wordmark.copy(fontSize = 28.sp), color = c.cyan)
            Text(" ${unit.short}", style = RcxType.Mono.copy(fontSize = 12.sp), color = c.muted)
        }
    }
}

@Composable
private fun FuelTile(segments: Int?, modifier: Modifier) {
    val c = Rcx.colors
    val total = ClusterFuelSegments
    val filled = segments?.coerceIn(0, total)
    val low = filled != null && filled <= 1
    val label = stringResource(R.string.dash_fuel)
    val state = when {
        filled == null -> "—"
        low -> stringResource(R.string.dash_fuel_reserve)
        else -> "$filled/$total"
    }
    HomeTile(label, "$label, $state", modifier) {
        BigValue(state, if (low) c.red else c.amber)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(total) { i ->
                val on = filled != null && i < filled
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (!on) c.muted.copy(alpha = 0.13f) else if (low) c.red else c.amber)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.dash_fuel_empty), style = RcxType.MonoTiny.copy(fontSize = 11.sp), color = c.muted)
            Text(stringResource(R.string.dash_fuel_full), style = RcxType.MonoTiny.copy(fontSize = 11.sp), color = c.muted)
        }
    }
}

/** Byte 24 of the telemetry frame is '0'..'5'. Hardware-confirmed. */
private const val ClusterFuelSegments = 5

/**
 * The vehicle hero card: model and colour picture, connection pill, name and colour.
 * Tap turns it over to the details. Shared by Home and My scooter, so the two always match.
 */
@Composable
fun VehicleHeroCard(
    state: com.eshwar.rideconnectx.presentation.viewmodel.DashboardUiState,
    vehicle: com.eshwar.rideconnectx.domain.model.Vehicle,
    distanceUnit: DistanceUnit,
    onPairVehicle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Rcx.colors
    val accent = vehicle.accent
    var flipped by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val tilt = com.eshwar.rideconnectx.presentation.theme.rememberTilt()
    val linking = state.connectionState is com.eshwar.rideconnectx.domain.repository.ConnectionState.Scanning ||
        state.connectionState is com.eshwar.rideconnectx.domain.repository.ConnectionState.Connecting
    com.eshwar.rideconnectx.presentation.theme.FlipCard(
        flipped = flipped,
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClickLabel = "Turn the card over",
            role = Role.Button,
        ) { flipped = !flipped },
        tilt = tilt,
        back = { VehicleCardBack(state, vehicle, distanceUnit) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(accent.copy(alpha = 0.10f), c.card)))
                .border(1.dp, accent.copy(alpha = 0.16f), RoundedCornerShape(22.dp))
                .runningBorder(linking, c.cyan, 22.dp)
                .padding(16.dp),
        ) {
            // The hero: about 40% of the screen height.
            val heroHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.4f)
                .coerceIn(240f, 420f).dp
            Box {
                com.eshwar.rideconnectx.presentation.components.VehicleStage(
                    vehicle = vehicle,
                    colorway = state.vehicleColor,
                    accent = accent,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)),
                    height = heroHeight,
                    live = state.isConnected,
                    scanning = linking,
                    rollIn = true,
                    tilt = tilt,
                )
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                    ConnectionPill(connected = state.isConnected, onClick = onPairVehicle)
                }
            }
            Spacer(Modifier.height(12.dp))
            Column {
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

/** The back of the vehicle card: what it is and what it last reported. */
@Composable
private fun VehicleCardBack(
    state: com.eshwar.rideconnectx.presentation.viewmodel.DashboardUiState,
    vehicle: com.eshwar.rideconnectx.domain.model.Vehicle,
    unit: DistanceUnit,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(vehicle.accent.copy(alpha = 0.16f), c.card)))
            .border(1.dp, vehicle.accent.copy(alpha = 0.16f), shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text("VEHICLE DETAILS", style = RcxType.MonoTiny.copy(fontSize = 12.sp), color = c.muted)
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
        Text("Tap to turn back", style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.muted)
    }
}
