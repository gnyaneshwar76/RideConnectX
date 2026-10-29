package com.eshwar.rideconnectx.presentation.screens

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.defaultMinSize
import com.eshwar.rideconnectx.presentation.theme.rememberAmbientTransition
import com.eshwar.rideconnectx.presentation.theme.animateAmbient
import com.eshwar.rideconnectx.presentation.theme.sheetEnter
import com.eshwar.rideconnectx.presentation.theme.RcxMotion
import androidx.compose.ui.graphics.graphicsLayer
import com.eshwar.rideconnectx.presentation.theme.innerFill
import com.eshwar.rideconnectx.presentation.theme.sheetContainerColor
import com.eshwar.rideconnectx.presentation.theme.GlassSheetWindow
import com.eshwar.rideconnectx.presentation.theme.cardFill
import com.eshwar.rideconnectx.presentation.theme.shake
import com.eshwar.rideconnectx.presentation.theme.rememberShakeKey
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.domain.model.Vehicle
import com.eshwar.rideconnectx.domain.model.VehicleCategory
import com.eshwar.rideconnectx.domain.model.VehicleColor
import com.eshwar.rideconnectx.core.util.AppPermissions
import com.eshwar.rideconnectx.domain.model.GuestNameRules
import com.eshwar.rideconnectx.domain.model.FieldRules
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.components.VehicleArtwork
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.viewmodel.NICKNAME_MAX
import com.eshwar.rideconnectx.presentation.viewmodel.ProfileFormMode
import com.eshwar.rideconnectx.presentation.viewmodel.VehiclePicker
import com.eshwar.rideconnectx.presentation.viewmodel.VehicleViewModel

/**
 * Create Profile — the one-time step between sign-in and the dashboard.
 *
 * Order follows the reference flow: who you are, where you ride, what you ride,
 * what colour it is, then consent. The dashboard is meaningless without the
 * vehicle, which is why this sits ahead of it rather than in Settings.
 *
 * Type, model and colour open as sheets rather than pushed screens so the rider
 * keeps their place in the form and can see the choice land.
 */
