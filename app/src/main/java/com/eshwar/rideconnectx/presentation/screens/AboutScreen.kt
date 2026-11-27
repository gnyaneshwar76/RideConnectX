package com.eshwar.rideconnectx.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.BuildConfig
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.RcxLogo
import com.eshwar.rideconnectx.presentation.components.RcxPhotoFill
import com.eshwar.rideconnectx.presentation.components.SettingsDivider
import com.eshwar.rideconnectx.presentation.components.SettingsGroup
import com.eshwar.rideconnectx.presentation.components.SettingsLinkRow
import com.eshwar.rideconnectx.presentation.components.contentWidth
import com.eshwar.rideconnectx.presentation.components.safeDrawingCentred
import com.eshwar.rideconnectx.presentation.theme.GlassSheetWindow
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import com.eshwar.rideconnectx.presentation.theme.cardSurface
import com.eshwar.rideconnectx.presentation.theme.innerFill
import com.eshwar.rideconnectx.presentation.theme.sheetContainerColor
import com.eshwar.rideconnectx.presentation.theme.sheetEnter
import kotlinx.coroutines.delay

private enum class HelpSheet { WHATS_NEW, LICENCES }

/**
 * Settings > Help and about. Report a problem, What's new, Licences, and the
 * version card. "Connection details" (developer text) appears only after the
 * version card is tapped 5 times.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    var sheet by rememberSaveable { mutableStateOf<HelpSheet?>(null) }
    var taps by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current
    val devOn = taps >= 5

    ScreenBackdrop(photo = R.drawable.img_about_header) {
        Column(
            Modifier
                .fillMaxHeight()
                .safeDrawingCentred()
                .contentWidth()
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val headerList = rememberLazyListState()
            BackHeader(title = stringResource(R.string.settings_help), onBack = onBack, lifted = headerList.canScrollBackward)

            LazyColumn(
                state = headerList,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    AppInformationCard(onVersionTap = {
                        if (taps < 5 && ++taps == 5) Toast.makeText(context, R.string.help_dev_on, Toast.LENGTH_SHORT).show()
                    })
                }
                item {
                    SettingsGroup(stringResource(R.string.about_information), Icons.Filled.Description) {
                        val reportDesc = stringResource(R.string.about_report_desc)
                        SettingsLinkRow(
                            title = stringResource(R.string.about_report),
                            subtitle = reportDesc,
                            onClick = { com.eshwar.rideconnectx.core.util.openReportMail(context) },
                        )
                        SettingsDivider()
                        SettingsLinkRow(
                            title = stringResource(R.string.about_whats_new),
                            subtitle = stringResource(R.string.help_whats_new_sub, BuildConfig.VERSION_NAME),
                            onClick = { sheet = HelpSheet.WHATS_NEW },
                        )
                        SettingsDivider()
                        SettingsLinkRow(
                            title = stringResource(R.string.help_licences),
                            subtitle = stringResource(R.string.help_licences_sub),
                            onClick = { sheet = HelpSheet.LICENCES },
                        )
                        if (devOn) {
                            SettingsDivider()
                            ConnectionDetails()
                        }
                    }
                }
                item { AboutFooter() }
            }
        }
    }

    sheet?.let { which ->
        val title = when (which) {
            HelpSheet.WHATS_NEW -> stringResource(R.string.help_whats_new_title, BuildConfig.VERSION_NAME)
            HelpSheet.LICENCES -> stringResource(R.string.help_licences)
        }
        val body = stringResource(if (which == HelpSheet.WHATS_NEW) R.string.help_whats_new_body else R.string.help_licences_body)
        AboutTextSheet(title = title, body = body, onDismiss = { sheet = null })
    }
}

@Composable
private fun AppInformationCard(onVersionTap: () -> Unit) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardSurface(shape)
    ) {
        RcxPhotoFill(
            res = R.drawable.img_about_header,
            modifier = Modifier.matchParentSize(),
            darken = 0.88f,
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
                color = c.text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            // Tapping the version line 5 times reveals Connection details.
            Row(
                Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onVersionTap),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VersionChip(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
                VersionChip(stringResource(R.string.about_build), BuildConfig.VERSION_CODE.toString())
            }
        }
    }
}

/** Plain text lines from [com.eshwar.rideconnectx.data.ble.LinkDiagnostics]; no names, addresses or text. */
@Composable
private fun ConnectionDetails() {
    val d by com.eshwar.rideconnectx.data.ble.LinkDiagnostics.state.collectAsStateWithLifecycle()
    // Ticks once a second so "Time since connect" is live.
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(1000); value = System.currentTimeMillis() } }
    val c = Rcx.colors
    val unknown = "-"
    fun hex(v: Int?) = v?.let { "0x%02X".format(it) } ?: unknown
    val lines = if (d.connectedAtMs == 0L) {
        listOf(stringResource(R.string.conn_none))
    } else {
        listOf(
            stringResource(R.string.conn_vehicle, d.profileCode.ifBlank { d.family?.name ?: unknown }),
            stringResource(
                R.string.conn_gatt,
                when (d.gattConnected) { true -> "connected"; false -> "disconnected"; null -> "connecting" },
                d.lastGattStatus?.toString() ?: unknown,
                d.lastDisconnectStatus?.toString() ?: unknown,
            ),
            stringResource(R.string.conn_mtu, d.mtu?.toString() ?: unknown),
            stringResource(R.string.conn_services, d.services.joinToString(" ").ifEmpty { unknown }),
            stringResource(
                R.string.conn_service_fefb,
                when (d.serviceFound) { true -> "found"; false -> "NOT found"; null -> "not checked yet" },
            ),
            stringResource(R.string.conn_cccd, d.cccdOk, d.cccdFailed, d.lastCccdFailStatus?.toString() ?: unknown),
            stringResource(
                R.string.conn_bond, d.bond,
                when (d.createBondResult) { true -> "accepted"; false -> "refused"; null -> "not called" },
            ),
            stringResource(
                R.string.conn_sent,
                d.sent.entries.sortedBy { it.key }.joinToString(", ") {
                    val label = when (it.key) { 0x33 -> "heartbeat"; 0x36 -> "profile"; else -> "type " + hex(it.key) }
                    "$label x${it.value}"
                }.ifEmpty { "nothing" },
                when (d.plainSumSent) { true -> "plain sum"; false -> "complement"; null -> "no rule yet" },
            ),
            stringResource(R.string.conn_received, d.rxCount, hex(d.lastRxType)),
            stringResource(R.string.conn_age, ((now - d.connectedAtMs) / 1000).coerceAtLeast(0).toInt()),
        )
    }
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.conn_title), style = RcxType.BodySmall.copy(fontSize = 14.sp), color = c.text)
        lines.forEach { Text(it, style = RcxType.BodySmall.copy(fontSize = 13.sp), color = c.muted) }
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
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.help_copyright), style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.muted)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.about_built_with), style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.muted)
    }
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
        Column(Modifier.sheetEnter()) {
            GlassSheetWindow()
            // Scrolls: the licence list is taller than a landscape screen.
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 40.dp).verticalScroll(rememberScrollState())) {
                Text(title, style = RcxType.Section.copy(fontSize = 17.sp), color = c.text)
                Spacer(Modifier.height(14.dp))
                Text(body, style = RcxType.BodySmall.copy(fontSize = 13.sp, lineHeight = 21.sp), color = c.text.copy(alpha = 0.78f))
            }
        }
    }
}
