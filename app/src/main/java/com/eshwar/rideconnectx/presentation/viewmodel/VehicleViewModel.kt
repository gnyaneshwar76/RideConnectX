package com.eshwar.rideconnectx.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eshwar.rideconnectx.core.util.CityLocator
import com.eshwar.rideconnectx.core.util.ProfilePhotoStore
import com.eshwar.rideconnectx.data.local.UserPreferencesStore
import com.eshwar.rideconnectx.data.repository.VehicleRepository
import com.eshwar.rideconnectx.data.repository.VehicleSelection
import com.eshwar.rideconnectx.domain.model.GuestNameRules
import com.eshwar.rideconnectx.domain.model.LoginMethod
import com.eshwar.rideconnectx.domain.model.Vehicle
import com.eshwar.rideconnectx.domain.model.VehicleCatalog
import com.eshwar.rideconnectx.domain.model.VehicleCategory
import com.eshwar.rideconnectx.domain.model.VehicleColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** How long Create Profile waits for sign-in to finish saving the session. */
private const val SESSION_WAIT_MS = 5_000L

/** Which sub-sheet of the profile step is open, if any. */
enum class VehiclePicker { None, Type, Model, Color }

/**
 * What the profile form is being opened for.
 *
 * [CHANGE_VEHICLE] exists because "Change vehicle" used to reopen the whole
 * Create Profile step — name, city, photo and the Terms checkbox — which reads
 * as the app having forgotten who the rider is. Changing a vehicle changes a
 * vehicle; everything else was settled the first time.
 */
enum class ProfileFormMode { CREATE, CHANGE_VEHICLE }

/** Longest nickname the dashboard header can show without truncating. */
const val NICKNAME_MAX = com.eshwar.rideconnectx.domain.model.FieldRules.NICKNAME_MAX

data class VehicleUiState(
    val query: String = "",
    val category: VehicleCategory = VehicleCategory.SCOOTER,
    val riderName: String = "",
    /** Short name for the dashboard header. Required, and never auto-filled. */
    val nickname: String = "",
    val location: String = "",
    val termsAccepted: Boolean = false,
    val picker: VehiclePicker = VehiclePicker.None,
    /** Local file Uri of the cropped avatar, or null while there isn't one. */
    val photoUri: android.net.Uri? = null,
    /** Bumped on every change so the image loader re-reads the same path. */
    val photoVersion: Int = 0,
    /** Set while the rider is framing a freshly picked image. */
    val pendingPhoto: android.net.Uri? = null,
    val isLocating: Boolean = false,
    val message: String? = null,
    /** True once the rider has opened the type sheet, so the coach mark retires. */
    val hasTouchedVehicle: Boolean = false,
    /** Set for one beat to flash the nickname field when it is blocking. */
    val nicknameNudge: Boolean = false,
    /** The last keystroke had a character the name rule ignores (N18). */
    val nameIgnored: Boolean = false,
    val nicknameIgnored: Boolean = false,
) {
    /** Search spans both sections; browsing is scoped to the active tab. */
    val results: List<Vehicle>
        get() = if (query.isBlank()) VehicleCatalog.byCategory(category)
        else VehicleCatalog.search(query)

    val isSearching: Boolean get() = query.isNotBlank()

    val nameError: String? get() = when {
        nameIgnored -> com.eshwar.rideconnectx.domain.model.FieldRules.NAME_CHARS_MESSAGE
        riderName.isBlank() -> null
        else -> GuestNameRules.validate(riderName)
    }

    /**
     * Nicknames are capped well below the full name: this is the string the
     * dashboard header has to fit beside the avatar and the connection pill.
     */
    val nicknameError: String? get() = when {
        nicknameIgnored -> com.eshwar.rideconnectx.domain.model.FieldRules.NAME_CHARS_MESSAGE
        nickname.isBlank() -> null
        else -> com.eshwar.rideconnectx.domain.model.FieldRules.nicknameError(nickname)
    }

    val nicknameValid: Boolean
        get() = com.eshwar.rideconnectx.domain.model.FieldRules.nicknameError(nickname) == null
}

/**
 * Drives the Create Profile step — the one-time screen between sign-in and the
 * dashboard where the rider names themselves and picks their machine.
 *
 * The dashboard reads all of this back: the header name, the vehicle name, the
 * artwork and its paint. Nothing here needs the network; the catalogue is local
 * and the choice lives in DataStore, so the step completes offline.
 */