@Composable
fun CreateProfileScreen(
    onContinue: () -> Unit,
    onLegal: () -> Unit = {},
    mode: ProfileFormMode = ProfileFormMode.CREATE,
    onBack: (() -> Unit)? = null,
    /** Back → Exit mid-setup: undo the unfinished setup, then leave. */
    onExitSetup: () -> Unit = {},
    vm: VehicleViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val ui by vm.ui.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val creating = mode == ProfileFormMode.CREATE

    // Back mid-setup used to drop the rider out of the app without a word. It
    // asks first, and leaving undoes the unfinished setup (rider, 26 Sep).
    var confirmLeave by remember { mutableStateOf(false) }
    BackHandler(enabled = creating) { confirmLeave = true }
    if (confirmLeave) {
        ConfirmSheet(
            title = "Leave setup?",
            body = "Your profile isn't saved. You'll be signed out, and next time " +
                "you open RideConnectX you'll start again.",
            confirmLabel = "Exit",
            onDismiss = { confirmLeave = false },
            onConfirm = {
                confirmLeave = false
                onExitSetup()
            },
        )
    }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { vm.onPhotoPicked(it) }

    // Android's own dialog first, the same way Bluetooth and Location are asked
    // for — on 14+ that is the three-way Allow all / Select photos / Don't allow
    // choice, which is what the pair of media permissions in the manifest buys.
    // The picker then opens whatever the answer was: refusing should narrow what
    // the app can see, not stop the rider setting a picture.
    val requestPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pickPhoto.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (c.isDark) listOf(Color(0xFF070D1B), Color(0xFF0B1120))
                    else listOf(Color(0xFFEFF4FF), Color(0xFFF7F9FC))
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            if (!creating && onBack != null) {
                BackHeader(title = "Change Vehicle", onBack = onBack)
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(8.dp))

                if (creating) {
                    Text("Create Profile", style = RcxType.Wordmark.copy(fontSize = 24.sp), color = c.text)
                    Text(
                        "Your name is what your vehicle greets you with.",
                        style = RcxType.BodySmall.copy(fontSize = 13.sp),
                        color = c.muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    Spacer(Modifier.height(22.dp))

                    ProfileAvatar(
                        name = ui.riderName,
                        accent = selection.vehicle?.accent ?: c.blue,
                        photoUri = ui.photoUri,
                        photoVersion = ui.photoVersion,
                        onPick = { requestPhotos.launch(AppPermissions.photos.manifest.toTypedArray()) },
                        onRemove = vm::removePhoto,
                    )

                    Spacer(Modifier.height(24.dp))

                    SectionLabel("Rider's Name")
                    RcxField(
                        value = ui.riderName,
                        onValueChange = vm::onRiderNameChange,
                        placeholder = "e.g. Alex Johnson",
                        error = ui.nameError,
                        hint = "Your full name, as the cluster greets you · " +
                            FieldRules.nameHint(GuestNameRules.MIN, GuestNameRules.MAX),
                    )
                    com.eshwar.rideconnectx.presentation.components.ClusterGreetingPreview(
                        ui.riderName, Modifier.padding(top = 8.dp),
                    )

                    Spacer(Modifier.height(18.dp))

                    // Separate from the full name on purpose: the dashboard
                    // header has room for a short word beside the avatar and
                    // the connection pill, and "Gnyaneshwar .P" already
                    // overflows it. Not auto-filled from the account, because
                    // the account name is precisely the thing that does not fit.
                    SectionLabel("Nickname")
                    RcxField(
                        value = ui.nickname,
                        onValueChange = vm::onNicknameChange,
                        placeholder = "e.g. Alex",
                        error = ui.nicknameError,
                        hint = "Required · shown on your dashboard · " +
                            FieldRules.nameHint(FieldRules.NICKNAME_MIN, FieldRules.NICKNAME_MAX),
                        highlight = ui.nicknameNudge,
                    )

                    Spacer(Modifier.height(18.dp))

                    SectionLabel("Location")
                    RcxField(
                        value = ui.location,
                        onValueChange = vm::onLocationChange,
                        placeholder = "Your city",
                        trailing = {
                            LocateButton(
                                busy = ui.isLocating,
                                // The coach mark only nags until the rider engages
                                // with the form at all.
                                hint = ui.location.isBlank() && !ui.isLocating,
                                onClick = vm::detectLocation,
                            )
                        },
                    )

                    ui.message?.let {
                        Text(
                            it,
                            style = RcxType.BodySmall.copy(fontSize = 12.sp),
                            color = c.amber,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    Spacer(Modifier.height(26.dp))
                } else {
                    Text(
                        "Pick the machine this app is paired to. Your name, city " +
                            "and everything else stay as they are.",
                        style = RcxType.BodySmall.copy(fontSize = 13.sp),
                        color = c.muted,
                    )
                    Spacer(Modifier.height(20.dp))
                }

                SectionLabel("Your Vehicle")

                ChooserRow(
                    label = "Vehicle type",
                    value = if (selection.vehicle != null) ui.category.label.dropLast(1) else null,
                    placeholder = "Scooter or motorcycle",
                    onClick = { vm.openPicker(VehiclePicker.Type) },
                )

                Spacer(Modifier.height(10.dp))

                ChooserRow(
                    label = "Vehicle model",
                    value = selection.vehicle?.name,
                    placeholder = "Choose your model",
                    onClick = { vm.openPicker(VehiclePicker.Model) },
                )

                // Paint only exists once a model does, so the whole block
                // appears together rather than as an empty row.
                AnimatedVisibility(
                    visible = selection.vehicle != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    selection.vehicle?.let { vehicle ->
                        VehiclePreview(
                            vehicle = vehicle,
                            color = selection.color,
                            onPick = vm::pickColorInline,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Consent belongs to setting the account up, not to swapping a
                // scooter. Asking again on every change is what made the app
                // look like it had lost the profile.
                Spacer(Modifier.height(12.dp))
            }

            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 20.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Pinned above the button, not inside the scroll area, so it is
                // always visible and tappable on small screens and large fonts.
                if (creating) {
                    TermsRow(
                        checked = ui.termsAccepted,
                        onCheckedChange = vm::onTermsChange,
                        onLegal = onLegal,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                PrimaryButton(
                    label = if (creating) "Continue" else "Save vehicle",
                    onClick = {
                        if (vm.canContinue(mode)) vm.saveProfile(mode, onContinue)
                        // Only the nickname is left — say so where it matters,
                        // on the field itself, rather than in a toast.
                        else vm.nudgeNickname()
                    },
                    enabled = vm.canContinueIgnoringNickname(mode),
                    modifier = Modifier.fillMaxWidth(),
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward, null,
                            Modifier.size(17.dp), tint = Color.White,
                        )
                    },
                )
            }
        }
    }

    // Framing happens before anything is written to disk.
    ui.pendingPhoto?.let { pending ->
        PhotoCropDialog(
            source = pending,
            decode = vm::decodeForCrop,
            onCancel = vm::cancelCrop,
            onConfirm = vm::confirmCrop,
        )
    }

    when (ui.picker) {
        VehiclePicker.Type -> TypeSheet(
            onDismiss = vm::closePicker,
            onPick = vm::selectCategory,
        )

        VehiclePicker.Model -> ModelSheet(
            title = ui.category.label,
            vehicles = ui.results,
            selectedId = selection.vehicle?.id,
            onDismiss = vm::closePicker,
            onPick = vm::selectVehicle,
        )

        VehiclePicker.Color -> selection.vehicle?.let { vehicle ->
            ColorSheet(
                vehicle = vehicle,
                selectedId = selection.color?.id,
                onDismiss = vm::closePicker,
                onPick = vm::selectColor,
            )
        }

        VehiclePicker.None -> Unit
    }
}

// ── Pieces ─────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = RcxType.Label.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        color = Rcx.colors.muted,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/**
 * The rider's avatar: their photo if they've set one, otherwise their initial.
 *
 * The stored file is already a centred square (see ProfilePhotoStore), so the
 * circular clip here only rounds it — it never lops the subject off, whichever
 * way the original photo was held.
 */
@Composable
private fun ProfileAvatar(
    name: String,
    accent: Color,
    photoUri: Uri?,
    photoVersion: Int,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    val c = Rcx.colors
    val initial = name.trim().firstOrNull()?.uppercase()
    val bitmap = rememberLocalImage(photoUri, photoVersion)

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.20f), accent.copy(alpha = 0.04f))
                        )
                    )
                    .border(1.5.dp, accent.copy(alpha = 0.35f), CircleShape)
                    .clickable(onClick = onPick),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    bitmap != null -> Image(
                        bitmap = bitmap,
                        contentDescription = "Profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                    initial != null -> Text(
                        initial,
                        style = RcxType.Wordmark.copy(fontSize = 36.sp),
                        color = accent,
                    )
                    else -> Icon(Icons.Default.Person, null, Modifier.size(38.dp), tint = c.muted)
                }
            }

            // Camera badge doubles as the remove control once a photo is set.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (bitmap != null) c.red else c.blue)
                    .border(2.dp, c.bg, CircleShape)
                    .clickable { if (bitmap != null) onRemove() else onPick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (bitmap != null) Icons.Default.Close else Icons.Default.PhotoCamera,
                    contentDescription = if (bitmap != null) "Remove photo" else "Add photo",
                    modifier = Modifier.size(15.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

/**
 * Decodes a local image file off the main thread.
 *
 * The project has no image-loading library, and pulling one in for a single
 * avatar would be heavier than the problem. The file is already downscaled to
 * 1080px square when it is saved, so a plain decode is cheap.
 */
@Composable
private fun rememberLocalImage(uri: Uri?, version: Int): ImageBitmap? {
    val context = LocalContext.current
    var image by remember(uri, version) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(uri, version) {
        image = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    return image
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RcxField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    error: String? = null,
    hint: String? = null,
    highlight: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Rcx.colors

    // The Samsung Settings search behaviour the rider asked for: when a field
    // is what is blocking the button, it lights up for a beat and settles. A
    // pulse rather than a steady colour, because a field that stays lit reads
    // as an error even after it has been filled in.
    val glow by animateFloatAsState(
        targetValue = if (highlight) 1f else 0f,
        animationSpec = tween(durationMillis = if (highlight) 220 else 900),
        label = "fieldGlow",
    )

    Column(Modifier.shake(rememberShakeKey(error))) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, style = RcxType.Body.copy(fontSize = 14.sp), color = c.muted) },
            singleLine = true,
            isError = error != null,
            trailingIcon = trailing,
            shape = RoundedCornerShape(14.dp),
            textStyle = RcxType.Body.copy(fontSize = 15.sp, color = c.text),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = c.card,
                unfocusedContainerColor = lerp(c.card, c.blue.copy(alpha = 0.22f), glow),
                focusedBorderColor = c.blue,
                unfocusedBorderColor = lerp(c.border, c.blue, glow),
                errorBorderColor = c.red,
                cursorColor = c.blue,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            error != null -> Text(
                error,
                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                color = c.red,
                modifier = Modifier.padding(start = 4.dp, top = 5.dp),
            )
            hint != null -> Text(
                hint,
                style = RcxType.BodySmall.copy(fontSize = 11.sp),
                color = lerp(c.muted, c.blue, glow),
                modifier = Modifier.padding(start = 4.dp, top = 5.dp),
            )
        }
    }
}

