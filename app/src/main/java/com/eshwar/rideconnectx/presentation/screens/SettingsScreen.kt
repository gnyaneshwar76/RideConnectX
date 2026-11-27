package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.presentation.components.safeDrawingCentred
import com.eshwar.rideconnectx.presentation.components.contentWidth
import com.eshwar.rideconnectx.presentation.theme.sheetEnter
import com.eshwar.rideconnectx.presentation.theme.sheetContainerColor
import com.eshwar.rideconnectx.presentation.theme.GlassSheetWindow
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import com.eshwar.rideconnectx.presentation.theme.cardSurface
import androidx.core.graphics.drawable.toBitmap
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import com.eshwar.rideconnectx.presentation.components.dockSpace
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.eshwar.rideconnectx.data.nav.AppTier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.BuildConfig
import com.eshwar.rideconnectx.domain.model.DistanceUnit
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.components.DangerButton
import com.eshwar.rideconnectx.presentation.components.SheetScaffold
import com.eshwar.rideconnectx.presentation.components.RiderAvatar
import com.eshwar.rideconnectx.presentation.components.SettingsChoiceRow
import com.eshwar.rideconnectx.presentation.components.SettingsDivider
import com.eshwar.rideconnectx.presentation.components.SettingsGroup
import com.eshwar.rideconnectx.presentation.components.SettingsLinkRow
import com.eshwar.rideconnectx.presentation.components.SettingsToggleRow
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.viewmodel.SettingsViewModel

/**
 * Screen 20 — Settings root.
 *
 * Nine rows, each with a one-line subtitle, each opening its own page that has
 * a way back. Everything on the pages saves the instant it changes. The rows
 * are in the order the owner approved on 26 Nov 2026.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onProfile: () -> Unit,
    onMyScooter: () -> Unit,
    onService: () -> Unit,
    onAlerts: () -> Unit,
    onAppearance: () -> Unit,
    onPermissions: () -> Unit,
    onPrivacy: () -> Unit,
    onAbout: () -> Unit,
    onDiagnostics: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val s by vm.settings.collectAsStateWithLifecycle()
    val connection by vm.connectionState.collectAsStateWithLifecycle()
    val vehicleName by vm.vehicleName.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val serviceStatus by vm.serviceStatus.collectAsStateWithLifecycle()

    val name = account.name.ifBlank { stringResource(R.string.common_rider) }
    val profileSub = when {
        account.isGuest -> stringResource(R.string.settings_profile_sub_guest, name)
        account.email.isNotBlank() -> account.email
        else -> name
    }
    val scooterSub = if (vehicleName.isBlank()) stringResource(R.string.my_scooter_none) else stringResource(
        R.string.settings_scooter_sub,
        vehicleName,
        stringResource(if (connection is ConnectionState.Connected) R.string.dash_connected else R.string.my_scooter_not_connected),
    )
    val km = serviceStatus.remainingKm
    val days = serviceStatus.remainingDays
    val serviceSub = when {
        !serviceStatus.hasBaseline -> stringResource(R.string.settings_service_none)
        serviceStatus.isOverdue -> stringResource(R.string.settings_service_overdue)
        km != null -> stringResource(R.string.settings_service_next, s.distanceUnit.format(km))
        days != null -> stringResource(R.string.settings_service_next_days, days)
        else -> stringResource(R.string.settings_service_none)
    }
    val shape = RoundedCornerShape(20.dp)

    ScreenBackdrop(photo = R.drawable.img_permissions_hero, accent = c.blue) {
        Column(
            Modifier
                .fillMaxHeight()
                .safeDrawingCentred()
                .dockSpace()
                .contentWidth()
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val headerList = androidx.compose.foundation.lazy.rememberLazyListState()
            BackHeader(showBack = false, title = stringResource(R.string.settings_title), onBack = onBack, lifted = headerList.canScrollBackward)

            LazyColumn(
                state = headerList,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            ) {
                item {
                    Column(Modifier.fillMaxWidth().clip(shape).cardSurface(shape).padding(vertical = 6.dp)) {
                        SettingsLinkRow(stringResource(R.string.settings_profile), profileSub, onClick = onProfile)
                        SettingsDivider()
                        SettingsLinkRow(stringResource(R.string.settings_my_scooter), scooterSub, onClick = onMyScooter)
                        SettingsDivider()
                        SettingsLinkRow(stringResource(R.string.service_title), serviceSub, onClick = onService)
                    }
                }
                item {
                    Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(shape).cardSurface(shape).padding(vertical = 6.dp)) {
                        SettingsLinkRow(stringResource(R.string.settings_alerts), stringResource(R.string.settings_alerts_sub), onClick = onAlerts)
                        SettingsDivider()
                        SettingsLinkRow(
                            stringResource(R.string.settings_appearance_units),
                            stringResource(R.string.settings_appearance_units_sub),
                            value = s.distanceUnit.short,
                            onClick = onAppearance,
                        )
                        SettingsDivider()
                        SettingsLinkRow(stringResource(R.string.settings_permissions), stringResource(R.string.settings_permissions_sub), onClick = onPermissions)
                    }
                }
                item {
                    Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(shape).cardSurface(shape).padding(vertical = 6.dp)) {
                        SettingsLinkRow(stringResource(R.string.settings_privacy_terms), stringResource(R.string.settings_privacy_terms_sub), onClick = onPrivacy)
                        SettingsDivider()
                        SettingsLinkRow(stringResource(R.string.settings_help), stringResource(R.string.settings_help_sub), onClick = onAbout)
                        SettingsDivider()
                        SettingsLinkRow(stringResource(R.string.settings_diagnostics), stringResource(R.string.settings_diagnostics_sub), onClick = onDiagnostics)
                    }
                }
            }
        }
    }
}

private val iconCache = java.util.concurrent.ConcurrentHashMap<String, androidx.compose.ui.graphics.ImageBitmap>()

/** The app's own icon; a bell when Android hides that package from us (package visibility). */
@Composable
internal fun AppIcon(pkg: String) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(iconCache[pkg], pkg) {
        value = iconCache[pkg] ?: withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }?.also { iconCache[pkg] = it }
    }
    val shape = RoundedCornerShape(10.dp)
    val bmp = bitmap
    if (bmp != null) {
        androidx.compose.foundation.Image(bmp, null, Modifier.size(34.dp).clip(shape))
    } else {
        Box(Modifier.size(34.dp).clip(shape).background(Rcx.colors.muted.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Notifications, null, Modifier.size(16.dp), tint = Rcx.colors.muted)
        }
    }
}

