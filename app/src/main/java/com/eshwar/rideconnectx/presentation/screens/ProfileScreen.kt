package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.presentation.components.safeDrawingCentred
import com.eshwar.rideconnectx.presentation.components.contentWidth
import com.eshwar.rideconnectx.presentation.theme.sheetEnter
import com.eshwar.rideconnectx.presentation.theme.innerFill
import com.eshwar.rideconnectx.presentation.theme.sheetContainerColor
import com.eshwar.rideconnectx.presentation.theme.GlassSheetWindow
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import com.eshwar.rideconnectx.presentation.theme.cardSurface
import com.eshwar.rideconnectx.presentation.theme.enterRise
import com.eshwar.rideconnectx.presentation.theme.shake
import com.eshwar.rideconnectx.presentation.theme.rememberShakeKey
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.core.util.ProfilePhotoStore
import com.eshwar.rideconnectx.domain.model.LoginMethod
import com.eshwar.rideconnectx.domain.model.Vehicle
import com.eshwar.rideconnectx.domain.model.VehicleColor
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import com.eshwar.rideconnectx.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.eshwar.rideconnectx.core.util.AppPermissions
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.BottomActions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.components.RiderAvatar
import com.eshwar.rideconnectx.domain.model.FieldRules
import com.eshwar.rideconnectx.domain.model.GuestNameRules
import com.eshwar.rideconnectx.domain.model.IndianCities
import com.eshwar.rideconnectx.presentation.components.RcxPhotoFill
import com.eshwar.rideconnectx.presentation.components.SettingsDivider
import com.eshwar.rideconnectx.presentation.components.SettingsGroup
import com.eshwar.rideconnectx.presentation.components.SettingsLinkRow
import com.eshwar.rideconnectx.presentation.components.VehicleStage
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.pressSquash
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.viewmodel.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Screen 21 — Profile.
 *
 * Account, vehicle and the four account actions. Guest and signed-in riders are
 * handled differently, as the design requires: a guest has no email and gets a
 * badge, and signing out returns them to Sign In rather than clearing an
 * account they never had.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onChangeVehicle: () -> Unit,
    onSignedOut: () -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val account by vm.account.collectAsStateWithLifecycle()
    val location by vm.riderLocation.collectAsStateWithLifecycle()
    val nickname by vm.riderNickname.collectAsStateWithLifecycle()
    val selection by vm.vehicle.collectAsStateWithLifecycle()
    val connection by vm.connectionState.collectAsStateWithLifecycle()
    val recentSignIns by vm.recentSignIns.collectAsStateWithLifecycle()

    var editing by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showPhotoViewer by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val photoVersion by vm.photoVersion.collectAsStateWithLifecycle()
    val pendingPhoto by vm.pendingPhoto.collectAsStateWithLifecycle()

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { vm.onPhotoPicked(it) }

    // Same standard dialog the rest of the app uses.
    val requestPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pickPhoto.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
    var confirmMigrate by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var confirmDelete by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    val connected = connection is ConnectionState.Connected

    ScreenBackdrop(photo = R.drawable.img_profile_cover, accent = c.cyan) {
        Column(
            Modifier
                .fillMaxHeight()
                .safeDrawingCentred()
                .contentWidth()
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val headerList = androidx.compose.foundation.lazy.rememberLazyListState()
            BackHeader(title = "Profile", onBack = onBack, lifted = headerList.canScrollBackward)

            LazyColumn(
                state = headerList,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Box(Modifier.enterRise(0)) {
                    ProfileCard(
                        name = account.name,
                        email = account.email,
                        location = location,
                        isGuest = account.isGuest,
                        method = account.method,
                        photoVersion = photoVersion,
                        // Big round photo: tap enlarges it; Change photo lives in that view.
                        onViewPhoto = { showPhotoViewer = true },
                    )
                    }
                }

                item {
                    SettingsGroup("Account", Icons.Filled.Person) {
                        SettingsLinkRow(
                            title = "Change vehicle",
                            subtitle = selection.vehicle?.name ?: "No vehicle selected",
                            onClick = onChangeVehicle,
                        )
                        SettingsDivider()
                        // Moving accounts keeps everything. Sign-out already
                        // leaves the rider name, nickname, city, vehicle, paint
                        // and photo on the device — only the account identity
                        // goes — and signing in to a fresh account now carries
                        // all of it up to the new one.
                        if (com.eshwar.rideconnectx.BuildConfig.CLOUD_ENABLED) SettingsLinkRow(
                            title = if (account.isGuest)
                                "Save to an account"
                            else
                                "Move to another account",
                            subtitle = if (account.isGuest)
                                "Sign in with Google or email — your profile and vehicle come with you"
                            else
                                "Sign in with a different email — your profile and vehicle come with you",
                            onClick = { confirmMigrate = true },
                        )
                        if (com.eshwar.rideconnectx.BuildConfig.CLOUD_ENABLED) SettingsDivider()
                        SettingsLinkRow(
                            title = "Delete local data",
                            subtitle = "Rides, notifications, service records, contacts, ride log and photo",
                            accent = c.red,
                            chevron = false,
                            onClick = { confirmDelete = true },
                        )
                    }
                }

                // So the rider can spot a sign-in that was not them.
                if (!account.isGuest && com.eshwar.rideconnectx.BuildConfig.CLOUD_ENABLED) item {
                    RecentSignIns(recentSignIns)
                }
            }

            // Pinned at the bottom, side by side.
            BottomActions {
                PrimaryButton(
                    label = stringResource(R.string.profile_edit),
                    onClick = { editing = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    label = if (account.isGuest) "Sign in" else stringResource(R.string.profile_sign_out),
                    // N137: a guest has nothing to sign out of; Sign in reuses the keep-profile sheet.
                    onClick = { if (account.isGuest) confirmMigrate = true else confirmSignOut = true },
                    secondary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showPhotoViewer) {
        PhotoViewer(
            name = account.name,
            hasPhoto = vm.hasPhoto,
            version = photoVersion,
            onChange = {
                showPhotoViewer = false
                requestPhotos.launch(AppPermissions.photos.manifest.toTypedArray())
            },
            onRemove = {
                showPhotoViewer = false
                vm.removePhoto()
            },
            onEdit = {
                showPhotoViewer = false
                vm.editPhoto()
            },
            onRotate = vm::rotatePhoto,
            onDismiss = { showPhotoViewer = false },
        )
    }

    // Framing happens before anything is written, same as Create Profile.
    pendingPhoto?.let { pending ->
        PhotoCropDialog(
            source = pending,
            decode = vm::decodeForCrop,
            onCancel = vm::cancelCrop,
            onConfirm = vm::confirmCrop,
        )
    }

    if (editing) {
        EditProfileSheet(
            initialName = account.name,
            initialLocation = location,
            initialNickname = nickname,
            validate = vm::validateName,
            onDismiss = { editing = false },
            onSave = { name, city, nick ->
                vm.saveProfile(name, city, nick)
                editing = false
            },
        )
    }

    if (confirmMigrate) {
        ConfirmSheet(
            title = if (account.isGuest) "Save to an account?" else "Move to another account?",
            body = "You'll be taken to sign in. Your rider name, nickname, city, " +
                "vehicle, paint and photo stay on this phone and are saved to " +
                "whichever account you sign in with next.\n\n" +
                // Guest rows move into the account since G4; an account's own
                // stay with it (AUD-6: this used to say "stay either way").
                if (account.isGuest)
                    "Your rides, service records and emergency contacts go with you."
                else
                    "Rides, service records and emergency contacts stay with this account.",
            confirmLabel = "Continue to sign in",
            destructive = false,
            onDismiss = { confirmMigrate = false },
            onConfirm = {
                confirmMigrate = false
                // The sheet above promises the profile follows the rider to the
                // next account, so this is the one path that keeps it.
                vm.signOut(keepLocalProfile = true, onDone = onSignedOut)
            },
        )
    }

    if (confirmSignOut) {
        ConfirmSheet(
            title = "Sign out?",
            body = if (account.isGuest)
                "Guest profiles live only on this phone, so signing out ends this " +
                    "session. Your rides and settings stay on the device."
            else
                "You will be returned to sign in. Your profile is safe in your " +
                    "account and comes back when you sign in again.",
            confirmLabel = "Sign out",
            destructive = false,
            onDismiss = { confirmSignOut = false },
            onConfirm = {
                confirmSignOut = false
                vm.signOut(onDone = onSignedOut)
            },
        )
    }

    if (confirmDelete) {
        ConfirmSheet(
            title = "Delete local data?",
            body = "Removes ride history, notifications, service records and " +
                "emergency contacts from this phone, and forgets the paired " +
                "vehicle. Your account is not deleted.",
            confirmLabel = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                vm.deleteLocalData { }
            },
        )
    }
}

/* ── Profile card ─────────────────────────────────────────────────── */

@Composable
private fun ProfileCard(
    name: String,
    email: String,
    location: String,
    isGuest: Boolean,
    method: LoginMethod,
    photoVersion: Int,
    onViewPhoto: () -> Unit,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(24.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardSurface(shape)
    ) {
        // Cover banner across the top of the card, with the avatar overlapping
        // its lower half. The gradient runs the photo into the card colour so
        // the two are one surface rather than a picture with a card under it.
        RcxPhotoFill(
            res = R.drawable.img_profile_cover,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .align(Alignment.TopCenter),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to c.card,
                        )
                    )
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 46.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The picture could only be set during Create Profile, so anyone who
            // skipped it had no route back to it. Tapping it opens the full-screen
            // view, which holds Change photo.
            RiderAvatar(
                name = name,
                size = 140.dp,
                version = photoVersion,
                modifier = Modifier
                    .clickable(
                        onClickLabel = stringResource(R.string.profile_photo_enlarge),
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClick = onViewPhoto,
                    ),
            )

            Spacer(Modifier.height(14.dp))

            Text(
                name.ifBlank { "Rider" },
                style = RcxType.Section.copy(fontSize = 19.sp),
                color = c.text,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(10.dp))
            com.eshwar.rideconnectx.presentation.components.ClusterGreetingPreview(name)

            if (location.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(location, style = RcxType.BodySmall.copy(fontSize = 13.sp), color = c.muted)
            }

            // Guests have no email; showing an empty line instead would look broken.
            if (email.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(email, style = RcxType.BodySmall.copy(fontSize = 13.sp), color = c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(12.dp))

            AccountBadge(isGuest = isGuest, method = method)
        }
    }
}