/** Crosshair that fills the city from GPS, with a first-run pulse. */
@Composable
private fun LocateButton(busy: Boolean, hint: Boolean, onClick: () -> Unit) {
    val c = Rcx.colors
    // Only ticks while the hint pulse is on screen; read in graphicsLayer so a tick only redraws.
    val transition = if (hint && !busy) rememberAmbientTransition("locate") else null
    val pulse = transition.animateAmbient(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(
        Modifier
            .padding(end = 6.dp)
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.blue.copy(alpha = 0.10f))
            .clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.blue)
        } else {
            Icon(
                Icons.Default.MyLocation,
                contentDescription = "Detect my city",
                modifier = Modifier
                    .size(19.dp)
                    .then(if (hint) Modifier.graphicsLayer { alpha = pulse.value; clip = true } else Modifier),
                tint = c.blue,
            )
        }
    }
}

/** A tappable row that reads as a field until it has a value. */
@Composable
private fun ChooserRow(
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(14.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardFill()
            .border(1.dp, if (value != null) c.blue.copy(alpha = 0.30f) else c.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = RcxType.BodySmall.copy(fontSize = 11.sp), color = c.muted)
            Text(
                value ?: placeholder,
                style = RcxType.Label.copy(fontSize = 15.sp),
                color = if (value != null) c.text else c.muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
            Modifier.size(20.dp), tint = c.muted,
        )
    }
}

