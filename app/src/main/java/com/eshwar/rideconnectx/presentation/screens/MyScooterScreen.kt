package com.eshwar.rideconnectx.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.BottomActions
import com.eshwar.rideconnectx.presentation.components.DangerButton
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.components.SettingsDivider
import com.eshwar.rideconnectx.presentation.components.SettingsGroup
import com.eshwar.rideconnectx.presentation.components.SettingsLinkRow
import com.eshwar.rideconnectx.presentation.components.contentWidth
import com.eshwar.rideconnectx.presentation.components.safeDrawingCentred
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import com.eshwar.rideconnectx.presentation.viewmodel.DashboardViewModel
import com.eshwar.rideconnectx.presentation.viewmodel.SettingsViewModel

/**
 * My scooter: the same picture as the Home hero card, the scooter's details, and
 * at the bottom, pinned and stacked, Connect now (or Disconnect) then Forget this vehicle.
 * Opened from the "My scooter" row in Settings.
 */
@Composable
fun MyScooterScreen(
    onBack: () -> Unit,
    onPairVehicle: () -> Unit,
    dash: DashboardViewModel = hiltViewModel(),
    vm: SettingsViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val state by dash.uiState.collectAsStateWithLifecycle()
    val unit by dash.distanceUnit.collectAsStateWithLifecycle()
    val hasSaved by vm.hasSavedScooter.collectAsStateWithLifecycle()
    var confirmForget by rememberSaveable { mutableStateOf(false) }

    val link = state.connectionState
    val connected = state.isConnected
    val linking = link is ConnectionState.Scanning || link is ConnectionState.Connecting
    val vehicle = state.vehicle

    ScreenBackdrop(photo = R.drawable.img_permissions_hero, accent = c.blue) {
        Column(
            Modifier
                .fillMaxHeight()
                .safeDrawingCentred()
                .contentWidth()
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val scroll = rememberScrollState()
            BackHeader(title = stringResource(R.string.settings_my_scooter), onBack = onBack, lifted = scroll.canScrollBackward)

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // The Home hero card itself, so the two always match.
                if (vehicle != null) VehicleHeroCard(state, vehicle, unit, onPairVehicle)

                SettingsGroup(stringResource(R.string.my_scooter_details), Icons.Filled.TwoWheeler) {
                    SettingsLinkRow(
                        title = stringResource(R.string.my_scooter_model),
                        value = vehicle?.name ?: stringResource(R.string.my_scooter_none),
                        chevron = false,
                    )
                    SettingsDivider()
                    SettingsLinkRow(
                        title = stringResource(R.string.my_scooter_colour),
                        value = state.vehicleColor?.name ?: "—",
                        chevron = false,
                    )
                    SettingsDivider()
                    SettingsLinkRow(
                        title = stringResource(R.string.my_scooter_status),
                        value = stringResource(
                            when {
                                connected -> R.string.dash_connected
                                linking -> R.string.my_scooter_connecting
                                else -> R.string.my_scooter_not_connected
                            }
                        ),
                        chevron = false,
                    )
                    SettingsDivider()
                    SettingsLinkRow(
                        title = stringResource(R.string.my_scooter_odometer),
                        value = state.odometer?.let { unit.format(it) } ?: "—",
                        chevron = false,
                    )
                    SettingsDivider()
                    SettingsLinkRow(
                        title = stringResource(R.string.my_scooter_auto_connect),
                        subtitle = stringResource(
                            if (hasSaved) R.string.my_scooter_auto_connect_on else R.string.my_scooter_auto_connect_off
                        ),
                        chevron = false,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            BottomActions {
                when {
                    connected -> PrimaryButton(
                        label = stringResource(R.string.my_scooter_disconnect),
                        onClick = vm::disconnect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Nothing paired yet: Connect now would do nothing, so it leads to pairing.
                    !hasSaved -> PrimaryButton(
                        label = stringResource(R.string.my_scooter_pair),
                        onClick = onPairVehicle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> PrimaryButton(
                        label = stringResource(if (linking) R.string.my_scooter_connecting else R.string.my_scooter_connect_now),
                        onClick = vm::connectNow,
                        enabled = !linking,
                        busy = linking,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DangerButton(
                    label = stringResource(R.string.settings_forget),
                    onClick = { confirmForget = true },
                    enabled = hasSaved || connected,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (confirmForget) {
        ConfirmSheet(
            title = stringResource(R.string.my_scooter_forget_title),
            body = stringResource(R.string.my_scooter_forget_body),
            confirmLabel = stringResource(R.string.my_scooter_forget_confirm),
            onDismiss = { confirmForget = false },
            onConfirm = {
                vm.forgetVehicle()
                confirmForget = false
            },
        )
    }
}
