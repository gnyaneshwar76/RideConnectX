package com.eshwar.rideconnectx.presentation.components

import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import com.eshwar.rideconnectx.presentation.theme.popIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import com.eshwar.rideconnectx.presentation.theme.isGlass
import com.eshwar.rideconnectx.presentation.theme.glassButton
import com.eshwar.rideconnectx.presentation.theme.mainButtonFill
import com.eshwar.rideconnectx.presentation.theme.mainButtonText
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.eshwar.rideconnectx.presentation.theme.pressScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType

/**
 * Shared building blocks, translated 1:1 from the Figma design
 * (`App.tsx` → StatusBar, GestureBar, PrimaryBtn, LogoIcon, LogoMark).
 *
 * Alpha suffixes in the design (e.g. `${C.blue}28`) are hex bytes;
 * they are converted here to Compose alpha fractions.
 */

/*
 * Note: the design also draws a mock status bar ("9:41" + signal/wifi/battery)
 * and an iOS home-indicator pill. Those exist only so the Figma frame looks like
 * a phone — Android draws both for real, so they are deliberately not ported.
 * Screens inset their content with WindowInsets.safeDrawing instead.
 */

/**
 * Full-width CTA.
 * Primary  → blue gradient fill with glow shadow.
 * Secondary → translucent blue with blue border.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    secondary: Boolean = false,
    enabled: Boolean = true,
    /** The action is running: a spinner replaces the icon and taps are ignored. */
    busy: Boolean = false,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val base = modifier
        .pressScale(pressed && enabled, 0.97f)
        .height(56.dp)
        // Dimmed so they do not look tappable; 0.6 (was 0.4) keeps the label readable in dark theme.
        .alpha(if (enabled) 1f else 0.6f)
        .clip(shape)

    // Main buttons follow the rider's colour mix and button style; secondary ones stay a faint accent.
    val styled = if (!secondary) {
        base.mainButtonFill(shape, pressed)
    } else if (isGlass) {
        base.glassButton(shape, c.blue, strong = false, pressed = pressed)
    } else {
        base
            .background(c.blue.copy(alpha = 0.051f))
            .border(1.5.dp, c.blue.copy(alpha = 0.267f), shape)
    }
    val onFill = if (secondary) c.blue else mainButtonText

    Box(
        modifier = styled
            .then(if (enabled && !busy) Modifier.clickable(interaction, LocalIndication.current, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = RcxType.Button.copy(fontWeight = FontWeight.SemiBold),
                color = onFill,
            )
            if (busy) androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(18.dp).popIn(),
                strokeWidth = 2.dp,
                color = onFill,
            ) else androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides onFill,
            ) { icon?.invoke() }
        }
    }
}

/** Progress dots — the active dot is a wide pill, the rest are small. */
@Composable
fun DotIndicator(
    count: Int,
    active: Int,
    modifier: Modifier = Modifier,
) {
    val c = Rcx.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val on = i == active
            // The active pill stretches and the colour follows, so moving a
            // page reads as the pill travelling rather than two dots swapping.
            val w by animateDpAsState(if (on) 22.dp else 7.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "dotW")
            val col by animateColorAsState(if (on) c.blue else c.blue.copy(alpha = 0.157f), label = "dotC")
            Box(
                Modifier
                    .width(w)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(col)
            )
        }
    }
}

/** Aspect ratio of the RCX wordmark artwork (783 × 285 in the source). */
private const val WORDMARK_RATIO = 783f / 285f

/**
 * The brand mark on its own.
 *
 * Renders [R.drawable.ic_rcx_wordmark] — the traced vector of the approved
 * logo — so it stays crisp at any size and keeps its mint-to-blue gradient.
 *
 * @param height how tall the mark should be; width follows the artwork's ratio.
 */
@Composable
fun RcxLogo(
    height: androidx.compose.ui.unit.Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.ic_rcx_wordmark),
        contentDescription = "RideConnectX",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(height)
            .width(height * WORDMARK_RATIO),
    )
}

/** Hero placement of the brand mark, used on the splash screen. */
@Composable
fun LogoIcon(
    size: androidx.compose.ui.unit.Dp = 96.dp,
    modifier: Modifier = Modifier,
) {
    // `size` is the design's square tile edge; the wordmark is wide, so it is
    // rendered at a matching optical weight rather than a literal square.
    RcxLogo(height = size * 0.62f, modifier = modifier)
}