/** The chosen machine, rendered in the chosen paint, with the swatch row. */
@Composable
private fun VehiclePreview(
    vehicle: Vehicle,
    color: VehicleColor?,
    onPick: (VehicleColor) -> Unit,
) {
    val c = Rcx.colors
    val body = color?.primary ?: vehicle.accent

    Column(Modifier.padding(top = 14.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(vehicle.accent.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
                .border(1.dp, c.border, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // The paint changes by dissolving, not by a jump cut.
            androidx.compose.animation.Crossfade(color, animationSpec = androidx.compose.animation.core.tween(320), label = "paint") { paint ->
                VehicleArtwork(
                    vehicle = vehicle,
                    body = paint?.primary ?: vehicle.accent,
                    outline = c.text.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(horizontal = 18.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            color?.name ?: "Choose a colour",
            style = RcxType.Label.copy(fontSize = 13.sp),
            color = if (color != null) c.text else c.muted,
        )

        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vehicle.colors, key = { it.id }) { swatch ->
                Swatch(
                    color = swatch,
                    selected = swatch.id == color?.id,
                    onClick = { onPick(swatch) },
                )
            }
        }
    }
}

/** Dual-tone paints show as a split circle, matching how they are sold. */
@Composable
private fun Swatch(color: VehicleColor, selected: Boolean, onClick: () -> Unit) {
    val c = Rcx.colors
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (color.isDualTone) {
                    Brush.horizontalGradient(
                        0f to color.primary,
                        0.5f to color.primary,
                        0.5f to color.secondary!!,
                        1f to color.secondary,
                    )
                } else {
                    Brush.linearGradient(listOf(color.primary, color.primary))
                }
            )
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) c.blue else c.border,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check, null,
                Modifier.size(18.dp),
                // Pale paints need a dark tick and vice versa.
                tint = if (color.primary.luminanceIsLight()) Color.Black else Color.White,
            )
        }
    }
}

private fun Color.luminanceIsLight(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) > 0.6f

