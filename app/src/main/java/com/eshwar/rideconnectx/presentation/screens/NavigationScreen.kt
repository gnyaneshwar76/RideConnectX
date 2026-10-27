package com.eshwar.rideconnectx.presentation.screens

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.eshwar.rideconnectx.presentation.theme.cardSurface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.graphicsLayer
import com.eshwar.rideconnectx.presentation.theme.enterRise
import com.eshwar.rideconnectx.presentation.theme.rememberBreath
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.domain.model.NavManeuver
import androidx.compose.runtime.LaunchedEffect
import com.eshwar.rideconnectx.core.util.rememberNotificationAccess
import com.eshwar.rideconnectx.domain.model.NavState
import com.eshwar.rideconnectx.R
import androidx.compose.ui.res.stringResource
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.components.RcxPhotoFill
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.viewmodel.NavigationViewModel

/**
 * Screen 15 — Navigation.
 *
 * The route itself lives in Google Maps: the rider hands off with "Open in
 * Google Maps", and each instruction Maps posts is read back and mirrored here
 * and onto the cluster. Until a route is running there is nothing to show, so
 * the screen leads with the hand-off rather than fabricating a maneuver.
 */
@Composable
fun NavigationScreen(
    onBack: () -> Unit,
    vm: NavigationViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val context = LocalContext.current
    val navState by vm.navState.collectAsStateWithLifecycle()
    val speed by vm.speedKmh.collectAsStateWithLifecycle()
    val clusterLinked by vm.clusterLinked.collectAsStateWithLifecycle()
    val vehicleConnected by vm.vehicleConnected.collectAsStateWithLifecycle()

    // Reading Maps' turn-by-turn needs notification access, which is granted on
    // a Settings page rather than by a dialog — so re-check whenever we return.
    val notificationAccess = rememberNotificationAccess()

    // Background run: one pop-up on the first visit after install, then a reminder
    // while it is off. Re-read on every resume (the rider returns from Settings).
    val backgroundRun = com.eshwar.rideconnectx.core.util.rememberBackgroundRunAccess()
    val popupShown by vm.popupShown.collectAsStateWithLifecycle()
    var showPopup by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) backgroundRun.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // firstOpen = this composition; the saved flag stops every later visit.
    LaunchedEffect(popupShown) {
        val shown = popupShown ?: return@LaunchedEffect
        if (com.eshwar.rideconnectx.core.util.BackgroundRunRules.showPopup(backgroundRun.granted, shown, true)) {
            showPopup = true
            vm.markPopupShown()
        }
    }
    if (showPopup) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPopup = false },
            title = { Text(stringResource(R.string.bgrun_title)) },
            text = { Text(stringResource(R.string.bgrun_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showPopup = false; backgroundRun.request() }) {
                    Text(stringResource(R.string.bgrun_allow))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showPopup = false }) {
                    Text(stringResource(R.string.bgrun_later))
                }
            },
        )
    }

    val openMaps = {
        vm.onHandOffToMaps()
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
                    .setPackage("com.google.android.apps.maps")
            )
        } catch (_: ActivityNotFoundException) {
            // Maps isn't installed — fall back to the browser rather than crash.
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/"))
            )
        }
    }

    Box(Modifier.fillMaxSize().background(c.outerBg)) {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            BackHeader(title = "Navigation", onBack = onBack)

            // Map area — the aerial photograph, then the design's grid and
            // route sketch over it.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // Heavily dimmed on purpose. The maneuver card is translucent
                // and the status cards put white text straight onto this, so
                // the photograph has to read as texture, not as a picture.
                RcxPhotoFill(
                    res = R.drawable.img_navigation_background,
                    modifier = Modifier.fillMaxSize(),
                    darken = 0.72f,
                    darkenColor = c.outerBg,
                )
                RouteBackdrop()

                when {
                    // Without this permission the relay can never receive a
                    // single instruction, so say that plainly instead of
                    // sitting on "waiting for Maps" forever.
                    !notificationAccess.granted -> StatusCard(
                        title = "Turn on notification access",
                        body = "RideConnectX reads Google Maps' turn-by-turn notification to mirror each " +
                            "instruction onto your vehicle's cluster. Android grants this from its own " +
                            "settings page.",
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                        action = "Open notification access" to notificationAccess.openSettings,
                    )

                    else -> when (val s = navState) {
                        is NavState.Active ->
                            if (s.maneuver.maneuverId == com.eshwar.rideconnectx.domain.ProtocolEngine.Maneuver.DESTINATION) {
                                ArrivalCard(
                                    s.maneuver.instruction,
                                    Modifier.align(Alignment.Center).padding(horizontal = 24.dp).enterRise(0, 24f),
                                )
                            } else ManeuverCard(
                                s.maneuver,
                                Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 12.dp).enterRise(0, -16f),
                            )

                        NavState.AwaitingMaps -> StatusCard(
                            title = "Waiting for Google Maps",
                            body = "Pick your destination and press Start in Maps. The turn-by-turn " +
                                "instructions will appear here automatically.",
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                        )

                        NavState.Inactive -> StatusCard(
                            title = "No navigation active",
                            body = "Open Google Maps to pick a destination. RideConnectX relays each turn " +
                                "to your vehicle's cluster.",
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                        )
                    }
                }
            }

            // Bottom sheet — trip readout, hand-off button, relay status.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(c.bg)
                    .padding(top = 16.dp)
            ) {
                val maneuver = (navState as? NavState.Active)?.maneuver

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Readout("ETA", maneuver?.etaMinutes?.let { "$it min" } ?: "—", false, Modifier.weight(1f))
                    Divider()
                    Readout("Distance", maneuver?.remainingDistance?.ifBlank { null } ?: "—", false, Modifier.weight(1f))
                    Divider()
                    // Speed comes from the bike, so it is live even with no route.
                    Readout("Speed", speed?.let { "$it km/h" } ?: "—", true, Modifier.weight(1f))
                }

                if (com.eshwar.rideconnectx.core.util.BackgroundRunRules.showReminder(backgroundRun.granted)) {
                    Spacer(Modifier.height(12.dp))
                    val reminder = stringResource(R.string.bgrun_reminder)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.amber.copy(alpha = 0.10f))
                            .padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(reminder, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.text, modifier = Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = backgroundRun.request) {
                            Text(stringResource(R.string.bgrun_enable))
                        }
                    }
                }

                // Maps-off card: Maps was opened from here but never posted. Never during a route.
                val mapsOff by vm.mapsOffState.collectAsStateWithLifecycle()
                var now by remember { mutableStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) {
                    while (true) { kotlinx.coroutines.delay(30_000); now = System.currentTimeMillis() }
                }
                if (com.eshwar.rideconnectx.data.local.MapsOffRules.shouldShowMapsOffCard(
                        mapsOff, notificationAccess.granted, navState is NavState.Active, now)
                ) {
                    Spacer(Modifier.height(12.dp))
                    val mapsOffText = stringResource(R.string.mapsoff_text)
                    val turnOnDesc = stringResource(R.string.mapsoff_turn_on_desc)
                    val dismissDesc = stringResource(R.string.mapsoff_dismiss_desc)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.amber.copy(alpha = 0.10f))
                            .padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(mapsOffText, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.text, modifier = Modifier.weight(1f))
                        androidx.compose.material3.TextButton(
                            onClick = { vm.mapsOffButtonTapped(); openMapsNotificationSettings(context) },
                            modifier = Modifier.semantics { contentDescription = turnOnDesc },
                        ) { Text(stringResource(R.string.mapsoff_turn_on)) }
                        androidx.compose.material3.TextButton(
                            onClick = vm::dismissMapsOff,
                            modifier = Modifier.semantics { contentDescription = dismissDesc },
                        ) { Text(stringResource(R.string.mapsoff_dismiss)) }
                    }
                }

                Spacer(Modifier.height(16.dp))

                PrimaryButton(
                    label = "Open in Google Maps",
                    onClick = openMaps,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = androidx.compose.material3.LocalContentColor.current,
                            modifier = Modifier.size(15.dp),
                        )
                    },
                )

                Spacer(Modifier.height(12.dp))
                RelayStatus(
                    linked = clusterLinked,
                    vehicleConnected = vehicleConnected,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Maps' notification settings; falls back to its app-info page, then does nothing. */
private fun openMapsNotificationSettings(context: android.content.Context) {
    val maps = "com.google.android.apps.maps"
    val notif = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, maps)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val info = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$maps"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(notif) } catch (_: ActivityNotFoundException) {
        try { context.startActivity(info) } catch (_: ActivityNotFoundException) { }
    }
}

