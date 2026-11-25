package com.eshwar.rideconnectx.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.content.res.Configuration
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.navigation.Routes
import com.eshwar.rideconnectx.presentation.theme.GlassTier
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxMotion
import com.eshwar.rideconnectx.presentation.theme.glassSurface
import com.eshwar.rideconnectx.presentation.theme.pressSquash
import com.eshwar.rideconnectx.presentation.theme.topEdgeLight
import kotlinx.coroutines.launch
import kotlin.math.abs

private val DockHeight = 64.dp
private val DockMargin = 12.dp

/** Landscape is short: the dock is slimmer there and shows icons only. */
private val DockHeightLandscape = 44.dp
private val DockMarginLandscape = 4.dp

/** Space the five top-level screens keep free at the bottom so the dock never hides content. */
val DockSpace = DockHeight + DockMargin

fun Modifier.dockSpace(): Modifier = composed {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    padding(bottom = if (landscape) DockHeightLandscape + DockMarginLandscape else DockSpace)
}

/**
 * The width cap every screen's content column uses: 600 dp in portrait, 840 dp in
 * landscape so the side margins are not a fifth of the screen each (walk A16 #5).
 * Apply it before fillMaxWidth(), as widthIn(max) was.
 */
fun Modifier.contentWidth(): Modifier = composed {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    widthIn(max = if (landscape) 840.dp else 600.dp)
}

/**
 * safeDrawing insets, but the display cutout is made equal on both sides (the larger one), so a
 * one-sided cutout does not push the content off-centre. System bars (a landscape 3-button nav
 * bar sits on one side) keep their real size, so the content is centred in the visible area.
 */
fun Modifier.safeDrawingCentred(): Modifier = composed {
    val d = androidx.compose.ui.platform.LocalLayoutDirection.current
    val w = androidx.compose.foundation.layout.WindowInsets.Companion
    val p = w.safeDrawing.asPaddingValues()
    val cut = w.displayCutout.asPaddingValues()
    val bars = w.systemBars.asPaddingValues()
    val c = maxOf(cut.calculateLeftPadding(d), cut.calculateRightPadding(d))
    consumeWindowInsets(w.safeDrawing).padding(
        start = maxOf(c, bars.calculateLeftPadding(d)),
        end = maxOf(c, bars.calculateRightPadding(d)),
        top = p.calculateTopPadding(), bottom = p.calculateBottomPadding(),
    )
}

private class DockTab(val route: String, val icon: ImageVector, val label: Int, val desc: Int)

private val Tabs = listOf(
    DockTab(Routes.DASHBOARD, Icons.Filled.Home, R.string.dock_home, R.string.dock_home_desc),
    DockTab(Routes.BLE, Icons.Filled.TwoWheeler, R.string.dock_bike, R.string.dock_bike_desc),
    DockTab(Routes.NAVIGATION, Icons.Filled.Navigation, R.string.dock_navigate, R.string.dock_navigate_desc),
    DockTab(Routes.STATS, Icons.Filled.QueryStats, R.string.dock_rides, R.string.dock_rides_desc),
    DockTab(Routes.SETTINGS, Icons.Filled.Settings, R.string.dock_settings, R.string.dock_settings_desc),
)

/** The routes the dock shows on; everywhere else it is hidden. */
val DockRoutes: Set<String> = Tabs.map { it.route }.toSet()

/** Which of [count] equal tabs lies under [x] in a bar [width] wide. Pure, so it is unit-tested. */
fun dockTabAt(x: Float, width: Float, count: Int): Int =
    if (width <= 0f || count <= 0) 0 else (x / (width / count)).toInt().coerceIn(0, count - 1)

/**
 * Floating glass dock. The glass bubble can be dragged: the page stays put while
 * the bubble follows the finger (stretching a little), and only on release does it
 * spring to the tab under it and [onSelect] fire, once. A tap on a tab does the same.
 */