@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val repo: VehicleRepository,
    private val prefs: UserPreferencesStore,
    private val cityLocator: CityLocator,
    private val photoStore: ProfilePhotoStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(VehicleUiState())
    val ui: StateFlow<VehicleUiState> = _ui.asStateFlow()

    /** Persisted choice; drives the checkmarks and the Continue button. */
    val selection: StateFlow<VehicleSelection> = repo.selection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleSelection())

    val scooters: List<Vehicle> get() = VehicleCatalog.scooters
    val motorcycles: List<Vehicle> get() = VehicleCatalog.motorcycles

    init {
        // Pre-fill from the account so a returning rider re-confirms rather
        // than retypes. Terms were accepted at sign-in, but this screen asks
        // again against the specific vehicle, so it starts unticked.
        viewModelScope.launch {
            val saved = prefs.riderName.first()
            // Wait for sign-in to finish saving the session. Firebase flips the
            // auth state - and navigates here - a moment before that write, so
            // an immediate read saw "not signed in": no Google name, no Google
            // picture, just the letter avatar (21 Sep, fresh install).
            val session = withTimeoutOrNull(SESSION_WAIT_MS) {
                prefs.session.first { it.isSignedIn }
            } ?: prefs.session.first()

            // Auto-fill only for Google, where the name and picture come from an
            // account the rider already curated. Email and guest riders typed
            // whatever they typed to get in — prefilling that here would be
            // presumptuous, and they should fill this in deliberately. A guest's
            // name is the exception, and is already in `saved`: the guest screen
            // asks for exactly this name, so asking twice was the bug.
            val fromGoogle = session.method == LoginMethod.GOOGLE

            _ui.update {
                it.copy(
                    riderName = saved.ifBlank { if (fromGoogle) session.name else "" },
                    // Never derived from the account name — see onNicknameChange.
                    nickname = prefs.riderNickname.first(),
                    location = prefs.riderLocation.first(),
                    photoUri = photoStore.existingPhotoUri,
                )
            }

            // Pull the Google profile picture once, if the rider has not already
            // chosen one of their own.
            if (fromGoogle && photoStore.existingPhotoUri == null && session.photoUrl.isNotBlank()) {
                photoStore.saveFromUrl(session.photoUrl)?.let { uri ->
                    _ui.update { it.copy(photoUri = uri, photoVersion = it.photoVersion + 1) }
                }
            }
        }
    }

    /**
     * Stores the picked image as a centred square. Cropping happens here rather
     * than in the UI so the file on disk is already the right shape.
     */
    /** A pick opens the cropper rather than saving straight away. */
    fun onPhotoPicked(uri: android.net.Uri?) {
        if (uri == null) return
        _ui.update { it.copy(pendingPhoto = uri) }
    }

    fun cancelCrop() = _ui.update { it.copy(pendingPhoto = null) }

    /** Decoder handed to the cropper so it can show any format the phone made. */
    suspend fun decodeForCrop(uri: android.net.Uri) = photoStore.decodeFull(uri)

    /** Saves exactly the region the rider framed inside the circle. */
    fun confirmCrop(scale: Float, offsetX: Float, offsetY: Float, viewportPx: Int) {
        val source = _ui.value.pendingPhoto ?: return
        viewModelScope.launch {
            _ui.update { it.copy(pendingPhoto = null) }
            val saved = photoStore.saveCropped(source, scale, offsetX, offsetY, viewportPx)
            if (saved == null) {
                _ui.update { it.copy(message = "Couldn't use that image. Try another one.") }
            } else {
                // Cache-buster: the path never changes, so the loader would
                // otherwise keep showing the previous photo.
                _ui.update { it.copy(photoUri = saved, photoVersion = it.photoVersion + 1) }
            }
        }
    }

    fun removePhoto() {
        viewModelScope.launch {
            photoStore.clear()
            _ui.update { it.copy(photoUri = null, photoVersion = it.photoVersion + 1) }
        }
    }

    fun onQueryChange(q: String) = _ui.update { it.copy(query = q) }

    fun onCategoryChange(c: VehicleCategory) = _ui.update { it.copy(category = c, query = "") }

    fun onRiderNameChange(v: String) = _ui.update {
        val f = com.eshwar.rideconnectx.domain.model.FieldRules.filterName(v, GuestNameRules.MAX)
        it.copy(riderName = f.text, nameIgnored = f.ignored)
    }

    /**
     * Deliberately not derived from the full name.
     *
     * Auto-filling it would defeat the point: the rider's account name is the
     * thing that does not fit, so guessing a short form from it lands back
     * where it started. They choose.
     */
    fun onNicknameChange(v: String) = _ui.update {
        val f = com.eshwar.rideconnectx.domain.model.FieldRules.filterName(v, NICKNAME_MAX)
        it.copy(nickname = f.text, nicknameIgnored = f.ignored, nicknameNudge = false)
    }

    /** Fires the highlight on the nickname field when Continue is blocked. */
    fun nudgeNickname() = _ui.update { it.copy(nicknameNudge = true) }

    fun clearNicknameNudge() = _ui.update { it.copy(nicknameNudge = false) }

    fun onLocationChange(v: String) =
        _ui.update { it.copy(location = v.take(com.eshwar.rideconnectx.domain.model.FieldRules.CITY_MAX)) }

    fun onTermsChange(v: Boolean) = _ui.update { it.copy(termsAccepted = v) }

    fun openPicker(p: VehiclePicker) = _ui.update {
        it.copy(picker = p, query = "", hasTouchedVehicle = it.hasTouchedVehicle || p == VehiclePicker.Type)
    }

    fun closePicker() = _ui.update { it.copy(picker = VehiclePicker.None, query = "") }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    /**
     * Fills the location field from GPS. Silent about *why* it failed beyond a
     * single line — the field is editable, so a failure is an inconvenience,
     * not a dead end.
     */
    fun detectLocation() {
        if (_ui.value.isLocating) return
        viewModelScope.launch {
            _ui.update { it.copy(isLocating = true, message = null) }
            val city = cityLocator.currentCity()
            _ui.update {
                if (city != null) {
                    it.copy(isLocating = false, location = city)
                } else {
                    it.copy(
                        isLocating = false,
                        message = if (cityLocator.hasPermission) {
                            "Couldn't get a location fix. Type your city instead."
                        } else {
                            "Location permission is off. Type your city instead."
                        },
                    )
                }
            }
        }
    }

    /** Picking a type moves straight on to the models for that type. */
    fun selectCategory(c: VehicleCategory) = _ui.update {
        it.copy(category = c, picker = VehiclePicker.Model, query = "")
    }

    /**
     * Choosing a model clears any previously chosen paint (a colour belongs to
     * one model) and opens the colour sheet, so the rider is never left with a
     * model and no paint.
     */
    fun selectVehicle(vehicle: Vehicle) = viewModelScope.launch {
        repo.selectVehicle(vehicle)
        _ui.update { it.copy(picker = VehiclePicker.Color, query = "") }
    }

    fun selectColor(color: VehicleColor) = viewModelScope.launch {
        repo.selectColor(color)
        _ui.update { it.copy(picker = VehiclePicker.None) }
    }

    /** Colour row on the main screen — no sheet to close. */
    fun pickColorInline(color: VehicleColor) = viewModelScope.launch {
        repo.selectColor(color)
    }

    /**
     * Everything except the nickname.
     *
     * The button stays live once the rest of the form is done, so tapping it
     * with an empty nickname *does something* — it flashes the field that is
     * blocking. A button that is simply dead gives the rider nothing to follow.
     */
    fun canContinueIgnoringNickname(mode: ProfileFormMode = ProfileFormMode.CREATE): Boolean =
        with(_ui.value) {
            when (mode) {
                ProfileFormMode.CREATE ->
                    GuestNameRules.isValid(riderName) && termsAccepted && selection.value.isComplete
                ProfileFormMode.CHANGE_VEHICLE -> selection.value.isComplete
            }
        }

    fun canContinue(mode: ProfileFormMode = ProfileFormMode.CREATE): Boolean = with(_ui.value) {
        when (mode) {
            ProfileFormMode.CREATE ->
                GuestNameRules.isValid(riderName) && nicknameValid &&
                    termsAccepted && selection.value.isComplete
            // Name, city and consent are not on screen in this mode, so they
            // cannot gate the button.
            ProfileFormMode.CHANGE_VEHICLE -> selection.value.isComplete
        }
    }

    /** Commits the profile. [onDone] runs only after everything is stored. */
    fun saveProfile(mode: ProfileFormMode = ProfileFormMode.CREATE, onDone: () -> Unit) {
        if (!canContinue(mode)) return
        viewModelScope.launch {
            val s = _ui.value

            if (mode == ProfileFormMode.CREATE) {
                prefs.saveRiderName(s.riderName)
                prefs.saveRiderNickname(s.nickname)
                prefs.saveRiderLocation(s.location)
                prefs.setProfileCompleted(true)
            }

            // The cloud document holds the whole profile, so a vehicle-only
            // change still has to send the name and city — read back from what
            // is stored rather than from a form that never showed them, or the
            // rename would be undone by changing a scooter.
            repo.syncSelectionToCloud(
                riderName = if (mode == ProfileFormMode.CREATE) s.riderName else prefs.riderName.first(),
                location = if (mode == ProfileFormMode.CREATE) s.location else prefs.riderLocation.first(),
            )
            onDone()
        }
    }
}
