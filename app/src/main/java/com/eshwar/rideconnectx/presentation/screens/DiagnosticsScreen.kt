package com.eshwar.rideconnectx.presentation.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.SettingsDivider
import com.eshwar.rideconnectx.presentation.components.SettingsGroup
import com.eshwar.rideconnectx.presentation.components.SettingsLinkRow
import com.eshwar.rideconnectx.presentation.components.SettingsToggleRow
import com.eshwar.rideconnectx.presentation.components.contentWidth
import com.eshwar.rideconnectx.presentation.components.safeDrawingCentred
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import com.eshwar.rideconnectx.presentation.viewmodel.SettingsViewModel

/**
 * Settings > Diagnostics (was "Ride testing"). Records every instruction sent to
 * the cluster (what Maps said, the code, the bytes, delivered or not, reroutes) so a
 * ride can be checked against what the dashboard showed. For testers.
 */
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val context = LocalContext.current
    val rideRecorder by vm.rideRecorder.collectAsStateWithLifecycle()
    val gpsCountdown by vm.gpsCountdown.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

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
            BackHeader(title = stringResource(R.string.settings_diagnostics), onBack = onBack, lifted = headerList.canScrollBackward)

            LazyColumn(
                state = headerList,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.diag_explain),
                        style = com.eshwar.rideconnectx.presentation.theme.RcxType.BodySmall.copy(fontSize = 13.sp),
                        color = c.muted,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    SettingsGroup(stringResource(R.string.settings_diagnostics), Icons.Filled.Tune) {
                        SettingsToggleRow(
                            title = stringResource(R.string.diag_recorder),
                            subtitle = stringResource(R.string.diag_recorder_sub),
                            checked = rideRecorder,
                            onCheckedChange = vm::setRideRecorder,
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = stringResource(R.string.diag_countdown),
                            subtitle = stringResource(R.string.diag_countdown_sub),
                            checked = gpsCountdown,
                            onCheckedChange = vm::setGpsCountdown,
                        )
                        SettingsDivider()
                        SettingsLinkRow(
                            title = stringResource(R.string.diag_share),
                            subtitle = stringResource(R.string.diag_share_sub),
                            onClick = {
                                val f = vm.rideLogFile()
                                if (f == null) {
                                    Toast.makeText(context, R.string.diag_nothing, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, R.string.ride_log_share_warning, Toast.LENGTH_LONG).show()
                                    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
                                    val send = Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(Intent.EXTRA_STREAM, uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    runCatching {
                                        context.startActivity(Intent.createChooser(send, context.getString(R.string.diag_share)))
                                    }.onFailure {
                                        Toast.makeText(context, R.string.diag_no_share_app, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        )
                        SettingsDivider()
                        SettingsLinkRow(
                            title = stringResource(R.string.diag_clear),
                            subtitle = stringResource(R.string.diag_clear_sub),
                            onClick = { confirmClear = true },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmSheet(
            title = stringResource(R.string.diag_clear_title),
            body = stringResource(R.string.diag_clear_body),
            confirmLabel = stringResource(R.string.diag_clear_confirm),
            onDismiss = { confirmClear = false },
            onConfirm = {
                vm.clearRideLog()
                Toast.makeText(context, R.string.diag_cleared, Toast.LENGTH_SHORT).show()
                confirmClear = false
            },
        )
    }
}
