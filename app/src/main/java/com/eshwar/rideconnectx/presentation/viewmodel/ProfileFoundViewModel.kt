package com.eshwar.rideconnectx.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eshwar.rideconnectx.data.local.UserPreferencesStore
import com.eshwar.rideconnectx.domain.model.AuthResult
import com.eshwar.rideconnectx.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The account being signed into, and the guest whose data is waiting. */
data class GuestMergeQuestion(
    val accountName: String = "",
    val accountEmail: String = "",
    val guestName: String = "",
)

/**
 * Backs "Add your guest data to this account?".
 *
 * Asked when a guest signs into an account that already has a profile. The app
 * used to merge silently (rider, 26 Sep); now nothing moves until the rider
 * answers, and they can pick another account instead.
 */
@HiltViewModel
class ProfileFoundViewModel @Inject constructor(
    private val prefs: UserPreferencesStore,
    private val auth: AuthRepository,
) : ViewModel() {

    val question: StateFlow<GuestMergeQuestion> =
        combine(prefs.session, prefs.riderName) { account, guestName ->
            GuestMergeQuestion(
                accountName = account.name,
                accountEmail = account.email,
                guestName = guestName,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, GuestMergeQuestion())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** [onDone] gets whether the account's profile is complete. */
    fun add(onDone: (Boolean) -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                when (val result = auth.addGuestDataToAccount()) {
                    is AuthResult.Success -> onDone(prefs.profileCompleted.first())
                    is AuthResult.Failure -> _error.value = result.error.message
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Something went wrong. Try again."
            } finally {
                _busy.value = false
            }
        }
    }

    fun chooseAnother(onDone: () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                auth.declineGuestMerge()
                onDone()
            } finally {
                _busy.value = false
            }
        }
    }
}
