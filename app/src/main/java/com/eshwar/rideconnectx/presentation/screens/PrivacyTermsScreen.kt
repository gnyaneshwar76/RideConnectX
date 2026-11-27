package com.eshwar.rideconnectx.presentation.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.BuildConfig
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.domain.model.AuthError
import com.eshwar.rideconnectx.domain.model.LoginMethod
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.BottomActions
import com.eshwar.rideconnectx.presentation.components.DangerButton
import com.eshwar.rideconnectx.presentation.components.SettingsDivider
import com.eshwar.rideconnectx.presentation.components.SettingsGroup
import com.eshwar.rideconnectx.presentation.components.SettingsLinkRow
import com.eshwar.rideconnectx.presentation.components.contentWidth
import com.eshwar.rideconnectx.presentation.components.safeDrawingCentred
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import com.eshwar.rideconnectx.presentation.viewmodel.NotificationsViewModel
import com.eshwar.rideconnectx.presentation.viewmodel.ProfileViewModel

/**
 * Settings > Privacy and terms. What is stored on the phone, the two legal
 * documents, clearing today's notification history, and (signed in only) the
 * in-app account deletion Google Play requires. Delete is pinned at the bottom.
 */
@Composable
fun PrivacyTermsScreen(
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    onSignedOut: () -> Unit,
    profileVm: ProfileViewModel = hiltViewModel(),
    notifVm: NotificationsViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val context = LocalContext.current
    val account by profileVm.account.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val canDelete = !account.isGuest && account.isSignedIn && BuildConfig.CLOUD_ENABLED

    ScreenBackdrop(photo = R.drawable.img_permissions_hero, accent = c.blue) {
        Column(
            Modifier
                .fillMaxHeight()
                .safeDrawingCentred()
                .contentWidth()
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val list = rememberLazyListState()
            BackHeader(title = stringResource(R.string.settings_privacy_terms), onBack = onBack, lifted = list.canScrollBackward)

            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    SettingsGroup(stringResource(R.string.priv_data_title), Icons.Filled.Lock) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                R.string.priv_data_rides, R.string.priv_data_service,
                                R.string.priv_data_notifs, R.string.priv_data_settings,
                            ).forEach { Bullet(stringResource(it)) }
                            Text(stringResource(R.string.priv_data_never), style = RcxType.BodySmall.copy(fontSize = 13.sp), color = c.text)
                            Text(stringResource(R.string.priv_data_account), style = RcxType.BodySmall.copy(fontSize = 13.sp), color = c.muted)
                        }
                    }
                }
                item {
                    SettingsGroup(stringResource(R.string.priv_rules_title), Icons.Filled.Lock) {
                        SettingsLinkRow(title = stringResource(R.string.common_privacy_policy), onClick = onPrivacy)
                        SettingsDivider()
                        SettingsLinkRow(title = stringResource(R.string.settings_terms), onClick = onTerms)
                        SettingsDivider()
                        SettingsLinkRow(
                            title = stringResource(R.string.priv_clear),
                            subtitle = stringResource(R.string.priv_clear_sub),
                            onClick = { confirmClear = true },
                        )
                    }
                }
                if (!canDelete) item {
                    Text(stringResource(R.string.priv_guest_note), style = RcxType.BodySmall.copy(fontSize = 13.sp), color = c.muted)
                }
            }

            if (canDelete) BottomActions {
                DangerButton(stringResource(R.string.priv_delete_account), { confirmDelete = true }, Modifier.fillMaxWidth())
            }
        }
    }

    if (confirmClear) {
        ConfirmSheet(
            title = stringResource(R.string.priv_clear_q),
            body = stringResource(R.string.priv_clear_body),
            confirmLabel = stringResource(R.string.priv_clear_confirm),
            onDismiss = { confirmClear = false },
            onConfirm = {
                notifVm.clearAll()
                Toast.makeText(context, R.string.priv_cleared, Toast.LENGTH_SHORT).show()
                confirmClear = false
            },
        )
    }

    if (confirmDelete) {
        val needsPassword = account.method == LoginMethod.EMAIL
        var password by rememberSaveable { mutableStateOf("") }
        val activity = context as android.app.Activity
        ConfirmSheet(
            title = stringResource(R.string.priv_delete_q),
            body = stringResource(R.string.priv_delete_body) + "\n\n" + stringResource(
                when {
                    needsPassword -> R.string.priv_delete_password
                    account.method == LoginMethod.EMAIL_LINK -> R.string.priv_delete_link
                    else -> R.string.priv_delete_google
                }
            ),
            confirmLabel = stringResource(if (deleting) R.string.priv_deleting else R.string.common_delete),
            onDismiss = { if (!deleting) confirmDelete = false },
            onConfirm = {
                if (deleting || (needsPassword && password.isEmpty())) return@ConfirmSheet
                deleting = true
                profileVm.deleteAccount(activity, password.takeIf { needsPassword }) { error ->
                    deleting = false
                    if (error == null) {
                        confirmDelete = false
                        onSignedOut()
                    } else {
                        Toast.makeText(activity, error, Toast.LENGTH_LONG).show()
                        // Firebase wants a fresh sign-in: sign out and open sign-in (no email is sent here).
                        if (error == AuthError.SignInTooOld.message) {
                            confirmDelete = false
                            profileVm.signOut(onDone = onSignedOut)
                        }
                    }
                }
            },
            extra = if (needsPassword) {
                {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.priv_delete_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else null,
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = Rcx.colors.blue, style = RcxType.BodySmall.copy(fontSize = 13.sp))
        Text(text, color = Rcx.colors.text, style = RcxType.BodySmall.copy(fontSize = 13.sp))
    }
}