/** Brand mark plus the "RideConnectX" name, used in screen headers. */
@Composable
fun LogoMark(
    size: androidx.compose.ui.unit.Dp = 34.dp,
    modifier: Modifier = Modifier,
) {
    val c = Rcx.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RcxLogo(height = size * 0.60f)
        Text(
            text = "RideConnectX",
            style = RcxType.Wordmark.copy(
                fontSize = (size.value * 0.37f).sp,
                fontWeight = FontWeight.Bold,
            ),
            color = c.text,
        )
    }
}

/**
 * Back arrow plus screen title, used on every pushed screen.
 *
 * @param right optional trailing slot, e.g. Notifications' "Mark all read".
 */
@Composable
fun BackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    right: (@Composable () -> Unit)? = null,
    /** The list below has scrolled: the title tucks in and a hairline separates it. */
    lifted: Boolean = false,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(12.dp)
    val lift by androidx.compose.animation.core.animateFloatAsState(
        if (lifted) 1f else 0f, com.eshwar.rideconnectx.presentation.theme.RcxMotion.snappy(), label = "headerLift",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (lift > 0f) drawLine(
                    c.text.copy(alpha = 0.12f * lift),
                    androidx.compose.ui.geometry.Offset(0f, size.height),
                    androidx.compose.ui.geometry.Offset(size.width, size.height),
                    1.dp.toPx(),
                )
            }
            .padding(horizontal = 16.dp, vertical = 12.dp - 3.dp * lift),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .then(
                    if (isGlass) Modifier.glassButton(shape, c.blue, strong = false)
                    else Modifier.clip(shape)
                        .background(c.blue.copy(alpha = 0.07f))
                        .border(1.dp, c.blue.copy(alpha = 0.133f), shape)
                )
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Back",
                modifier = Modifier.size(18.dp),
                tint = c.blue,
            )
        }
        Text(
            text = title,
            style = RcxType.Wordmark.copy(fontSize = 16.sp),
            color = c.text,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).graphicsLayer {
                val s = 1f - 0.1f * lift
                scaleX = s
                scaleY = s
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            },
        )
        right?.invoke()
    }
}

/**
 * The rider's name as it goes to the cluster, updating while they type. The
 * profile packet carries 20 characters, so anything longer is cut here too.
 */
@Composable
fun ClusterGreetingPreview(name: String, modifier: Modifier = Modifier) {
    val shown = name.trim().take(20)
    val shape = RoundedCornerShape(12.dp)
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF0A0F14))
            .border(1.dp, Color(0xFF1E2A36), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("ON YOUR SCOOTER'S DISPLAY", style = RcxType.MonoTiny.copy(fontSize = 8.sp), color = Color(0xFF5B6B7A))
        Text(
            if (shown.isEmpty()) "WELCOME" else "WELCOME $shown",
            style = RcxType.Mono.copy(fontSize = 15.sp),
            color = Color(0xFF7CFFCB),
            maxLines = 1,
        )
    }
}

/**
 * Pull down to refresh, with a scooter that edges out as you pull and rides
 * across while [onRefresh] runs. Held for a moment even when the work is
 * instant, so the ride is seen.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RideRefreshBox(
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val c = Rcx.colors
    var refreshing by remember { androidx.compose.runtime.mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pull = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                val started = System.currentTimeMillis()
                runCatching { onRefresh() }
                kotlinx.coroutines.delay((RIDE_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0))
                refreshing = false
            }
        },
        modifier = modifier,
        state = pull,
        indicator = {
            val ride = remember { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(refreshing) {
                if (refreshing) {
                    ride.snapTo(0f)
                    ride.animateTo(1f, androidx.compose.animation.core.tween(RIDE_MS.toInt(), easing = androidx.compose.animation.core.LinearEasing))
                }
            }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(34.dp)) {
                val track = maxWidth
                Icon(
                    Icons.Filled.TwoWheeler, contentDescription = null, tint = c.blue,
                    modifier = Modifier.size(26.dp).graphicsLayer {
                        val pulled = pull.distanceFraction.coerceIn(0f, 1f)
                        val x = if (refreshing) 0.2f + 0.95f * ride.value else 0.2f * pulled
                        translationX = x * track.toPx() - 26.dp.toPx()
                        alpha = if (refreshing) 1f else pulled
                    },
                )
            }
        },
        content = content,
    )
}

private const val RIDE_MS = 900L