@Composable
private fun RecentSignIns(result: Result<List<com.eshwar.rideconnectx.domain.model.LoginEntry>>?) {
    SettingsGroup("Recent sign-ins", Icons.Filled.History) {
        val list = result?.getOrNull()
        when {
            result == null -> SettingsLinkRow(title = "Loading…", chevron = false)
            list == null -> SettingsLinkRow(
                title = "Couldn't load your sign-ins",
                subtitle = "Check your internet and open Profile again.",
                chevron = false,
            )
            list.isEmpty() -> SettingsLinkRow(title = "No sign-ins recorded yet", chevron = false)
            else -> {
                val format = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT) }
                list.forEachIndexed { i, e ->
                    if (i > 0) SettingsDivider()
                    val time = if (e.timeMillis > 0) format.format(java.util.Date(e.timeMillis)) else "Just now"
                    SettingsLinkRow(
                        title = e.device.ifBlank { "Unknown phone" },
                        subtitle = "$time · ${com.eshwar.rideconnectx.domain.model.LoginHistory.methodLabel(e.method)} · " +
                            "Android ${e.android} · app ${e.app}",
                        chevron = false,
                    )
                }
            }
        }
        SettingsDivider()
        SettingsLinkRow(
            title = "Don't recognise one?",
            subtitle = "Change your Google or email password, then sign out and back in. Help: rideconnectx.app@gmail.com",
            chevron = false,
        )
    }
}

