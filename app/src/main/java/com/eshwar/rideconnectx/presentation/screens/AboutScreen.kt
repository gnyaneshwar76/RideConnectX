package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.presentation.theme.sheetEnter
import com.eshwar.rideconnectx.presentation.theme.innerFill
import com.eshwar.rideconnectx.presentation.theme.sheetContainerColor
import com.eshwar.rideconnectx.presentation.theme.GlassSheetWindow
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import com.eshwar.rideconnectx.presentation.theme.cardSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eshwar.rideconnectx.BuildConfig
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.RcxLogo
import com.eshwar.rideconnectx.presentation.components.RcxPhotoFill
import com.eshwar.rideconnectx.presentation.components.SettingsDivider
import com.eshwar.rideconnectx.presentation.components.SettingsGroup
import com.eshwar.rideconnectx.presentation.components.SettingsLinkRow
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType

/**
 * Screen 23 — About.
 *
 * Version and build number come from `BuildConfig`, so they are always the
 * numbers actually installed. Everything on this screen works offline.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
) {
    val c = Rcx.colors
    var sheet by remember { mutableStateOf<AboutSheet?>(null) }

    ScreenBackdrop(photo = R.drawable.img_about_header) {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val headerList = androidx.compose.foundation.lazy.rememberLazyListState()
            BackHeader(title = stringResource(R.string.about_title), onBack = onBack, lifted = headerList.canScrollBackward)

            LazyColumn(
                state = headerList,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item { AppInformationCard() }

                item {
                    // Trimmed to what a rider actually opens this screen for.
                    // The changelog duplicated What's new; open-source licences
                    // are for open-source apps and this one is closed; and the
                    // support section waits for a real support address rather
                    // than showing the developer's personal one.
                    SettingsGroup(stringResource(R.string.about_information), Icons.Filled.Description) {
                        SettingsLinkRow(
                            title = stringResource(R.string.about_whats_new),
                            subtitle = "Version ${BuildConfig.VERSION_NAME}",
                            onClick = { sheet = AboutSheet.WHATS_NEW },
                        )
                        SettingsDivider()
                        SettingsLinkRow(title = stringResource(R.string.common_privacy_policy), onClick = onPrivacy)
                        SettingsDivider()
                        SettingsLinkRow(title = stringResource(R.string.settings_terms), onClick = onTerms)
                    }
                }

                item { AboutFooter() }
            }
        }
    }

    sheet?.let { which ->
        AboutTextSheet(
            title = which.title,
            body = which.body,
            onDismiss = { sheet = null },
        )
    }
}

@Composable
private fun AppInformationCard() {
    val c = Rcx.colors
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardSurface(shape)
    ) {
        // The open road sits *behind* the card rather than above it, so the
        // version details stay the subject. Dimmed towards the card colour it
        // reads as a texture on the surface, and every label below keeps the
        // contrast it had against a flat card.
        RcxPhotoFill(
            res = R.drawable.img_about_header,
            modifier = Modifier.matchParentSize(),
            darken = 0.74f,
            darkenColor = c.card,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RcxLogo(height = 46.dp)
            Spacer(Modifier.height(14.dp))
            Text("RideConnectX", style = RcxType.Wordmark.copy(fontSize = 22.sp), color = c.text)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.about_tagline),
                style = RcxType.BodySmall.copy(fontSize = 13.sp),
                color = c.muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VersionChip(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
                VersionChip(stringResource(R.string.about_build), BuildConfig.VERSION_CODE.toString())
            }
        }
    }
}

@Composable
private fun VersionChip(label: String, value: String) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .clip(shape)
            .innerFill()
            .border(1.dp, c.border, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = RcxType.MonoTiny, color = c.muted)
        Spacer(Modifier.height(4.dp))
        Text(value, style = RcxType.Mono.copy(fontSize = 13.sp), color = c.cyan)
    }
}

@Composable
private fun AboutFooter() {
    val c = Rcx.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "© 2026 RideConnectX",
            style = RcxType.BodySmall.copy(fontSize = 12.sp),
            color = c.muted,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Built with ❤️ for riders.",
            style = RcxType.BodySmall.copy(fontSize = 12.sp),
            color = c.muted.copy(alpha = 0.7f),
        )
    }
}

/** What's new — new features and what was fixed, which is all a rider needs. */
private enum class AboutSheet(val title: String, val body: String) {
    WHATS_NEW(
        "What's new in ${BuildConfig.VERSION_NAME}",
        """
        NEW
        • Turn-by-turn directions relayed to the instrument cluster.
        • Live odometer, Trip A, Trip B and fuel from the vehicle.
        • Phone battery, signal and clock shown on the cluster.
        • Service tracking with reminders and a maintenance history.
        • Safety screen with SOS and emergency contacts.
        • Light and dark themes, eight accent colours, adjustable text size.

        FIXED
        • Google sign-in no longer says "no accounts found" on the first tap.
        • A mistyped service reading can now be undone by deleting it.
        • The units setting is honoured everywhere, not just stored.
        • Pairing tells you when a scan found nothing, and offers to retry.
        """.trimIndent(),
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutTextSheet(title: String, body: String, onDismiss: () -> Unit) {
    val c = Rcx.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetContainerColor(),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.sheetEnter()) {
        GlassSheetWindow()
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
            Text(title, style = RcxType.Section.copy(fontSize = 17.sp), color = c.text)
            Spacer(Modifier.height(14.dp))
            Text(
                body,
                style = RcxType.BodySmall.copy(fontSize = 13.sp, lineHeight = 21.sp),
                color = c.muted,
            )
        }
    }
    }
}