/** A short list of choices in a bottom sheet; scrolls when it is taller than the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChoiceSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = Rcx.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetContainerColor(),
    ) {
        SheetScaffold {
            Text(
                title,
                style = RcxType.Section.copy(fontSize = 17.sp),
                color = c.text,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            content()
        }
    }
}

/**
 * Shared confirmation, used wherever the design says "confirm before...".
 * Buttons are pinned under a scrolling body, stacked: the safe choice first,
 * the red one last.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    /** Optional content between the body and the buttons, e.g. a password field. */
    extra: (@Composable () -> Unit)? = null,
    /** Red only when the action deletes, clears or discards; otherwise the accent button. */
    destructive: Boolean = true,
) {
    val c = Rcx.colors
    val cancel = stringResource(R.string.common_cancel)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetContainerColor(),
    ) {
        SheetScaffold(
            buttons = {
                if (destructive) {
                    PrimaryButton(cancel, onDismiss, secondary = true, modifier = Modifier.fillMaxWidth())
                    DangerButton(confirmLabel, onConfirm, modifier = Modifier.fillMaxWidth())
                } else {
                    PrimaryButton(confirmLabel, onConfirm, modifier = Modifier.fillMaxWidth())
                    PrimaryButton(cancel, onDismiss, secondary = true, modifier = Modifier.fillMaxWidth())
                }
            },
        ) {
            Text(title, style = RcxType.Section.copy(fontSize = 17.sp), color = c.text)
            Spacer(Modifier.height(8.dp))
            Text(body, style = RcxType.BodySmall.copy(fontSize = 13.sp), color = c.muted)
            extra?.let {
                Spacer(Modifier.height(16.dp))
                it()
            }
        }
    }
}