@Composable
private fun AccountBadge(isGuest: Boolean, method: LoginMethod) {
    val c = Rcx.colors
    val (label, accent) = when {
        isGuest -> "GUEST MODE" to c.amber
        method == LoginMethod.GOOGLE -> "GOOGLE ACCOUNT" to c.blue
        method == LoginMethod.EMAIL || method == LoginMethod.EMAIL_LINK -> "EMAIL ACCOUNT" to c.blue
        else -> "SIGNED OUT" to c.muted
    }
    Text(
        label,
        style = RcxType.MonoTiny,
        color = accent,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.11f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/* ── Edit profile ─────────────────────────────────────────────────── */

/** Under a name-like field: the error when there is one, else the rule (N18). */
@Composable
internal fun NameFieldNote(error: String?, hint: String) {
    Text(
        error ?: hint,
        style = RcxType.BodySmall.copy(fontSize = 12.sp),
        color = if (error != null) Rcx.colors.red else Rcx.colors.muted,
        modifier = Modifier.padding(start = 4.dp, top = 5.dp).shake(rememberShakeKey(error)),
    )
}

/** The rider's picture full screen, with the things they can do to it (N15). */
@Composable
private fun PhotoViewer(
    name: String,
    hasPhoto: Boolean,
    version: Int,
    onChange: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onRotate: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingCentred(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 24.dp), contentAlignment = Alignment.CenterEnd) {
                Text(
                    stringResource(R.string.common_close),
                    style = RcxType.Body.copy(fontSize = 15.sp),
                    color = Color.White,
                    modifier = Modifier
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onDismiss)
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .padding(8.dp),
                )
            }
            val zoom = remember { Animatable(0.4f) }
            LaunchedEffect(Unit) { zoom.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 320f)) }
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                // As big as fits, up to 300 dp, so it also fits a landscape screen.
                val side = minOf(300.dp, maxWidth, maxHeight)
                Box(Modifier.graphicsLayer { scaleX = zoom.value; scaleY = zoom.value; alpha = (zoom.value - 0.4f) / 0.6f }) {
                    RiderAvatar(name = name, size = side, ring = false, version = version)
                }
            }
            // The buttons are pinned and stacked; on a short screen they scroll inside their own area.
            val maxBar = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.55f).dp
            BottomActions(Modifier.heightIn(max = maxBar).verticalScroll(rememberScrollState())) {
                PrimaryButton(
                    label = stringResource(R.string.profile_change_photo),
                    onClick = onChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Edit the picture already set (N17): re-frame it, or turn it.
                if (hasPhoto) {
                    PrimaryButton(label = stringResource(R.string.profile_photo_edit), onClick = onEdit, secondary = true, modifier = Modifier.fillMaxWidth())
                    PrimaryButton(label = stringResource(R.string.profile_photo_rotate), onClick = onRotate, secondary = true, modifier = Modifier.fillMaxWidth())
                    PrimaryButton(
                        label = stringResource(R.string.profile_remove_photo),
                        onClick = onRemove,
                        secondary = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheet(
    initialName: String,
    initialLocation: String,
    initialNickname: String,
    validate: (String) -> String?,
    onDismiss: () -> Unit,
    onSave: (name: String, location: String, nickname: String) -> Unit,
) {
    val c = Rcx.colors
    var name by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialName) }
    var location by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialLocation) }
    // The dashboard greets the rider by this; it could only be set once, in
    // Create Profile (AUD-7). Same rule as there.
    var nickname by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialNickname) }
    var showError by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // The last keystroke had a character the name rule ignores (N18).
    var nameIgnored by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var nicknameIgnored by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val error = validate(name)
    var cityOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // From the list, or exactly what was saved before; blank is allowed.
    val cityError = if (
        location.isBlank() || location.trim() == initialLocation.trim() || IndianCities.find(location) != null
    ) null else "Pick your city from the list"
    // Optional (N136): blank saves, anything typed still follows the nickname rule.
    val nicknameError = if (nickname.isBlank()) null else FieldRules.nicknameError(nickname)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetContainerColor(),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.sheetEnter()) {
        GlassSheetWindow()
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Edit profile", style = RcxType.Section.copy(fontSize = 17.sp), color = c.text)
            Spacer(Modifier.height(6.dp))
            Text(
                "Your name is what your scooter's cluster greets you by.",
                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                color = c.muted,
            )
            Spacer(Modifier.height(18.dp))

            ProfileField(
                value = name,
                onValueChange = {
                    val f = FieldRules.filterName(it, GuestNameRules.MAX)
                    name = f.text
                    nameIgnored = f.ignored
                },
                placeholder = "Your name",
            )
            com.eshwar.rideconnectx.presentation.components.ClusterGreetingPreview(name, Modifier.padding(top = 8.dp))
            NameFieldNote(
                error = if (nameIgnored) FieldRules.NAME_CHARS_MESSAGE else error.takeIf { showError },
                hint = FieldRules.nameHint(GuestNameRules.MIN, GuestNameRules.MAX),
            )

            Spacer(Modifier.height(14.dp))

            ProfileField(
                value = nickname,
                onValueChange = {
                    val f = FieldRules.filterName(it, FieldRules.NICKNAME_MAX)
                    nickname = f.text
                    nicknameIgnored = f.ignored
                },
                placeholder = "Nickname (optional)",
            )
            NameFieldNote(
                error = if (nicknameIgnored) FieldRules.NAME_CHARS_MESSAGE else nicknameError.takeIf { showError },
                hint = FieldRules.nameHint(FieldRules.NICKNAME_MIN, FieldRules.NICKNAME_MAX),
            )

            Spacer(Modifier.height(14.dp))

            // A searchable list rather than free text (N15). A city saved
            // before this, even one not on the list, is kept as it is.
            ProfileField(
                value = location,
                onValueChange = { location = it.take(FieldRules.CITY_MAX); cityOpen = true },
                placeholder = "City — type to search",
            )
            val cities = IndianCities.search(location, limit = 6)
                .filterNot { it.equals(location.trim(), ignoreCase = true) }
            if (cityOpen && location.isNotBlank() && cities.isNotEmpty()) {
                Column(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .innerFill()
                        .border(1.dp, c.border, RoundedCornerShape(14.dp)),
                ) {
                    cities.forEach { city ->
                        Text(
                            city,
                            style = RcxType.Body.copy(fontSize = 14.sp),
                            color = c.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { location = city; cityOpen = false }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                        )
                    }
                }
            }
            if (showError && cityError != null) {
                Text(
                    cityError,
                    style = RcxType.BodySmall.copy(fontSize = 12.sp),
                    color = c.red,
                    modifier = Modifier.padding(start = 4.dp, top = 5.dp),
                )
            }

            Spacer(Modifier.height(22.dp))

            PrimaryButton(
                label = "Save",
                onClick = {
                    if (error != null || nicknameError != null || cityError != null) showError = true
                    else onSave(name.trim(), IndianCities.find(location) ?: location.trim(), nickname.trim())
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    }
}

@Composable
private fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val c = Rcx.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // A floating label: it sits in the box while empty and moves to the border when filled, so the field never loses its name.
        label = { Text(placeholder, style = RcxType.BodySmall.copy(fontSize = 13.sp), color = c.muted, maxLines = 1) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = RcxType.Body.copy(fontSize = 15.sp, color = c.text),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.card2,
            unfocusedContainerColor = c.card2,
            focusedBorderColor = c.blue,
            unfocusedBorderColor = c.border,
            cursorColor = c.blue,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/* ── Helpers ──────────────────────────────────────────────────────── */