/** The faint grid and route line behind the maneuver card. */
@Composable
private fun RouteBackdrop() {
    val c = Rcx.colors
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(1400, delayMillis = 200, easing = FastOutSlowInEasing)) }
    val pulse by rememberBreath(1400, "navDot")
    val routeMeasure = remember { PathMeasure() }
    Canvas(Modifier.fillMaxSize()) {
        val gridColor = c.blue.copy(alpha = 0.031f)
        val step = 30.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }

        // Route geometry is expressed in the design's 355 × 400 viewBox.
        val sx = size.width / 355f
        val sy = size.height / 400f
        fun p(px: Float, py: Float) = Offset(px * sx, py * sy)

        val road = Path().apply {
            moveTo(p(0f, 200f).x, p(0f, 200f).y)
            quadraticTo(p(80f, 180f).x, p(80f, 180f).y, p(160f, 165f).x, p(160f, 165f).y)
            quadraticTo(p(240f, 148f).x, p(240f, 148f).y, p(355f, 152f).x, p(355f, 152f).y)
        }
        drawPath(road, c.blue.copy(alpha = 0.22f), style = Stroke(width = 14.dp.toPx() * sx))

        val route = Path().apply {
            moveTo(p(90f, 400f).x, p(90f, 400f).y)
            quadraticTo(p(125f, 290f).x, p(125f, 290f).y, p(160f, 165f).x, p(160f, 165f).y)
            quadraticTo(p(198f, 142f).x, p(198f, 142f).y, p(240f, 130f).x, p(240f, 130f).y)
        }
        // The route traces itself from the rider to the destination.
        routeMeasure.setPath(route, false)
        val drawn = Path()
        routeMeasure.getSegment(0f, routeMeasure.length * draw.value, drawn, true)
        drawPath(drawn, c.blue, style = Stroke(width = 4.dp.toPx() * sx))

        // Destination pin and current position.
        drawCircle(c.blue.copy(alpha = 0.10f * draw.value), radius = 22f * sx * draw.value, center = p(240f, 130f))
        drawCircle(c.blue, radius = 10f * sx, center = p(240f, 130f))
        drawCircle(Color.White, radius = 5f * sx, center = p(240f, 130f))
        drawCircle(c.cyan.copy(alpha = 0.18f * (1f - pulse)), radius = (14f + 16f * pulse) * sx, center = p(90f, 360f))
        drawCircle(c.cyan.copy(alpha = 0.9f), radius = 9f * sx, center = p(90f, 360f))
    }
}

