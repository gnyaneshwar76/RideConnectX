package com.eshwar.rideconnectx.presentation.screens

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.components.AnimatedSignInBg
import com.eshwar.rideconnectx.presentation.components.LogoIcon
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import com.eshwar.rideconnectx.domain.model.PasswordRules
import com.eshwar.rideconnectx.presentation.viewmodel.AuthMode
import com.eshwar.rideconnectx.presentation.viewmodel.AuthViewModel
import com.eshwar.rideconnectx.presentation.viewmodel.SignInUiState

/**
 * Screen 06 — Sign In.
 *
 * Guest mode works fully offline. Google Sign-In is present and styled but
 * reports that it is not configured until an OAuth client ID exists; see
 * `AuthRepositoryImpl`.
 */
@Composable
fun SignInScreen(
    onGuest: () -> Unit,
    onTerms: () -> Unit,
    onPrivacy: () -> Unit,
    vm: AuthViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val state by vm.ui.collectAsStateWithLifecycle()
    // Credential Manager shows a system dialog, so it needs the hosting Activity.
    val activity = LocalContext.current as android.app.Activity


    Box(Modifier.fillMaxSize()) {
        // §4 of the Figma export — the living aurora. Dark theme only: the
        // export specifies a near-black space base, and the same orbs over a
        // pale background would be invisible, so light keeps its gradient.
        if (c.isDark) {
            AnimatedSignInBg()
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFE4EEFF), Color(0xFFEEF2FF))
                        )
                    )
            )
        }

        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LogoIcon(size = 72.dp)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.signin_welcome_back), style = RcxType.Wordmark.copy(fontSize = 22.sp), color = c.text)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.signin_subtitle),
                    style = RcxType.Body.copy(fontSize = 14.sp),
                    color = c.muted,
                )
            }

            Column(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Google — Google's brand guidelines require the white button.
                if (!com.eshwar.rideconnectx.BuildConfig.CLOUD_ENABLED) {
                    Text(
                        stringResource(R.string.signin_cloud_off),
                        style = RcxType.BodySmall.copy(fontSize = 12.sp),
                        color = c.muted,
                    )
                }
                val googleShape = RoundedCornerShape(16.dp)
                if (com.eshwar.rideconnectx.BuildConfig.CLOUD_ENABLED) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(googleShape)
                        .background(Color.White)
                        .then(
                            if (c.isDark) Modifier
                            else Modifier.border(1.dp, c.border, googleShape)
                        )
                        .clickable(enabled = !state.isBusy) { vm.signInWithGoogle(activity) },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF1A1A2E),
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.ic_google),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            stringResource(R.string.signin_google),
                            style = RcxType.Button.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF1A1A2E),
                        )
                    }
                }

                if (state.mode == AuthMode.Email) {
                    EmailForm(state = state, vm = vm)
                } else {
                    PrimaryButton(
                        label = stringResource(R.string.signin_email),
                        onClick = { vm.setMode(AuthMode.Email) },
                        modifier = Modifier.fillMaxWidth(),
                        secondary = true,
                        enabled = !state.isBusy,
                        icon = {
                            Icon(Icons.Default.Email, null, Modifier.size(18.dp), tint = c.blue)
                        },
                    )
                }
                }

                PrimaryButton(
                    label = stringResource(R.string.signin_guest),
                    onClick = onGuest,
                    modifier = Modifier.fillMaxWidth(),
                    secondary = true,
                    enabled = !state.isBusy,
                    icon = {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = c.blue,
                        )
                    },
                )

                // Shown when Google Sign-In has no OAuth client ID yet.
                (state.errorMessage ?: state.infoMessage)?.let { notice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.amber.copy(alpha = 0.06f))
                            .border(1.dp, c.amber.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                            .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
                            .clickable { vm.dismissMessages() }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = c.amber,
                        )
                        Text(
                            notice,
                            style = RcxType.BodySmall.copy(fontSize = 12.sp),
                            color = c.muted,
                        )
                    }
                }

                LegalFooter(onTerms = onTerms, onPrivacy = onPrivacy)
            }
        }
    }
}

@Composable
private fun LegalFooter(onTerms: () -> Unit, onPrivacy: () -> Unit) {
    val c = Rcx.colors
    val link = SpanStyle(color = c.blue, fontWeight = FontWeight.SemiBold)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.signin_legal_prefix))
                withStyle(link) { append(stringResource(R.string.common_terms)) }
            },
            style = RcxType.BodySmall.copy(fontSize = 10.5.sp),
            color = c.muted.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onTerms).defaultMinSize(minHeight = 48.dp).wrapContentHeight(Alignment.CenterVertically),
        )
        Text(
            text = stringResource(R.string.signin_legal_and),
            style = RcxType.BodySmall.copy(fontSize = 10.5.sp),
            color = c.muted.copy(alpha = 0.7f),
        )
        Text(
            text = buildAnnotatedString { withStyle(link) { append(stringResource(R.string.common_privacy_policy)) } },
            style = RcxType.BodySmall.copy(fontSize = 10.5.sp),
            color = c.muted.copy(alpha = 0.7f),
            modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onPrivacy).defaultMinSize(minHeight = 48.dp).wrapContentHeight(Alignment.CenterVertically),
        )
    }
}


/** Email + password entry, doubling as sign-up when the toggle is on. */
@Composable
private fun EmailForm(state: SignInUiState, vm: AuthViewModel) {
    val c = Rcx.colors

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = c.card,
        unfocusedContainerColor = c.card,
        focusedTextColor = c.text,
        unfocusedTextColor = c.text,
        cursorColor = c.blue,
        focusedBorderColor = c.blue.copy(alpha = 0.38f),
        unfocusedBorderColor = c.border,
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.isNewAccount) {
            OutlinedTextField(
                value = state.displayName,
                onValueChange = vm::onDisplayNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.signin_field_name), style = RcxType.Body, color = c.muted.copy(alpha = 0.6f)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                textStyle = RcxType.Body.copy(fontSize = 15.sp),
                colors = fieldColors,
            )
        }

        OutlinedTextField(
            value = state.email,
            onValueChange = vm::onEmailChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.signin_field_email), style = RcxType.Body, color = c.muted.copy(alpha = 0.6f)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            textStyle = RcxType.Body.copy(fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = fieldColors,
        )

        OutlinedTextField(
            value = state.password,
            onValueChange = vm::onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.signin_field_password), style = RcxType.Body, color = c.muted.copy(alpha = 0.6f)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            textStyle = RcxType.Body.copy(fontSize = 15.sp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = fieldColors,
        )

        // New accounts only — older accounts may have shorter passwords.
        if (state.isNewAccount) {
            Text(
                stringResource(R.string.signin_password_rule),
                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                color = if (PasswordRules.isValid(state.password)) c.green else c.muted,
            )
        }

        PrimaryButton(
            label = if (state.isNewAccount) stringResource(R.string.signin_create_account)
                else stringResource(R.string.signin_sign_in),
            onClick = vm::submitEmail,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmitEmail || state.isBusy,
            busy = state.isBusy,
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state.isNewAccount) stringResource(R.string.signin_have_account)
                else stringResource(R.string.signin_new_here),
                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                color = c.blue,
                modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { vm.toggleNewAccount() }.defaultMinSize(minHeight = 48.dp).wrapContentHeight(Alignment.CenterVertically),
            )
            if (!state.isNewAccount) {
                Text(
                    stringResource(R.string.signin_forgot),
                    style = RcxType.BodySmall.copy(fontSize = 12.sp),
                    color = c.muted,
                    modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { vm.resetPassword() }.defaultMinSize(minHeight = 48.dp).wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }
    }
}
