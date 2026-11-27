package com.eshwar.rideconnectx.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.data.nav.AppTier
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.BottomActions
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.components.SegmentedRow
import com.eshwar.rideconnectx.presentation.components.SettingsGroup
import com.eshwar.rideconnectx.presentation.components.SettingsToggleRow
import com.eshwar.rideconnectx.presentation.components.contentWidth
import com.eshwar.rideconnectx.presentation.components.safeDrawingCentred
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import com.eshwar.rideconnectx.presentation.viewmodel.SettingsViewModel

/**
 * Settings > Alerts on your scooter: master switch, Important/Medium, message text,
 * connection alerts, the Alert apps list, and a pinned "Send a test alert".
 */
@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val s by vm.settings.collectAsStateWithLifecycle()
    val notifApps by vm.notificationApps.collectAsStateWithLifecycle()

    val master by vm.clusterAlerts.collectAsStateWithLifecycle()
    val importantOn by vm.importantOn.collectAsStateWithLifecycle()
    val mediumOn by vm.mediumOn.collectAsStateWithLifecycle()
    val nameOnly by vm.nameOnly.collectAsStateWithLifecycle()
    val testResult by vm.testResult.collectAsStateWithLifecycle()

    ScreenBackdrop(photo = R.drawable.img_permissions_hero, accent = c.blue) {
        Column(
            Modifier
                .fillMaxHeight()
                .safeDrawingCentred()
                .contentWidth()
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val headerList = rememberLazyListState()
            BackHeader(title = stringResource(R.string.settings_alerts), onBack = onBack, lifted = headerList.canScrollBackward)

            LazyColumn(
                state = headerList,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    SettingsGroup(stringResource(R.string.alerts_group_main), Icons.Filled.Notifications) {
                        SettingsToggleRow(
                            title = stringResource(R.string.alerts_master),
                            subtitle = stringResource(R.string.alerts_master_sub),
                            checked = master,
                            onCheckedChange = vm::setClusterAlerts,
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.alerts_important),
                            subtitle = stringResource(R.string.alerts_important_sub),
                            checked = importantOn,
                            onCheckedChange = { vm.setTierOn(AppTier.IMPORTANT, it) },
                            enabled = master,
                        )
                        SettingsToggleRow(
                            title = stringResource(R.string.alerts_medium),
                            subtitle = stringResource(R.string.alerts_medium_sub),
                            checked = mediumOn,
                            onCheckedChange = { vm.setTierOn(AppTier.MEDIUM, it) },
                            enabled = master,
                        )
                    }
                }

                item {
                    SettingsGroup(stringResource(R.string.alerts_text_group), Icons.Filled.Notifications) {
                        SegmentedRow(
                            options = listOf(stringResource(R.string.alerts_text_name_only), stringResource(R.string.alerts_text_preview)),
                            selected = if (nameOnly) 0 else 1,
                            onSelect = { vm.setNameOnly(it == 0) },
                            enabled = master,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        Text(
                            stringResource(R.string.alerts_text_note),
                            style = RcxType.BodySmall.copy(fontSize = 12.sp),
                            color = c.muted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                item {
                    SettingsGroup(stringResource(R.string.settings_connection_alerts), Icons.Filled.Notifications) {
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_connection_alerts),
                            subtitle = stringResource(R.string.settings_connection_alerts_sub),
                            checked = s.connectionAlerts,
                            onCheckedChange = vm::setConnectionAlerts,
                        )
                    }
                }

                // Fills itself from the apps that posted; ignored apps never appear.
                item {
                    SettingsGroup(stringResource(R.string.settings_notif_apps), Icons.Filled.Notifications) {
                        if (notifApps.isEmpty()) {
                            Text(
                                stringResource(R.string.settings_notif_empty),
                                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                                color = c.muted,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        notifApps.groupBy { it.tier }.forEach { (tier, apps) ->
                            Text(
                                stringResource(if (tier == AppTier.IMPORTANT) R.string.settings_notif_important else R.string.settings_notif_medium),
                                style = RcxType.Label.copy(fontSize = 12.sp),
                                color = c.muted,
                                modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
                            )
                            apps.forEach { app ->
                                SettingsToggleRow(
                                    title = app.label,
                                    subtitle = stringResource(if (tier == AppTier.IMPORTANT) R.string.settings_notif_important_sub else R.string.settings_notif_medium_sub),
                                    checked = app.on,
                                    onCheckedChange = { vm.setAppSwitch(app.pkg, it) },
                                    leading = { AppIcon(app.pkg) },
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.alerts_everything_else),
                            style = RcxType.BodySmall.copy(fontSize = 12.sp),
                            color = c.muted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            BottomActions(sideBySide = false) {
                testResult?.let {
                    Text(
                        stringResource(it),
                        style = RcxType.BodySmall.copy(fontSize = 13.sp),
                        color = c.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                PrimaryButton(
                    label = stringResource(R.string.alerts_test),
                    onClick = vm::sendTestAlert,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