/** Shown while Maps is on its arrival instruction: where, and a tick that draws itself. */
@Composable
private fun ArrivalCard(place: String, modifier: Modifier = Modifier) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.bg.copy(alpha = 0.94f))
            .border(1.dp, c.green.copy(alpha = 0.35f), shape)
            .padding(vertical = 22.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(c.green.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            com.eshwar.rideconnectx.presentation.theme.DrawnTick(c.green, Modifier.size(38.dp))
        }
        Text(
            "You have arrived",
            style = RcxType.Label.copy(fontSize = 17.sp),
            color = c.text,
            modifier = Modifier.padding(top = 14.dp),
        )
        if (place.isNotBlank()) Text(
            place,
            style = RcxType.BodySmall.copy(fontSize = 12.sp),
            color = c.muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ManeuverCard(maneuver: NavManeuver, modifier: Modifier = Modifier) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(18.dp)
    // The arrow swings to the new direction when the turn changes.
    val heading by androidx.compose.animation.core.animateFloatAsState(
        arrowDegrees(maneuver.maneuverId), com.eshwar.rideconnectx.presentation.theme.RcxMotion.snappy(), label = "arrowHeading",
    )
    // A bar along the bottom closes as the turn gets nearer: full at the
    // farthest distance seen for this instruction, empty at the turn.
    val metres = metresOf(maneuver.distanceToTurn)
    var farthest by remember(maneuver.instruction) { mutableStateOf(metres) }
    if (metres > farthest) farthest = metres
    val left by androidx.compose.animation.core.animateFloatAsState(
        if (farthest > 0) metres.toFloat() / farthest else 0f,
        androidx.compose.animation.core.tween(400), label = "turnBar",
    )
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.bg.copy(alpha = 0.94f))
            .drawWithContent {
                drawContent()
                val h = 3.dp.toPx()
                drawRect(c.blue, Offset(0f, size.height - h), Size(size.width * left, h))
            }
            .border(1.dp, c.blue.copy(alpha = 0.157f), shape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(c.blue.copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Navigation,
                // N37 row 11: named for TalkBack; the turn itself is spoken by the live instruction below.
                // Spoken as the direction, e.g. "Next turn: left" (TalkBack check, plan b).
                contentDescription = stringResource(R.string.nav_turn_arrow) + ": " + arrowWords(maneuver.maneuverId),
                tint = c.blue,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = heading },
            )
        }
        AnimatedContent(
            targetState = maneuver,
            transitionSpec = {
                if (targetState.instruction != initialState.instruction) {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                } else {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                }
            },
            label = "maneuver",
        ) { maneuver ->
        Column {
            // Unknown turn distance (blank after the hold) shows no "IN" line at all.
            if (maneuver.distanceToTurn.isNotBlank()) Text(
                "IN ${maneuver.distanceToTurn.uppercase()}",
                style = RcxType.MonoTiny.copy(fontSize = 10.sp),
                color = c.muted,
            )
            Text(
                maneuver.instruction,
                style = RcxType.Label.copy(fontSize = 14.sp),
                color = c.text,
                // Maps street names can be long: two lines, then an ellipsis.
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                // A new turn is spoken through its text.
                modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
            )
        }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    /** Optional call to action — label paired with what it does. */
    action: Pair<String, () -> Unit>? = null,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.bg.copy(alpha = 0.92f))
            .border(1.dp, c.border, shape)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.blue.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Navigation,
                contentDescription = null,
                tint = c.blue,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = RcxType.Label.copy(fontSize = 15.sp), color = c.text)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = RcxType.BodySmall.copy(fontSize = 12.sp),
            color = c.muted,
            textAlign = TextAlign.Center,
        )

        action?.let { (label, onClick) ->
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                label = label,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Readout(label: String, value: String, highlight: Boolean, modifier: Modifier = Modifier) {
    val c = Rcx.colors
    // Each readout gets a third of the row and may wrap, so large text does not clip.
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = RcxType.Wordmark.copy(fontSize = 20.sp),
            color = if (highlight) c.cyan else c.text,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(3.dp))
        Text(label, style = RcxType.BodySmall.copy(fontSize = 10.sp), color = c.muted)
    }
}