@Composable
private fun TermsRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onLegal: () -> Unit,
) {
    val c = Rcx.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = onCheckedChange)
            .defaultMinSize(minHeight = 48.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (checked) c.blue else Color.Transparent)
                .border(1.5.dp, if (checked) c.blue else c.border, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Default.Check, null, Modifier.size(15.dp), tint = Color.White)
        }

        Column(Modifier.weight(1f)) {
            Text(
                "I agree to the Terms & Conditions and Privacy Policy",
                style = RcxType.BodySmall.copy(fontSize = 13.sp),
                color = c.text,
            )
            // One link to one page holding both documents. Ticking the box is
            // the agreement; reading is offered, never forced.
            Text(
                "Read them",
                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                color = c.blue,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(onClick = onLegal),
            )
        }
    }
}

// ── Sheets ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSheet(onDismiss: () -> Unit, onPick: (VehicleCategory) -> Unit) {
    val c = Rcx.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetContainerColor(),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.sheetEnter()) {
        GlassSheetWindow()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Choose your vehicle type", style = RcxType.Label.copy(fontSize = 16.sp), color = c.text)
            Spacer(Modifier.height(16.dp))

            VehicleCategory.entries.forEach { category ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .innerFill()
                        .border(1.dp, c.border, RoundedCornerShape(16.dp))
                        .clickable { onPick(category) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        category.label.dropLast(1),
                        style = RcxType.Label.copy(fontSize = 15.sp),
                        color = c.text,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                        Modifier.size(20.dp), tint = c.muted,
                    )
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(
    title: String,
    vehicles: List<Vehicle>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onPick: (Vehicle) -> Unit,
) {
    val c = Rcx.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetContainerColor(),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.sheetEnter()) {
        GlassSheetWindow()
        Column(Modifier.fillMaxWidth()) {
            Text(
                title,
                style = RcxType.Label.copy(fontSize = 16.sp),
                color = c.text,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(14.dp))

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 24.dp),
            ) {
                items(vehicles, key = { it.id }) { vehicle ->
                    ModelCard(
                        vehicle = vehicle,
                        selected = vehicle.id == selectedId,
                        dimmed = selectedId != null && vehicle.id != selectedId,
                        onClick = { onPick(vehicle) },
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun ModelCard(vehicle: Vehicle, selected: Boolean, dimmed: Boolean, onClick: () -> Unit) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(18.dp)
    // The chosen model holds its size; the rest step back and dim.
    val emphasis by androidx.compose.animation.core.animateFloatAsState(
        if (dimmed) 0f else 1f, RcxMotion.snappy(), label = "modelEmphasis",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = 0.55f + 0.45f * emphasis
                scaleX = 0.97f + 0.03f * emphasis
                scaleY = 0.97f + 0.03f * emphasis
            }
            .clip(shape)
            .innerFill()
            .border(1.dp, if (selected) c.blue else c.border, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(width = 84.dp, height = 54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(vehicle.accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            VehicleArtwork(
                vehicle = vehicle,
                body = vehicle.colors.first().primary,
                outline = c.text.copy(alpha = 0.5f),
                modifier = Modifier.size(width = 76.dp, height = 46.dp),
            )
        }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(vehicle.name, style = RcxType.Label.copy(fontSize = 15.sp), color = c.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (vehicle.tag.isNotBlank()) {
                    Text(
                        vehicle.tag,
                        style = RcxType.BodySmall.copy(fontSize = 10.sp),
                        color = vehicle.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(vehicle.accent.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                vehicle.subtitle,
                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                color = c.muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        if (selected) {
            Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = c.blue)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSheet(
    vehicle: Vehicle,
    selectedId: String?,
    onDismiss: () -> Unit,
    onPick: (VehicleColor) -> Unit,
) {
    val c = Rcx.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetContainerColor(),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.sheetEnter()) {
        GlassSheetWindow()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("${vehicle.name} — colour", style = RcxType.Label.copy(fontSize = 16.sp), color = c.text)
            Spacer(Modifier.height(16.dp))

            vehicle.colors.forEach { swatch ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .innerFill()
                        .border(
                            1.dp,
                            if (swatch.id == selectedId) c.blue else c.border,
                            RoundedCornerShape(16.dp),
                        )
                        .clickable { onPick(swatch) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Swatch(swatch, selected = swatch.id == selectedId, onClick = { onPick(swatch) })
                    Text(
                        swatch.name,
                        style = RcxType.Body.copy(fontSize = 14.sp),
                        color = c.text,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    }
}
