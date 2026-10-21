package com.eshwar.rideconnectx.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eshwar.rideconnectx.core.di.ApplicationScope
import com.eshwar.rideconnectx.data.local.ServicePreferencesStore
import com.eshwar.rideconnectx.data.local.SessionDataStore
import com.eshwar.rideconnectx.data.local.UserPreferencesStore
import com.eshwar.rideconnectx.data.local.db.RideDatabase
import com.eshwar.rideconnectx.data.repository.VehicleRepository
import com.eshwar.rideconnectx.data.repository.VehicleSelection
import com.eshwar.rideconnectx.domain.model.GuestNameRules
import com.eshwar.rideconnectx.domain.model.UserSession
import com.eshwar.rideconnectx.domain.repository.AuthRepository
import com.eshwar.rideconnectx.domain.repository.BleRepository
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Screen 21 — Profile.
 *
 * The rider name shown here is the one the *cluster* greets them by, which is
 * why it comes from [UserPreferencesStore.riderName] rather than the account's
 * display name.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val photoStore: com.eshwar.rideconnectx.core.util.ProfilePhotoStore,
    private val prefs: UserPreferencesStore,
    private val auth: AuthRepository,
    private val bleRepository: BleRepository,
    private val session: SessionDataStore,
    private val servicePrefs: ServicePreferencesStore,
    private val database: RideDatabase,
    private val rideLog: com.eshwar.rideconnectx.data.nav.RideLog,
    private val vehicleRepository: VehicleRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    /** Account identity, with the rider's chosen display name layered on top. */
    val account: StateFlow<UserSession> = combine(
        prefs.session,
        prefs.riderName,
    ) { account, riderName ->
        account.copy(name = riderName.ifBlank { account.name })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSession())

    val riderNickname: StateFlow<String> =
        prefs.riderNickname.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val riderLocation: StateFlow<String> =
        prefs.riderLocation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val vehicle: StateFlow<VehicleSelection> = vehicleRepository.selection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleSelection())

    /** Profile -> Recent sign-ins. Null while loading; a failure is shown as "couldn't load". */
    private val _recentSignIns = MutableStateFlow<Result<List<com.eshwar.rideconnectx.domain.model.LoginEntry>>?>(null)
    val recentSignIns: StateFlow<Result<List<com.eshwar.rideconnectx.domain.model.LoginEntry>>?> = _recentSignIns.asStateFlow()

    init {
        viewModelScope.launch { _recentSignIns.value = auth.recentSignIns() }
    }

    val connectionState: StateFlow<ConnectionState> = bleRepository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionState.Idle)

    /** Validates the same way the guest name field does. Null when acceptable. */
    /* ── Profile picture ─────────────────────────────────────────────
     *
     * The picture could only be set during Create Profile, which meant the one
     * screen actually called "Profile" could show it and not change it — and a
     * rider who skipped it at setup had no way back to it at all.
     */

    private val _photoVersion = MutableStateFlow(0)
    /** Bumped on every change so the loader re-reads the same file path. */
    val photoVersion: StateFlow<Int> = _photoVersion.asStateFlow()

    private val _pendingPhoto = MutableStateFlow<android.net.Uri?>(null)
    val pendingPhoto: StateFlow<android.net.Uri?> = _pendingPhoto.asStateFlow()

    val hasPhoto: Boolean get() = photoStore.existingPhotoUri != null

    fun onPhotoPicked(uri: android.net.Uri?) {
        if (uri != null) _pendingPhoto.value = uri
    }

    fun cancelCrop() { _pendingPhoto.value = null }

    /**
     * Re-frame the picture already set, without picking a new one (N17): the
     * same cropper, opened on the stored file. Saving decodes it fully before
     * writing, so cropping it in place is safe.
     */
    fun editPhoto() {
        photoStore.existingPhotoUri?.let { _pendingPhoto.value = it }
    }

    /** A quarter turn clockwise, saved and synced like any other change (N17). */
    fun rotatePhoto() {
        appScope.launch {
            photoStore.rotateClockwise() ?: return@launch
            _photoVersion.value += 1
            vehicleRepository.syncPhotoToCloud()
        }
    }

    suspend fun decodeForCrop(uri: android.net.Uri) = photoStore.decodeFull(uri)

    fun confirmCrop(scale: Float, offsetX: Float, offsetY: Float, viewportPx: Int) {
        val source = _pendingPhoto.value ?: return
        _pendingPhoto.value = null
        appScope.launch {
            photoStore.saveCropped(source, scale, offsetX, offsetY, viewportPx)
            _photoVersion.value += 1
            // Push it to the account too, so it survives a reinstall.
            vehicleRepository.syncPhotoToCloud()
        }
    }

    fun removePhoto() {
        appScope.launch {
            photoStore.clear()
            _photoVersion.value += 1
            // Or the account's copy came back at the next sign-in (AUD-4).
            vehicleRepository.clearPhotoInCloud()
        }
    }

    fun validateName(name: String): String? = GuestNameRules.validate(name)

    /**
     * Saves the edited name and location.
     *
     * On [appScope]: this returns straight into a sheet dismissal, and work
     * that must outlive a screen cannot run on that screen's scope — the defect
     * that has already caused three separate bugs in this project.
     */
    fun saveProfile(name: String, location: String, nickname: String) {
        appScope.launch {
            prefs.saveRiderName(name)
            prefs.saveRiderLocation(location)
            prefs.saveRiderNickname(nickname)
            // Saved on the phone only, the next sign-in restored the old name
            // and city from the account (AUD-4).
            vehicleRepository.syncSelectionToCloud(riderName = name.trim(), location = location.trim())
        }
    }

    /**
     * Signs out and clears the local session.
     *
     * The vehicle link is dropped too — leaving a foreground BLE service
     * running for an account that is no longer signed in would be wrong.
     */
    /**
     * [keepLocalProfile] distinguishes the two sheets that both land here.
     * "Move to another account" promises the rider that their name, nickname,
     * city, vehicle, paint and photo follow them to the next account, so it
     * passes true. Plain "Sign out" passes false and the profile is cleared —
     * otherwise the next account signed in on this phone inherits it.
     * The clearing itself is in [AuthRepository.signOut], so the other
     * sign-out call site cannot bypass it.
     */
    fun signOut(keepLocalProfile: Boolean = false, onDone: () -> Unit) {
        appScope.launch {
            bleRepository.disconnect()
            // Setup progress goes with the profile: kept on a move, cleared on a
            // sign-out. Forcing it true on a move sent an unfinished profile
            // straight to the next account's dashboard.
            auth.signOut(keepLocalProfile)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    /**
     * Permanently deletes the account. [onResult] gets null on success, or the
     * reason it stopped - in which case nothing further was deleted.
     */
    fun deleteAccount(activity: android.app.Activity, password: String?, onResult: (String?) -> Unit) {
        appScope.launch {
            val result = auth.deleteAccount(activity, password)
            // Only after the account is gone: a cancelled or failed delete keeps the link.
            if (result !is com.eshwar.rideconnectx.domain.model.AuthResult.Failure) bleRepository.disconnect()
            val error = (result as? com.eshwar.rideconnectx.domain.model.AuthResult.Failure)
                ?.error?.let(::deleteErrorText)
            withContext(Dispatchers.Main) { onResult(error) }
        }
    }

    private fun deleteErrorText(e: com.eshwar.rideconnectx.domain.model.AuthError): String = when (e) {
        is com.eshwar.rideconnectx.domain.model.AuthError.Cancelled -> "Cancelled — your account was not deleted."
        is com.eshwar.rideconnectx.domain.model.AuthError.NoInternet -> "No internet — your account was not deleted."
        is com.eshwar.rideconnectx.domain.model.AuthError.InvalidCredentials ->
            "That didn't match this account — nothing was deleted."
        is com.eshwar.rideconnectx.domain.model.AuthError.SignInTooOld -> e.message
        else -> "Couldn't finish deleting the account — please try again."
    }

    /**
     * Deletes everything cached on this device: rides, notifications, service
     * records, emergency contacts and the paired-vehicle session.
     *
     * The account itself is untouched — signing in again restores the profile
     * from the cloud copy. The screen says so before this runs.
     */
    fun deleteLocalData(onDone: () -> Unit) {
        appScope.launch {
            bleRepository.disconnect()
            database.clearAllTables()
            session.clearSession()
            session.clearTelemetry()
            // The odometer cache is not in Room, so clearAllTables misses it —
            // and it is the one value that survives as a floor on new records.
            servicePrefs.clearOdometer()
            // L4: ride log file, avatar and profile prefs (name, city, vehicle).
            rideLog.clear()
            photoStore.clear()
            prefs.clearProfile()
            withContext(Dispatchers.Main) { onDone() }
        }
    }
}