@Composable
private fun Divider() {
    val c = Rcx.colors
    Box(Modifier.width(1.dp).height(32.dp).background(c.border))
}

@Composable
private fun RelayStatus(
    linked: Boolean,
    vehicleConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(14.dp)

    // Connected-but-not-yet-relaying is its own state. Reporting it as "not
    // connected" was wrong and read as a bug to anyone whose bike was plainly
    // paired.
    val (message, dot) = when {
        linked -> "Sending instructions to vehicle cluster via BLE" to c.green
        vehicleConnected -> "Vehicle connected — waiting for the next instruction" to c.blue
        else -> "Vehicle not connected — navigating on phone only" to c.muted.copy(alpha = 0.4f)
    }

    Row(
        modifier
            .clip(shape)
            .cardSurface(shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            style = RcxType.BodySmall.copy(fontSize = 10.sp),
            color = c.muted,
            modifier = Modifier.weight(1f),
        )
        val dotColor by animateColorAsState(dot, tween(500), label = "relayDot")
        val ping by rememberBreath(1200, "relayPing")
        Box(contentAlignment = Alignment.Center) {
            if (linked) Box(
                Modifier.size(16.dp)
                    .graphicsLayer { scaleX = 0.5f + ping; scaleY = 0.5f + ping; alpha = 1f - ping }
                    .border(1.dp, dotColor, CircleShape)
            )
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        }
    }
}

/** Which way the arrow points for a cluster maneuver code, in degrees from straight ahead. */
private fun arrowDegrees(code: Int): Float {
    val m = com.eshwar.rideconnectx.domain.ProtocolEngine.Maneuver
    return when (code) {
        m.TURN_LEFT, m.LEFT_FLAT, m.ROUNDABOUT_EXIT_LEFT -> -90f
        m.TURN_RIGHT, m.TURN_RIGHT_FLAT, m.ROUNDABOUT_EXIT_RIGHT -> 90f
        m.SLIGHT_LEFT, m.JUNCTION_SLIGHT_LEFT, m.KEEP_LEFT, m.ROUNDABOUT_EXIT_SLIGHT_LEFT -> -45f
        m.SLIGHT_RIGHT, m.KEEP_RIGHT, m.ROUNDABOUT_EXIT_SLIGHT_RIGHT -> 45f
        m.SHARP_LEFT, m.ROUNDABOUT_EXIT_SHARP_LEFT -> -135f
        m.SHARP_RIGHT, m.ROUNDABOUT_EXIT_SHARP_RIGHT -> 135f
        m.U_TURN, m.U_TURN_LOOP -> 180f
        else -> 0f
    }
}

/** The arrow's direction in words, for TalkBack; follows [arrowDegrees]. */
private fun arrowWords(code: Int): String = when (arrowDegrees(code)) {
    -90f -> "left"
    90f -> "right"
    -45f -> "slight left"
    45f -> "slight right"
    -135f -> "sharp left"
    135f -> "sharp right"
    180f -> "U-turn"
    else -> "straight ahead"
}

private val METRES = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m)""", RegexOption.IGNORE_CASE)

/** "200 m" / "1.2 km" to metres; 0 when there is no distance. */
private fun metresOf(text: String): Int {
    val m = METRES.find(text) ?: return 0
    val v = m.groupValues[1].replace(',', '.').toFloatOrNull() ?: return 0
    return (if (m.groupValues[2].equals("km", true)) v * 1000 else v).toInt()
}
