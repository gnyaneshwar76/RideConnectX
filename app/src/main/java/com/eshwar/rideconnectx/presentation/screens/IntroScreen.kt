package com.eshwar.rideconnectx.presentation.screens

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.components.DotIndicator
import com.eshwar.rideconnectx.presentation.components.LogoMark
import com.eshwar.rideconnectx.presentation.components.ObIllustration
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.components.WelcomeHero
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import kotlinx.coroutines.launch

/**
 * Screens 02–05 — Welcome and the three onboarding pages, as one swipeable flow.
 *
 * These used to be four separate destinations, advanced only by tapping the
 * button. Riders swipe: an intro carousel that ignores a horizontal drag reads
 * as broken long before anyone thinks to look for a button. Making them one
 * pager also removes four entries from the back stack, which is what the
 * blank-screen bug on Back was living in.
 *
 * Skip sits beside the primary button rather than in the top-right corner —
 * where it was small, far from the thumb, and easy to hit by accident reaching
 * for the notification shade — and disappears on the last page, where there is
 * nothing left to skip.
 */
private const val PAGE_COUNT = 4

@Composable
fun IntroScreen(
    onFinished: () -> Unit,
    onSkip: () -> Unit,
) {
    val c = Rcx.colors
    val pager = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    val page = pager.currentPage
    val isLast = page == PAGE_COUNT - 1

    fun goTo(target: Int) = scope.launch { pager.animateScrollToPage(target) }

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
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val compact = maxHeight < 680.dp
            val bottomPad = if (compact) 16.dp else 28.dp

            Column(
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                // LogoMark already draws the wordmark beside the glyph.
                LogoMark(
                    size = 34.dp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )

                // The artwork takes everything left over, edge to edge. It used
                // to sit inside 16dp of side padding on a fixed-height box,
                // which is what made the pages read as boxy.
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    pageSpacing = 0.dp,
                ) { index ->
                    // Parallax: the picture travels slower than its page, so
                    // the swipe has depth. The gap this opens is always on the
                    // side of the page that is off screen.
                    val art = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .graphicsLayer {
                            val offset = (index - pager.currentPage) - pager.currentPageOffsetFraction
                            translationX = -offset * size.width * 0.3f
                        }

                    when (index) {
                        0 -> WelcomeHero(modifier = art)
                        else -> ObIllustration(
                            type = OnboardingStep.entries[index - 1].art,
                            modifier = art,
                        )
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = bottomPad),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
                ) {
                    DotIndicator(
                        count = PAGE_COUNT,
                        active = page,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )

                    Column(Modifier.height(if (compact) 82.dp else 92.dp)) {
                        if (page == 0) {
                            Text(
                                stringResource(R.string.intro_welcome_to),
                                style = RcxType.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                                color = c.muted,
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "RideConnect",
                                    style = RcxType.Wordmark.copy(fontSize = if (compact) 24.sp else 27.sp),
                                    color = c.text,
                                )
                                Text(
                                    "X",
                                    style = RcxType.Wordmark.copy(fontSize = if (compact) 24.sp else 27.sp),
                                    color = c.blue,
                                )
                            }
                            Text(
                                stringResource(R.string.intro_tagline),
                                style = RcxType.Body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                                color = c.muted,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        } else {
                            val step = OnboardingStep.entries[page - 1]
                            Text(
                                step.title,
                                style = RcxType.Wordmark.copy(fontSize = if (compact) 18.sp else 20.sp),
                                color = c.text,
                            )
                            Text(
                                step.desc,
                                style = RcxType.Body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                                color = c.muted,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Back only exists once there is something behind.
                        AnimatedVisibility(
                            visible = page > 0,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally(),
                        ) {
                            Box(
                                Modifier
                                    .width(48.dp)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(c.blue.copy(alpha = 0.035f))
                                    .border(1.5.dp, c.blue.copy(alpha = 0.157f), RoundedCornerShape(16.dp))
                                    .clickable { goTo(page - 1) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = stringResource(R.string.common_back),
                                    modifier = Modifier.size(20.dp),
                                    tint = c.muted,
                                )
                            }
                        }

                        // Beside the primary action, and gone on the last page.
                        AnimatedVisibility(
                            visible = !isLast,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally(),
                        ) {
                            Box(
                                Modifier
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(onClick = onSkip)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(R.string.common_skip),
                                    style = RcxType.Label.copy(fontSize = 14.sp),
                                    color = c.muted,
                                )
                            }
                        }

                        PrimaryButton(
                            label = when (page) {
                                0 -> stringResource(R.string.intro_get_started)
                                PAGE_COUNT - 1 -> stringResource(R.string.intro_lets_go)
                                else -> stringResource(R.string.common_next)
                            },
                            onClick = { if (isLast) onFinished() else goTo(page + 1) },
                            modifier = Modifier.weight(1f),
                            icon = {
                                Icon(
                                    if (isLast) Icons.AutoMirrored.Filled.ArrowForward
                                    else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp),
                                    tint = androidx.compose.material3.LocalContentColor.current,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
