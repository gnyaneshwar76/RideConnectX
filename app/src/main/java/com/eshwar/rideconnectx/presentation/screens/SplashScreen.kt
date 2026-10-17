package com.eshwar.rideconnectx.presentation.screens

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eshwar.rideconnectx.BuildConfig
import com.eshwar.rideconnectx.presentation.components.LogoIcon
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import kotlinx.coroutines.delay

/**
 * Screen 01 — Splash.
 *
 * Non-interactive. Plays the brand animation, then hands off to Welcome.
 * Timing matches the design exactly: 150 / 750 / 1700 / 2300 ms.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val c = Rcx.colors
    var phase by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(150); phase = 1
        delay(600); phase = 2
        delay(950); phase = 3
        delay(600); onFinished()
    }

    val screenAlpha by animateFloatAsState(
        targetValue = if (phase >= 3) 0f else 1f,
        animationSpec = tween(450),
        label = "screenAlpha",
    )
    val logoScale by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0.45f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "logoScale",
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(400),
        label = "logoAlpha",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (phase >= 1) 0.55f else 0f,
        animationSpec = tween(1600),
        label = "glowAlpha",
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(520),
        label = "textAlpha",
    )

    val sweep by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(900, delayMillis = 200),
        label = "wordmarkSweep",
    )

    // Radial backdrop: ellipse centred at 50% / 52%, brand navy fading to bg.
    val inner = if (c.isDark) Color(0xFF0C1D3E) else Color(0xFFC5D9FF)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(screenAlpha)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(inner, c.bg),
                        center = Offset(size.width * 0.5f, size.height * 0.52f),
                        radius = size.height * 0.7f,
                    )
                )
                // Soft blue bloom behind the logo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(c.blue.copy(alpha = 0.22f * glowAlpha), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                        radius = size.width * 0.42f,
                    ),
                    radius = size.width * 0.42f,
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LogoIcon(
                size = 96.dp,
                modifier = Modifier
                    .scale(logoScale)
                    .alpha(logoAlpha),
            )

            Box(Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .alpha(textAlpha)
                    // A band of light crosses the letters once. Offscreen so the
                    // band only paints where there is text (SrcAtop).
                    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val band = size.width * 0.3f
                        val x = -band + (size.width + 2 * band) * sweep
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.8f), Color.Transparent),
                                startX = x - band, endX = x + band,
                            ),
                            blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop,
                        )
                    },
            ) {
                Text("RideConnect", style = RcxType.Wordmark, color = c.text)
                Text(
                    "X",
                    style = RcxType.Wordmark.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
                    ),
                    color = c.blue,
                )
            }

            Box(Modifier.height(8.dp))

            Text(
                text = "SMART NAVIGATION",
                style = RcxType.MonoTiny.copy(fontSize = 10.sp, letterSpacing = 2.5.sp),
                color = c.muted.copy(alpha = 0.44f),
                modifier = Modifier.alpha(textAlpha),
            )
        }

        Text(
            // Was hardcoded "v2.5.0" while the build was 1.0, so the splash and
            // the About screen disagreed about which app this is.
            text = "v${BuildConfig.VERSION_NAME}",
            style = RcxType.Mono.copy(fontSize = 10.sp),
            color = c.muted.copy(alpha = 0.22f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Sits above the real gesture bar on any device.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 32.dp)
                .alpha(textAlpha),
        )
    }
}
