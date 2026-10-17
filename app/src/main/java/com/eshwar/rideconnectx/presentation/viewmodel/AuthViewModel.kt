package com.eshwar.rideconnectx.presentation.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eshwar.rideconnectx.data.local.UserPreferencesStore
import com.eshwar.rideconnectx.domain.model.AuthResult
import com.eshwar.rideconnectx.domain.model.AuthState
import com.eshwar.rideconnectx.domain.model.FieldRules
import com.eshwar.rideconnectx.domain.model.GuestNameRules
import com.eshwar.rideconnectx.domain.model.PasswordRules
import com.eshwar.rideconnectx.domain.model.SyncOutcome
import com.eshwar.rideconnectx.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { Choice, Email }

data class SignInUiState(
    val mode: AuthMode = AuthMode.Choice,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val isNewAccount: Boolean = false,
    val guestName: String = "",
    val guestNameError: String? = null,
    val isBusy: Boolean = false,
    val googleAvailable: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
) {
    val canSubmitEmail: Boolean
        get() = !isBusy &&
            email.contains('@') && email.substringAfterLast('@').contains('.') &&
            password.length >= 6 &&
            (!isNewAccount || (GuestNameRules.isValid(displayName) && PasswordRules.isValid(password)))

    val canContinueGuest: Boolean get() = !isBusy && GuestNameRules.isValid(guestName)
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val prefs: UserPreferencesStore,
) : ViewModel() {

    /** Drives the auth gate: Loading → Authenticated / Unauthenticated. */
    val authState: StateFlow<AuthState> = auth.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    /**
     * True once this device has a rider name and a chosen vehicle — either set
     * up here, or pulled back down from the rider's account on sign-in.
     *
     * Lets a returning rider skip Create Profile after a reinstall, which is the
     * whole reason for signing in with an account in the first place.
     */
    val profileCompleted: StateFlow<Boolean> = prefs.profileCompleted
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The stored value, not the StateFlow's `false` placeholder before it loads. */
    suspend fun isProfileCompleted(): Boolean = prefs.profileCompleted.first()

    /** A guest signed into an account with a profile and has not yet chosen what to do. */
    suspend fun isGuestMergePending(): Boolean = prefs.guestMergePending.first()

    private val _ui = MutableStateFlow(SignInUiState(googleAvailable = auth.isGoogleSignInAvailable))
    val ui: StateFlow<SignInUiState> = _ui.asStateFlow()

    fun setMode(mode: AuthMode) = _ui.update { it.copy(mode = mode, errorMessage = null) }
    fun onEmailChange(v: String) = _ui.update { it.copy(email = v, errorMessage = null) }
    fun onPasswordChange(v: String) = _ui.update { it.copy(password = v.take(PasswordRules.MAX), errorMessage = null) }
    fun onDisplayNameChange(v: String) =
        _ui.update { it.copy(displayName = FieldRules.filterName(v, GuestNameRules.MAX).text) }
    fun toggleNewAccount() = _ui.update { it.copy(isNewAccount = !it.isNewAccount, errorMessage = null) }
    fun dismissMessages() = _ui.update { it.copy(errorMessage = null, infoMessage = null) }

    /** Characters outside the name rule are ignored as typed, with a message (N18). */
    fun onGuestNameChange(raw: String) = _ui.update {
        val f = FieldRules.filterName(raw, GuestNameRules.MAX)
        it.copy(
            guestName = f.text,
            guestNameError = if (f.ignored) FieldRules.NAME_CHARS_MESSAGE
            else it.guestNameError?.takeIf { _ -> !GuestNameRules.isValid(f.text) },
        )
    }

    fun signInWithGoogle(activity: Activity) = run { auth.signInWithGoogle(activity) }

    fun submitEmail() {
        val s = _ui.value
        run {
            if (s.isNewAccount) auth.signUpWithEmail(s.email, s.password, s.displayName)
            else auth.signInWithEmail(s.email, s.password)
        }
    }

    /**
     * [onDone] runs only once the guest is saved. The screen used to navigate in
     * the same tap, which cleared this ViewModel and cancelled the save partway —
     * so the name typed here never reached Create Profile (rider, 26 Sep).
     */
    fun continueAsGuest(onDone: () -> Unit) {
        val name = _ui.value.guestName
        GuestNameRules.validate(name)?.let { error ->
            _ui.update { it.copy(guestNameError = error) }
            return
        }
        run(onSuccess = onDone) { auth.continueAsGuest(name) }
    }

    fun resetPassword() {
        val email = _ui.value.email
        if (!email.contains('@')) {
            _ui.update { it.copy(errorMessage = "Enter your email first.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true) }
            when (val result = auth.sendPasswordReset(email)) {
                is AuthResult.Success ->
                    _ui.update { it.copy(isBusy = false, infoMessage = "Reset link sent to $email.") }
                is AuthResult.Failure ->
                    _ui.update { it.copy(isBusy = false, errorMessage = result.error.message) }
            }
        }
    }

    fun signOut() = viewModelScope.launch { auth.signOut() }

    suspend fun resetUnfinishedSetup() = auth.resetUnfinishedSetup()

    suspend fun dropUnfinishedSignIn() = auth.dropUnfinishedSignIn()

    fun markPermissionsCompleted() = viewModelScope.launch { prefs.setPermissionsCompleted(true) }

    /**
     * Legal acceptance is recorded on sign-in because the Sign In screen states
     * that continuing constitutes agreement.
     */
    private fun run(onSuccess: () -> Unit = {}, block: suspend () -> AuthResult) {
        viewModelScope.launch {
            _ui.update { it.copy(isBusy = true, errorMessage = null) }
            // Busy always clears, even if the call throws (CHK-25).
            try {
                when (val result = block()) {
                    is AuthResult.Success -> {
                        prefs.acceptTerms()
                        prefs.acceptPrivacy()
                        _ui.update { it.copy(isBusy = false, infoMessage = syncNote(result.sync)) }
                        onSuccess()
                    }
                    is AuthResult.Failure ->
                        _ui.update { it.copy(isBusy = false, errorMessage = result.error.message) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(isBusy = false, errorMessage = "Something went wrong. Try again.") }
            } finally {
                _ui.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun syncNote(sync: SyncOutcome): String? = when (sync) {
        SyncOutcome.SYNCED -> null
        SyncOutcome.OFFLINE -> "Signed in. Your profile will sync when you are online."
        SyncOutcome.FAILED -> "Signed in, but your profile could not be saved to the cloud."
    }
}