@Composable
fun Dock(selectedRoute: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Rcx.colors
    val scope = rememberCoroutineScope()
    val selected = Tabs.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)
    val n = Tabs.size
    val spring = RcxMotion.snappy<Float>()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val dockHeight = if (landscape) DockHeightLandscape else DockHeight

    CappedFontScale(1.15f) {
    Box(modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = if (landscape) DockMarginLandscape else DockMargin)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(dockHeight)
                .glassSurface(RoundedCornerShape(dockHeight / 2), tier = GlassTier.HEAVY)
                .topEdgeLight()
        ) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val tabW = widthPx / n
            fun centre(i: Int) = tabW * (i + 0.5f)
            val bubble = remember { Animatable(centre(selected)) }
            var dragging by remember { mutableStateOf(false) }
            var dir by remember { mutableIntStateOf(1) }
            var stretchTarget by remember { mutableFloatStateOf(0f) }
            val stretch by animateFloatAsState(stretchTarget, RcxMotion.interactive(), label = "dockStretch")
            val under by remember(widthPx) { derivedStateOf { dockTabAt(bubble.value, widthPx, n) } }
            val lit = if (dragging) under else selected

            // Back, or a screen opened from elsewhere, moves the bubble to the new tab.
            LaunchedEffect(selected, widthPx) {
                if (!dragging) bubble.animateTo(centre(selected), spring)
            }

            // Spring to tab [i] first, then open it: navigation fires once, on release.
            fun commit(i: Int) = scope.launch {
                dragging = false
                stretchTarget = 0f
                bubble.animateTo(centre(i), spring)
                if (i != selected) onSelect(Tabs[i].route)
            }

            Box(
                Modifier.fillMaxSize().pointerInput(widthPx, selected) {
                    detectHorizontalDragGestures(
                        onDragStart = { o ->
                            dragging = true
                            scope.launch { bubble.snapTo(o.x.coerceIn(tabW / 2, widthPx - tabW / 2)) }
                        },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            if (dx != 0f) dir = if (dx > 0) 1 else -1
                            stretchTarget = (abs(dx) * 0.04f).coerceAtMost(0.3f)
                            scope.launch { bubble.snapTo((bubble.value + dx).coerceIn(tabW / 2, widthPx - tabW / 2)) }
                        },
                        onDragEnd = { commit(dockTabAt(bubble.value, widthPx, n)) },
                        onDragCancel = { commit(selected) },
                    )
                }
            ) {
                // Only translation and scale move per frame: no shader, no blur.
                val bubbleShape = RoundedCornerShape(percent = 50)
                Box(
                    Modifier
                        .width(with(LocalDensity.current) { tabW.toDp() })
                        .fillMaxHeight()
                        .padding(6.dp)
                        .graphicsLayer {
                            translationX = bubble.value - tabW / 2
                            scaleX = 1f + stretch
                            transformOrigin = TransformOrigin(if (dir > 0) 0f else 1f, 0.5f)
                        }
                        .background(c.blue.copy(alpha = 0.2f), bubbleShape)
                        .border(1.dp, c.blue.copy(alpha = 0.45f), bubbleShape)
                )
                Row(Modifier.fillMaxSize()) {
                    Tabs.forEachIndexed { i, t ->
                        val on = i == lit
                        val tint by animateColorAsState(if (on) c.blue else c.muted, label = "dockTint")
                        val desc = stringResource(t.desc)
                        val big = t.route == Routes.NAVIGATION
                        val tabSrc = remember { MutableInteractionSource() }
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pressSquash(tabSrc)
                                .selectable(
                                    selected = i == selected,
                                    interactionSource = tabSrc,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = { commit(i) },
                                )
                                .semantics { contentDescription = desc },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(t.icon, contentDescription = null, tint = tint, modifier = Modifier.size(if (big) 30.dp else 24.dp))
                            if (!landscape) Text(
                                stringResource(t.label),
                                color = if (on) c.text else tint,
                                fontSize = if (big) 12.sp else 11.sp,
                                fontWeight = if (big || on) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
    }
}
