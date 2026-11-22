package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.presentation.components.contentWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.components.LogoMark
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.components.WelcomeHero
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType

/**
 * The only first-open page: welcome and one Start button, then Home. The three
 * tour pages, the sign-in wall and the Permissions page are gone; permissions
 * are asked at the moment they are needed (22 Nov 2026).
 */
@Composable
fun IntroScreen(onStart: () -> Unit, onSignIn: () -> Unit) {
    val c = Rcx.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (c.isDark) listOf(Color(0xFF060C18), Color(0xFF0A1428))
                    else listOf(Color(0xFFD8E8FF), Color(0xFFEEF2FF))
                )
            )
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val compact = maxHeight < 680.dp
            Column(
                Modifier
                    .fillMaxHeight()
                    .contentWidth()
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                // LogoMark already draws the wordmark beside the glyph.
                LogoMark(size = 34.dp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

                WelcomeHero(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(26.dp)),
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = if (compact) 16.dp else 28.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
                ) {
                    Text(
                        stringResource(R.string.intro_welcome_to),
                        style = RcxType.Body.copy(fontSize = 14.sp),
                        color = c.muted,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("RideConnect", style = RcxType.Wordmark.copy(fontSize = if (compact) 24.sp else 27.sp), color = c.text)
                        Text("X", style = RcxType.Wordmark.copy(fontSize = if (compact) 24.sp else 27.sp), color = c.blue)
                    }
                    Text(
                        stringResource(R.string.intro_tagline),
                        style = RcxType.Body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        color = c.muted,
                    )
                    PrimaryButton(
                        label = stringResource(R.string.intro_start),
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth(),
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = androidx.compose.material3.LocalContentColor.current,
                            )
                        },
                    )
                    TextButton(
                        onClick = onSignIn,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(
                            stringResource(R.string.intro_sign_in),
                            style = RcxType.Body.copy(fontSize = 14.sp),
                            color = c.blue,
                        )
                    }
                }
            }
        }
    }
}
