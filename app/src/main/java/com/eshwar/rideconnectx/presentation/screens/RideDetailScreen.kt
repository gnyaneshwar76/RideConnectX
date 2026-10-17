package com.eshwar.rideconnectx.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.data.local.LatLon
import com.eshwar.rideconnectx.data.local.RoutePolyline
import com.eshwar.rideconnectx.data.local.db.CloudState
import com.eshwar.rideconnectx.data.local.db.KeepState
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.theme.LocalDistanceUnit
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.theme.cardSurface
import com.eshwar.rideconnectx.presentation.viewmodel.RideDetailViewModel
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DAY_MS = 86_400_000L

@Composable
fun RideDetailScreen(onBack: () -> Unit, vm: RideDetailViewModel = hiltViewModel()) {
    val c = Rcx.colors
    val ride by vm.ride.collectAsStateWithLifecycle()
    val guest by vm.isGuest.collectAsStateWithLifecycle()
    val unit = LocalDistanceUnit.current
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.ride_delete_title)) },
            text = { Text(stringResource(R.string.ride_delete_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false; vm.delete(); onBack() }) {
                    Text(stringResource(R.string.ride_delete_confirm), color = c.red)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.notifs_cancel))
                }
            },
        )
    }
    val r = ride
    val points = remember(r?.route) { r?.route?.let(RoutePolyline::decode).orEmpty() }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).widthIn(max = 600.dp)
                .fillMaxWidth().align(Alignment.TopCenter),
        ) {
            BackHeader(title = stringResource(R.string.ride_detail_title), onBack = onBack)
            if (r == null) {
                Text(stringResource(R.string.ride_gone), color = c.muted, style = RcxType.BodySmall, modifier = Modifier.padding(20.dp))
                return@Column
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RouteCanvas(points, Modifier.fillMaxWidth().height(260.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .cardSurface(RoundedCornerShape(18.dp)).padding(16.dp)
                        .semantics(mergeDescendants = true) {},
                ) {
                    val dist = "%.2f %s".format(unit.fromKm(r.distanceMeters / 1000f), unit.short)
                    Text(dist, style = RcxType.Wordmark.copy(fontSize = 24.sp), color = c.blue)
                    Text(
                        "${formatDuration(r.durationMillis)} · ${stringResource(R.string.live_avg)} ${unit.fromKm(r.avgSpeedKmh.toFloat()).roundToInt()} ${unit.speed} · ${stringResource(R.string.live_max)} ${unit.fromKm(r.topSpeedKmh.toFloat()).roundToInt()} ${unit.speed}",
                        style = RcxType.Mono.copy(fontSize = 11.sp), color = c.muted,
                    )
                    RideBadge(r)
                }

                if (r.keepState == KeepState.PENDING) {
                    val days = (((r.expiresAt ?: r.endedAt) - System.currentTimeMillis()) / DAY_MS + 1).coerceAtLeast(0)
                    Text(stringResource(R.string.ride_expiry_note), style = RcxType.BodySmall.copy(fontSize = 13.sp), color = c.text)
                    Text(stringResource(R.string.ride_days_left, days.toInt()), style = RcxType.Mono.copy(fontSize = 11.sp), color = c.amber)
                    Text(
                        stringResource(if (guest) R.string.ride_keep_guest else R.string.ride_keep_signed_in),
                        style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.muted,
                    )
                    PrimaryButton(stringResource(R.string.ride_keep), vm::keep, Modifier.fillMaxWidth())
                } else if (r.cloudState == CloudState.PHONE_ONLY || guest) {
                    Text(
                        stringResource(if (r.cloudState == CloudState.PHONE_ONLY) R.string.ride_phone_only_full else R.string.ride_keep_guest),
                        style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.muted,
                    )
                }
                PrimaryButton(stringResource(R.string.ride_delete), { confirmDelete = true }, Modifier.fillMaxWidth(), secondary = true)
            }
        }
    }
}

/** Points scaled to fit a w x h box (longitude squashed by cos(lat), as on a flat map). */
internal fun projectRoute(pts: List<LatLon>, w: Float, h: Float, pad: Float): List<Offset> {
    if (pts.isEmpty()) return emptyList()
    val k = cos(Math.toRadians(pts.sumOf { it.lat } / pts.size))
    val xs = pts.map { it.lon * k }; val ys = pts.map { -it.lat }
    val minX = xs.min(); val minY = ys.min()
    val spanX = xs.max() - minX; val spanY = ys.max() - minY
    val aw = max(w - 2 * pad, 1f); val ah = max(h - 2 * pad, 1f)
    // One spot (or a dead straight line) has a zero span: scale 0 centres it.
    val scale = if (max(spanX, spanY) < 1e-9) 0.0 else min(aw / spanX.coerceAtLeast(1e-12), ah / spanY.coerceAtLeast(1e-12))
    val ox = pad + (aw - spanX * scale) / 2; val oy = pad + (ah - spanY * scale) / 2
    return pts.indices.map { Offset((ox + (xs[it] - minX) * scale).toFloat(), (oy + (ys[it] - minY) * scale).toFloat()) }
}

/** Offline route drawing: plain background, line, green start dot, red end dot. No map tiles. */
@Composable
fun RouteCanvas(points: List<LatLon>, modifier: Modifier = Modifier) {
    val c = Rcx.colors
    val km = if (points.size > 1) points.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) } / 1000 else 0.0
    val desc = if (points.isEmpty()) stringResource(R.string.route_none)
    else stringResource(R.string.route_desc, "%.1f".format(km))
    Box(
        modifier.clip(RoundedCornerShape(18.dp)).cardSurface(RoundedCornerShape(18.dp)).semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        if (points.isEmpty()) {
            Text(stringResource(R.string.route_none), color = c.muted, style = RcxType.BodySmall)
            return@Box
        }
        Canvas(Modifier.fillMaxSize()) {
            val p = projectRoute(points, size.width, size.height, 28.dp.toPx())
            if (p.size > 1) {
                val path = Path().apply { moveTo(p[0].x, p[0].y); p.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(path, c.blue, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drawCircle(c.green, 7.dp.toPx(), p.first())
            if (p.size > 1) drawCircle(c.red, 7.dp.toPx(), p.last())
            drawCircle(Color.White, 2.5.dp.toPx(), p.first())
        }
    }
}
