package com.eshwar.rideconnectx.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.domain.model.GuestNameRules
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.viewmodel.AuthViewModel

/**
 * Screen 07 — Guest Profile.
 *
 * Collects a display name so the app can greet the rider. Entirely local:
 * no account, no network, nothing leaves the device.
 */
@Composable
fun GuestProfileScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    vm: AuthViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val state by vm.ui.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }

    // The design opens the keyboard immediately.
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (c.isDark) listOf(Color(0xFF070D1B), Color(0xFF0C1428))
                    else listOf(Color(0xFFE4EEFF), Color(0xFFEEF2FF))
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            BackHeader(title = stringResource(R.string.guest_title), onBack = onBack)

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column {
                    Text(
                        stringResource(R.string.guest_heading),
                        style = RcxType.Wordmark.copy(fontSize = 20.sp),
                        color = c.text,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.guest_subtitle),
                        style = RcxType.Body.copy(fontSize = 14.sp),
                        color = c.muted,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.guest_name_label),
                        style = RcxType.Mono.copy(fontSize = 11.sp),
                        color = c.muted,
                    )
                    OutlinedTextField(
                        value = state.guestName,
                        onValueChange = vm::onGuestNameChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .focusRequester(focus),
                        placeholder = {
                            Text(
                                stringResource(R.string.guest_name_hint),
                                style = RcxType.Body,
                                color = c.muted.copy(alpha = 0.6f),
                            )
                        },
                        singleLine = true,
                        isError = state.guestNameError != null,
                        shape = RoundedCornerShape(16.dp),
                        textStyle = RcxType.Body.copy(fontSize = 16.sp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { vm.continueAsGuest(onContinue) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = c.card,
                            unfocusedContainerColor = c.card,
                            errorContainerColor = c.card,
                            focusedTextColor = c.text,
                            unfocusedTextColor = c.text,
                            cursorColor = c.blue,
                            focusedBorderColor = c.blue.copy(alpha = 0.38f),
                            unfocusedBorderColor = c.border,
                            errorBorderColor = c.red,
                        ),
                    )

                    // Error, or a live character counter as the limit approaches.
                    val error = state.guestNameError
                    when {
                        error != null -> Text(error, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.red)
                        state.guestName.length > GuestNameRules.MAX - 10 -> Text(
                            "${state.guestName.length} / ${GuestNameRules.MAX}",
                            style = RcxType.Mono.copy(fontSize = 11.sp),
                            color = c.muted,
                        )
                        // The rule, always in view (N18).
                        else -> Text(
                            com.eshwar.rideconnectx.domain.model.FieldRules.nameHint(GuestNameRules.MIN, GuestNameRules.MAX),
                            style = RcxType.BodySmall.copy(fontSize = 12.sp),
                            color = c.muted,
                        )
                    }
                }

                // Guest mode caveat, straight from the design.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.blue.copy(alpha = 0.035f))
                        .border(1.dp, c.blue.copy(alpha = 0.133f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = c.blue,
                    )
                    Column {
                        Text(
                            stringResource(if (com.eshwar.rideconnectx.BuildConfig.CLOUD_ENABLED) R.string.guest_nav_notice_title else R.string.guest_nav_notice_title_local),
                            style = RcxType.Label.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            color = c.blue,
                        )
                        Text(
                            stringResource(if (com.eshwar.rideconnectx.BuildConfig.CLOUD_ENABLED) R.string.guest_nav_notice_body else R.string.guest_nav_notice_body_local),
                            style = RcxType.BodySmall.copy(fontSize = 12.sp),
                            color = c.muted,
                        )
                    }
                }
            }

            PrimaryButton(
                label = if (state.isBusy) stringResource(R.string.guest_creating)
                else stringResource(R.string.common_continue),
                onClick = { vm.continueAsGuest(onContinue) },
                enabled = state.canContinueGuest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = androidx.compose.material3.LocalContentColor.current,
                    )
                },
            )
        }
    }
}
