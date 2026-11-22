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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
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
            Text(label, style = RcxType.MonoTiny.copy(fontSize = 10.sp), color = Rcx.colors.muted)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun BigValue(text: String, color: Color) =
    Text(text, style = RcxType.Wordmark.copy(fontSize = 28.sp), color = color)

/** Odometer, Fuel, Trip A, Next service: a 2x2 grid that grows with the font size. */
@Composable
fun HomeReadings(
    odometerKm: Int?,
    tripAKm: Float?,
    fuelSegments: Int?,
    unit: DistanceUnit,
    service: ServiceStatus,
    onService: () -> Unit,
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
                    Text(" ${unit.short}", style = RcxType.Mono.copy(fontSize = 11.sp), color = c.muted)
                }
            }
            FuelTile(fuelSegments, tile.weight(1f))
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val tripLabel = stringResource(R.string.dash_trip_a)
            val trip = tripAKm?.let { "%.1f".format(unit.fromKm(it)) }
            HomeTile(tripLabel, "$tripLabel, ${trip?.let { "$it ${unit.short}" } ?: none}", tile.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingText(trip ?: none, style = RcxType.Wordmark.copy(fontSize = 28.sp), color = c.cyan)
                    Text(" ${unit.short}", style = RcxType.Mono.copy(fontSize = 11.sp), color = c.muted)
                }
            }
            ServiceTile(service, unit, onService, tile.weight(1f))
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
            Text(stringResource(R.string.dash_fuel_empty), style = RcxType.MonoTiny.copy(fontSize = 9.sp), color = c.muted)
            Text(stringResource(R.string.dash_fuel_full), style = RcxType.MonoTiny.copy(fontSize = 9.sp), color = c.muted)
        }
    }
}

@Composable
private fun ServiceTile(status: ServiceStatus, unit: DistanceUnit, onClick: () -> Unit, modifier: Modifier) {
    val c = Rcx.colors
    val line = nextServiceLine(status)
    val label = stringResource(R.string.dash_next_service).uppercase()
    val value = when (line.kind) {
        NextServiceKind.KM_LEFT -> unit.format(line.amount)
        NextServiceKind.DAYS_LEFT -> pluralStringResource(R.plurals.dash_days, line.amount, line.amount)
        NextServiceKind.DUE_SOON -> stringResource(R.string.dash_service_due_soon)
        NextServiceKind.OVERDUE -> stringResource(R.string.dash_service_overdue)
        NextServiceKind.NO_BASELINE -> "—"
    }
    val sub = when (line.kind) {
        NextServiceKind.KM_LEFT, NextServiceKind.DAYS_LEFT -> stringResource(R.string.dash_service_left)
        NextServiceKind.NO_BASELINE -> stringResource(R.string.dash_no_baseline)
        else -> ""
    }
    val color = when (line.kind) {
        NextServiceKind.OVERDUE -> c.red
        NextServiceKind.DUE_SOON -> c.amber
        NextServiceKind.NO_BASELINE -> c.muted
        else -> c.green
    }
    HomeTile(label, "$label, $value $sub".trim(), modifier, onClick) {
        BigValue(value, color)
        if (sub.isNotEmpty()) Text(sub, style = RcxType.BodySmall.copy(fontSize = 11.sp), color = c.muted)
    }
}

/** Byte 24 of the telemetry frame is '0'..'5'. Hardware-confirmed. */
private const val ClusterFuelSegments = 5
