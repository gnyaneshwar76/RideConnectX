package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.presentation.theme.rememberAmbientTransition
import com.eshwar.rideconnectx.presentation.theme.animateAmbient
import com.eshwar.rideconnectx.presentation.theme.sheetEnter
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.onClick
import com.eshwar.rideconnectx.presentation.theme.innerFill
import com.eshwar.rideconnectx.presentation.theme.sheetContainerColor
import com.eshwar.rideconnectx.presentation.theme.GlassSheetWindow
import com.eshwar.rideconnectx.presentation.theme.cardFill
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import com.eshwar.rideconnectx.presentation.theme.cardSurface
import com.eshwar.rideconnectx.presentation.theme.enterRise
import com.eshwar.rideconnectx.presentation.theme.shake
import com.eshwar.rideconnectx.presentation.theme.rememberShakeKey
import com.eshwar.rideconnectx.core.util.isLocationOn
import com.eshwar.rideconnectx.core.util.rememberSystemServices
import com.eshwar.rideconnectx.data.local.OwnerScope
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.data.local.db.EmergencyContactEntity
import com.eshwar.rideconnectx.core.util.rememberContactPicker
import com.eshwar.rideconnectx.data.repository.ContactError
import com.eshwar.rideconnectx.domain.model.FieldRules
import androidx.compose.ui.res.stringResource
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.components.RcxSwitch
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.components.RcxHeroBanner
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.viewmodel.LocationShare
import com.eshwar.rideconnectx.presentation.viewmodel.SafetyViewModel
import kotlinx.coroutines.launch

/**
 * Screen 19 — Safety.
 *
 * Built to be usable under stress: the emergency action is the largest thing on
 * the screen, contacts are one tap from a call, and everything it needs is
 * stored locally so it still works at the roadside with no signal.
 *
 * **Calls are placed through the dialer, not directly.** `ACTION_DIAL` opens the
 * phone app with the number filled in and the rider presses call — no
 * `CALL_PHONE` permission, and no possibility of the app dialling by accident.
 */
@Composable
fun SafetyScreen(
    onBack: () -> Unit,
    vm: SafetyViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Set when the rider says "No thanks" to turning location on; the next
    // tap goes straight to the Location page instead of re-asking.
    var locationDeclined by remember { mutableStateOf(false) }
    var onLocationDeclined by remember { mutableStateOf({}) }
    val systemServices = rememberSystemServices(onLocationDeclined = { onLocationDeclined() })

    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val primary by vm.primaryContact.collectAsStateWithLifecycle()
    val sosEnabled by vm.sosEnabled.collectAsStateWithLifecycle()
    val shareLocation by vm.shareLocation.collectAsStateWithLifecycle()
    val helmetReminder by vm.helmetReminder.collectAsStateWithLifecycle()
    val locating by vm.locating.collectAsStateWithLifecycle()

    val contactsFull by vm.contactsFull.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<EmergencyContactEntity?>(null) }
    var contactError by remember { mutableStateOf<ContactError?>(null) }
    var pendingDelete by remember { mutableStateOf<EmergencyContactEntity?>(null) }
    var showSos by remember { mutableStateOf(false) }

    // Picking from the phone lands straight in the edit sheet with both fields
    // filled, so the rider only has to confirm.
    val pickContact = rememberContactPicker { picked ->
        contactError = null
        editing = EmergencyContactEntity(
            ownerId = OwnerScope.DRAFT,
            name = picked.name,
            phone = picked.phone,
            normalizedPhone = "",
        )
    }

    // Android's own dialog first, exactly as Bluetooth and Location are asked
    // for. Whatever the rider answers, the picker still opens afterwards: it
    // works without the permission, and dead-ending someone who tapped "Don't
    // allow" would be worse than them simply not getting caller names on the
    // cluster, which is the only thing the permission adds.
    val requestContacts = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { pickContact() }

    fun dial(number: String) {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }

    /*
     * Sharing location, as one flow that never needs a second tap (rider,
     * 20 Sep: "after granting permission I had to tap share again"):
     * permission → location switched on → fix → share sheet. Each wait says
     * what it is waiting for, and the flow resumes by itself when it clears.
     */
    var sharePending by remember { mutableStateOf(false) }
    var shareWaitingFor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var permissionTick by remember { mutableIntStateOf(0) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    onLocationDeclined = {
        sharePending = false
        shareWaitingFor = null
        locationDeclined = true
        toast("Location is off, so your position can't be shared. Tap Share again to open location settings.")
    }

    val requestLocationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            permissionTick++
        } else if (result.values.any { it }) {
            // Approximate only: a point up to a few km out is worse than none
            // when someone is coming to find you (rider, 24 Sep). Don't share it.
            sharePending = false
            shareWaitingFor = null
            toast("Precise location is needed to share where you are. Turn on \"Use precise location\" for RideConnectX.")
            context.startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            )
        } else {
            sharePending = false
            shareWaitingFor = null
            Toast.makeText(
                context, context.getString(R.string.safety_location_denied), Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun sendShare() {
        sharePending = false
        shareWaitingFor = null
        vm.buildLocationMessage { result ->
            when (result) {
                is LocationShare.Ready -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, result.text)
                    }
                    context.startActivity(
                        Intent.createChooser(send, context.getString(R.string.safety_share_location))
                    )
                }
                // Permission and the location switch are both settled by now,
                // so this is a genuine failure to get a fix.
                LocationShare.Unavailable -> Toast.makeText(
                    context, context.getString(R.string.safety_no_fix), Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun startShare() {
        sharePending = true
        // Precise only. With approximate granted, asking for FINE again shows
        // Android's "Change to precise location" dialog.
        val hasPermission = vm.hasPreciseLocation
        when {
            !hasPermission -> {
                shareWaitingFor = R.string.safety_waiting_permission to R.string.safety_waiting_permission_sub
                requestLocationPermission.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
            !context.isLocationOn() -> {
                shareWaitingFor = R.string.safety_turn_on_location to R.string.safety_turn_on_location_sub
                // Play Services puts the switch in a dialog over this screen;
                // once declined, the Location page is the only way left.
                if (locationDeclined) systemServices.openLocationPage() else systemServices.openLocationSettings()
            }
            else -> sendShare()
        }
    }

    // Resume when the permission is granted.
    LaunchedEffect(permissionTick) {
        if (sharePending) startShare()
    }
    // Resume when location comes on. Polled, not read once: Android reports the
    // popup's "Turn on" a moment before the providers actually switch, so a
    // single check still saw "off" and the rider had to tap again (21 Sep).
    LaunchedEffect(shareWaitingFor) {
        if (shareWaitingFor?.first != R.string.safety_turn_on_location) return@LaunchedEffect
        repeat(LOCATION_WAIT_POLLS) {
            kotlinx.coroutines.delay(500)
            if (context.isLocationOn()) {
                locationDeclined = false
                startShare()
                return@LaunchedEffect
            }
        }
        // Gave up waiting: say so instead of leaving "turning on location…" up.
        onLocationDeclined()
    }

    ScreenBackdrop(photo = R.drawable.img_safety_hero, accent = c.red) {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val headerList = androidx.compose.foundation.lazy.rememberLazyListState()
            BackHeader(title = stringResource(R.string.safety_title), onBack = onBack, lifted = headerList.canScrollBackward)

            val refreshContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
            com.eshwar.rideconnectx.presentation.components.RideRefreshBox(onRefresh = {
                // Re-checks what is due, so a reminder that has come up appears now.
                dagger.hilt.android.EntryPointAccessors.fromApplication(
                    refreshContext, com.eshwar.rideconnectx.core.di.ServiceEntryPoint::class.java,
                ).serviceReminder().checkOnce()
            }) {
            LazyColumn(
                state = headerList,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Above the emergency card, not behind it. The SOS control is
                // the one thing on this screen that has to be unmistakable at a
                // glance, and nothing goes behind it that could compete with it.
                // Taller than the default band: this photograph is a single
                // object against a plain background, and at 132 dp the 16:9
                // crop takes the top and bottom off the helmet until it reads
                // as an abstract dark shape rather than as a helmet.
                item { Box(Modifier.enterRise(0)) { RcxHeroBanner(R.drawable.img_safety_hero, height = 170.dp) } }

                item {
                    Box(Modifier.enterRise(1)) {
                    EmergencyCard(
                        enabled = sosEnabled,
                        primary = primary,
                        onSos = { showSos = true },
                        onToggle = vm::setSosEnabled,
                    )
                    }
                }

                item {
                    Column(Modifier.enterRise(2)) {
                    SafetySectionHeader(stringResource(R.string.safety_sec_ride), Icons.Filled.Shield)
                    Spacer(Modifier.height(10.dp))
                    RideSafetyCard(
                        helmetReminder = helmetReminder,
                        onHelmetToggle = vm::setHelmetReminder,
                    )
                    }
                }

                item {
                    Column(Modifier.enterRise(3)) {
                    SafetySectionHeader(
                        stringResource(R.string.safety_sec_accident),
                        Icons.Filled.Warning,
                    )
                    Spacer(Modifier.height(10.dp))
                    AccidentInformationCard(onRead = vm::acknowledgeSafetyInfo)
                    }
                }

                item {
                    SafetySectionHeader(
                        stringResource(R.string.safety_sec_contacts),
                        Icons.Filled.Person,
                    )
                }

                if (contacts.isEmpty()) {
                    item { NoContactsCard() }
                } else {
                    items(contacts, key = { it.id }) { contact ->
                        Box(Modifier.animateItem().enterRise(4)) {
                        ContactRow(
                            contact = contact,
                            onCall = { dial(contact.phone) },
                            onEdit = { contactError = null; editing = contact },
                            onDelete = { pendingDelete = contact },
                            onMakePrimary = { vm.setPrimary(contact.id) },
                        )
                        }
                    }
                }

                item {
                    if (contactsFull) {
                        // Say why the button is gone rather than leaving a gap.
                        Text(
                            stringResource(R.string.safety_contacts_max, vm.contactLimit),
                            style = RcxType.BodySmall.copy(fontSize = 12.sp),
                            color = c.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Choosing from the phone is the path almost everyone
                            // wants; typing a number stays available beneath it.
                            PrimaryButton(
                                label = stringResource(R.string.safety_choose_contacts),
                                // Android asks first; see requestContacts above.
                                onClick = {
                                    requestContacts.launch(Manifest.permission.READ_CONTACTS)
                                },
                                icon = {
                                    Icon(
                                        Icons.Filled.Contacts, null,
                                        Modifier.size(18.dp), tint = androidx.compose.material3.LocalContentColor.current,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            PrimaryButton(
                                label = stringResource(R.string.safety_enter_manually),
                                onClick = {
                                    contactError = null
                                    editing = EmergencyContactEntity(
                                        ownerId = OwnerScope.DRAFT,
                                        name = "", phone = "", normalizedPhone = "",
                                    )
                                },
                                secondary = true,
                                icon = {
                                    Icon(
                                        Icons.Filled.Add, null,
                                        Modifier.size(18.dp), tint = c.blue,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                item {
                    ShareLocationToggle(enabled = shareLocation, onToggle = vm::setShareLocation)
                }
            }
            }
        }
    }

    // The fix is started as the sheet opens, not at the tap (N8).
    LaunchedEffect(showSos) { if (showSos && shareLocation) vm.prefetchLocation() }

    if (showSos) {
        SosSheet(
            primary = primary,
            shareLocation = shareLocation,
            locating = locating,
            precise = vm.hasPreciseLocation,
            // Precise/approximate can only be changed in system settings —
            // Android will not re-prompt once approximate has been granted.
            onFixPrecision = {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                )
            },
            onDismiss = { showSos = false },
            onCallContact = { number -> dial(number) },
            onCallServices = { dial(EMERGENCY_NUMBER) },
            waitingFor = shareWaitingFor,
            // One share at a time: repeat taps opened two or three share
            // sheets while the fix was still coming (rider, 24 Sep).
            onShareLocation = {
                if (sharePending || locating) toast("Getting your location — please wait…") else startShare()
            },
        )
    }

    editing?.let { contact ->
        ContactSheet(
            contact = contact,
            onDismiss = { editing = null },
            onSave = { name, phone ->
                scope.launch {
                    val error = vm.saveContact(contact.id, name, phone)
                    if (error == null) editing = null else contactError = error
                }
            },
            error = contactError,
            onErrorCleared = { contactError = null },
        )
    }

    pendingDelete?.let { contact ->
        ConfirmDeleteContactSheet(
            contact = contact,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                vm.delete(contact)
                pendingDelete = null
            },
        )
    }
}

/** India's single emergency number. Dialled, never called automatically. */
private const val EMERGENCY_NUMBER = "112"

/** Half-second checks for location coming on after the popup: 30 = 15 s. */
private const val LOCATION_WAIT_POLLS = 30

/* ── Emergency ────────────────────────────────────────────────────── */

@Composable
private fun EmergencyCard(
    enabled: Boolean,
    primary: EmergencyContactEntity?,
    onSos: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(24.dp)

    // SOS pulse — a slow breath, not a flash, so it reads as "live" rather
    // than as an alarm already going off.
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    // Held, not tapped: a ring fills round the button and SOS opens when it closes.
    val hold = remember { androidx.compose.animation.core.Animatable(0f) }
    val holdScope = androidx.compose.runtime.rememberCoroutineScope()
    val transition = rememberAmbientTransition("sosPulse")
    val pulse by transition.animateAmbient(
        initialValue = 1f,
        targetValue = if (enabled) 1.045f else 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "sosScale",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardFill()
            .border(1.dp, c.red.copy(alpha = 0.24f), shape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.safety_emergency), style = RcxType.MonoTiny, color = c.red)
        Spacer(Modifier.height(16.dp))

        Box(
            Modifier
                .size(148.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(c.red, Color(0xFF9E1220))
                    )
                )
                .drawWithContent {
                    drawContent()
                    if (hold.value > 0f) {
                        val w = 6.dp.toPx()
                        drawArc(
                            Color.White, -90f, 360f * hold.value, false,
                            topLeft = androidx.compose.ui.geometry.Offset(w, w),
                            size = androidx.compose.ui.geometry.Size(size.width - 2 * w, size.height - 2 * w),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        )
                    }
                }
                // A screen reader cannot hold: it gets a plain action.
                .semantics { onClick(label = "Send SOS") { if (enabled) onSos(); enabled } }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onPress = {
                        val filling = holdScope.launch {
                            hold.animateTo(1f, androidx.compose.animation.core.tween(SOS_HOLD_MS, easing = androidx.compose.animation.core.LinearEasing))
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onSos()
                        }
                        tryAwaitRelease()
                        filling.cancel()
                        holdScope.launch { hold.animateTo(0f, androidx.compose.animation.core.tween(200)) }
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.safety_sos),
                    style = RcxType.Headline.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    "HOLD FOR HELP",
                    style = RcxType.MonoTiny.copy(fontSize = 8.sp),
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            primary?.let { stringResource(R.string.safety_sos_reaches, it.name) }
                ?: stringResource(R.string.safety_sos_no_contact),
            style = RcxType.BodySmall.copy(fontSize = 12.sp),
            color = c.muted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .innerFill()
                .border(1.dp, c.border, RoundedCornerShape(14.dp))
                .toggleable(value = enabled, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onToggle)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.safety_sos_enabled),
                style = RcxType.BodySmall.copy(fontSize = 13.sp),
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            RcxSwitch(checked = enabled, onCheckedChange = onToggle, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SosSheet(
    primary: EmergencyContactEntity?,
    shareLocation: Boolean,
    locating: Boolean,
    precise: Boolean,
    onFixPrecision: () -> Unit,
    onDismiss: () -> Unit,
    onCallContact: (String) -> Unit,
    onCallServices: () -> Unit,
    onShareLocation: () -> Unit,
    /** Title and subtitle res ids while sharing waits on the rider, else null. */
    waitingFor: Pair<Int, Int>? = null,
) {
    val c = Rcx.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetContainerColor(),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.sheetEnter()) {
        GlassSheetWindow()
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.safety_get_help_now),
                style = RcxType.Section.copy(fontSize = 18.sp),
                color = c.text,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                // Said plainly, because a rider must not believe help was
                // summoned when it was not.
                stringResource(R.string.safety_dialer_note),
                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                color = c.muted,
            )
            Spacer(Modifier.height(18.dp))

            primary?.let { contact ->
                SosAction(
                    icon = Icons.Filled.Call,
                    title = stringResource(R.string.safety_call_name, contact.name),
                    subtitle = contact.phone,
                    accent = c.red,
                    onClick = { onCallContact(contact.phone) },
                )
                Spacer(Modifier.height(10.dp))
            }

            SosAction(
                icon = Icons.Filled.LocalHospital,
                title = stringResource(R.string.safety_call_emergency),
                subtitle = EMERGENCY_NUMBER,
                accent = c.red,
                onClick = onCallServices,
            )

            if (shareLocation) {
                Spacer(Modifier.height(10.dp))
                SosAction(
                    icon = Icons.Filled.LocationOn,
                    title = when {
                        locating -> stringResource(R.string.safety_getting_fix)
                        waitingFor != null -> stringResource(waitingFor.first)
                        else -> stringResource(R.string.safety_share_location)
                    },
                    subtitle = when {
                        locating -> stringResource(R.string.safety_waiting_gps)
                        waitingFor != null -> stringResource(waitingFor.second)
                        else -> stringResource(R.string.safety_sends_map_link)
                    },
                    accent = c.blue,
                    onClick = onShareLocation,
                )

                // Approximate location is accurate to about a city block, which
                // is no use to someone trying to reach you. Say so, and offer
                // the one place it can be changed.
                if (!precise) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.amber.copy(alpha = 0.07f))
                            .border(1.dp, c.amber.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onFixPrecision)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Warning, null,
                            Modifier.size(16.dp), tint = c.amber,
                        )
                        Text(
                            stringResource(R.string.safety_approx_location),
                            style = RcxType.BodySmall.copy(fontSize = 11.sp),
                            color = c.amber,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                label = stringResource(R.string.safety_close),
                onClick = onDismiss,
                secondary = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    }
}

@Composable
private fun SosAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = 0.063f))
            .border(1.dp, accent.copy(alpha = 0.2f), shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = accent)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = RcxType.Label.copy(fontSize = 15.sp), color = c.text)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = c.muted)
        }
    }
}

/* ── Ride safety ──────────────────────────────────────────────────── */

@Composable
private fun RideSafetyCard(
    helmetReminder: Boolean,
    onHelmetToggle: (Boolean) -> Unit,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardSurface(shape)
            .padding(16.dp),
    ) {
        Row(
            Modifier.toggleable(value = helmetReminder, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onHelmetToggle),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SafetyIconTile(Icons.Filled.SportsMotorsports, c.green)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.safety_helmet_reminder),
                    style = RcxType.Label.copy(fontSize = 15.sp),
                    color = c.text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.safety_helmet_desc),
                    style = RcxType.BodySmall.copy(fontSize = 12.sp),
                    color = c.muted,
                )
            }
            RcxSwitch(checked = helmetReminder, onCheckedChange = onHelmetToggle, modifier = Modifier.clearAndSetSemantics {})
        }

        Spacer(Modifier.height(14.dp))
        SafetyDivider()
        Spacer(Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            SafetyIconTile(Icons.Filled.Speed, c.amber)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.safety_speed_awareness),
                    style = RcxType.Label.copy(fontSize = 15.sp),
                    color = c.text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    // Honest about the limit: the Access cluster does not send
                    // speed over BLE, so nothing here can police it.
                    stringResource(R.string.safety_speed_desc),
                    style = RcxType.BodySmall.copy(fontSize = 12.sp),
                    color = c.muted,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SafetyDivider()
        Spacer(Modifier.height(14.dp))

        Text(stringResource(R.string.safety_ride_tips), style = RcxType.MonoTiny, color = c.muted)
        Spacer(Modifier.height(10.dp))
        RIDE_TIPS.forEach { tip ->
            Row(
                Modifier.padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.padding(top = 6.dp).size(5.dp).clip(CircleShape).background(c.blue)
                )
                Text(
                    stringResource(tip),
                    style = RcxType.BodySmall.copy(fontSize = 12.sp),
                    color = c.muted,
                )
            }
        }
    }
}

private val RIDE_TIPS = listOf(
    R.string.safety_tip_1,
    R.string.safety_tip_2,
    R.string.safety_tip_3,
    R.string.safety_tip_4,
)

/* ── Accident information ─────────────────────────────────────────── */

@Composable
private fun AccidentInformationCard(onRead: () -> Unit) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(20.dp)
    var expanded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardFill()
            .border(1.dp, c.amber.copy(alpha = 0.2f), shape)
            // `indication = null` kills the Material ripple. On a card this
            // large the ripple drew an expanding circle across the whole
            // surface at the same moment the card grew, and the two motions
            // fought each other. The size change is the feedback.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = androidx.compose.ui.semantics.Role.Button,
            ) {
                expanded = !expanded
                if (expanded) onRead()
            }
            // Grows and shrinks smoothly instead of snapping.
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
            )
            .padding(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SafetyIconTile(Icons.Filled.LocalHospital, c.amber)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.safety_emergency_instructions),
                    style = RcxType.Label.copy(fontSize = 15.sp),
                    color = c.text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (expanded)
                        stringResource(R.string.safety_tap_collapse)
                    else
                        stringResource(R.string.safety_first_aid_open),
                    style = RcxType.BodySmall.copy(fontSize = 12.sp),
                    color = c.muted,
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(14.dp))
            SafetyDivider()
            Spacer(Modifier.height(14.dp))
            ACCIDENT_STEPS.forEachIndexed { index, step ->
                Row(
                    Modifier.padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(c.amber.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${index + 1}",
                            style = RcxType.Mono.copy(fontSize = 10.sp),
                            color = c.amber,
                        )
                    }
                    Text(
                        stringResource(step),
                        style = RcxType.BodySmall.copy(fontSize = 12.sp),
                        color = c.muted,
                    )
                }
            }
            Text(
                stringResource(R.string.safety_medical_disclaimer, EMERGENCY_NUMBER),
                style = RcxType.BodySmall.copy(fontSize = 11.sp),
                color = c.muted.copy(alpha = 0.7f),
            )
        }
    }
}

private val ACCIDENT_STEPS = listOf(
    R.string.safety_aid_1,
    R.string.safety_aid_2,
    R.string.safety_aid_3,
    R.string.safety_aid_4,
    R.string.safety_aid_5,
    R.string.safety_aid_6,
    R.string.safety_aid_7,
)

/* ── Contacts ─────────────────────────────────────────────────────── */

@Composable
private fun ContactRow(
    contact: EmergencyContactEntity,
    onCall: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMakePrimary: () -> Unit,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardFill()
            .border(1.dp, if (contact.isPrimary) c.red.copy(alpha = 0.24f) else c.border, shape)
            .padding(14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(c.blue.copy(alpha = 0.094f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, null, Modifier.size(18.dp), tint = c.blue)
            }

            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(contact.name, style = RcxType.Label.copy(fontSize = 15.sp), color = c.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (contact.isPrimary) {
                        Text(
                            stringResource(R.string.safety_sos),
                            style = RcxType.MonoTiny.copy(fontSize = 8.sp),
                            color = c.red,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(c.red.copy(alpha = 0.11f))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(contact.phone, style = RcxType.Mono.copy(fontSize = 12.sp), color = c.muted)
            }

            // Call is the big target — it is the reason this row exists.
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(c.green.copy(alpha = 0.11f))
                    .border(1.dp, c.green.copy(alpha = 0.24f), CircleShape)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onCall),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Call,
                    stringResource(R.string.safety_call_name, contact.name),
                    Modifier.size(19.dp),
                    tint = c.green,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!contact.isPrimary) {
                SafetyGhostAction(
                    stringResource(R.string.safety_make_sos_contact),
                    Icons.Filled.Star, c.amber, onMakePrimary,
                )
            }
            SafetyGhostAction(
                stringResource(R.string.safety_edit), Icons.Filled.Edit, c.blue, onEdit,
            )
            SafetyGhostAction(
                stringResource(R.string.safety_delete), Icons.Filled.Delete, c.red, onDelete,
            )
        }
    }
}

@Composable
private fun NoContactsCard() {
    val c = Rcx.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardFill()
            .border(1.dp, c.amber.copy(alpha = 0.2f), shape)
            .padding(vertical = 28.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(c.amber.copy(alpha = 0.09f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Warning, null, Modifier.size(21.dp), tint = c.amber)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.safety_no_contact_title),
            style = RcxType.Label.copy(fontSize = 15.sp),
            color = c.text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.safety_no_contact_desc),
            style = RcxType.BodySmall.copy(fontSize = 12.sp),
            color = c.muted,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactSheet(
    contact: EmergencyContactEntity,
    error: ContactError?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String) -> Unit,
    onErrorCleared: () -> Unit,
) {
    val c = Rcx.colors
    var name by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(contact.name) }
    var nameIgnored by remember { mutableStateOf(false) }
    var phone by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(contact.phone) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetContainerColor(),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.sheetEnter()) {
        GlassSheetWindow()
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                if (contact.id == 0L)
                    stringResource(R.string.safety_add_contact)
                else
                    stringResource(R.string.safety_edit_contact),
                style = RcxType.Section.copy(fontSize = 17.sp),
                color = c.text,
            )
            Spacer(Modifier.height(18.dp))

            SafetyFieldLabel(stringResource(R.string.safety_name))
            SafetyField(
                value = name,
                onValueChange = {
                    val f = FieldRules.filterName(it, FieldRules.CONTACT_NAME_MAX)
                    name = f.text
                    nameIgnored = f.ignored
                    onErrorCleared()
                },
                placeholder = stringResource(R.string.safety_name_placeholder),
                capitalization = KeyboardCapitalization.Words,
            )
            // Characters outside the rule are ignored as typed, and it says so (N18).
            NameFieldNote(
                error = when {
                    nameIgnored -> FieldRules.NAME_CHARS_MESSAGE
                    error is ContactError.NameMissing -> stringResource(R.string.safety_name_error)
                    else -> null
                },
                hint = FieldRules.nameHint(FieldRules.CONTACT_NAME_MIN, FieldRules.CONTACT_NAME_MAX),
            )

            Spacer(Modifier.height(14.dp))

            SafetyFieldLabel(stringResource(R.string.safety_phone))
            SafetyField(
                value = phone,
                onValueChange = { input ->
                    // Digits, with '+' only at the start (N16).
                    phone = FieldRules.phoneInput(input)
                    onErrorCleared()
                },
                placeholder = stringResource(R.string.safety_phone_placeholder),
                numeric = true,
            )
            when (error) {
                is ContactError.PhoneInvalid ->
                    SafetyFieldError(stringResource(R.string.safety_phone_error))
                is ContactError.Duplicate ->
                    SafetyFieldError(
                        stringResource(R.string.safety_duplicate_error, error.existingName)
                    )
                is ContactError.LimitReached ->
                    SafetyFieldError(
                        stringResource(R.string.safety_limit_error, error.limit)
                    )
                else -> Unit
            }

            Spacer(Modifier.height(22.dp))

            PrimaryButton(
                label = stringResource(R.string.safety_save_contact),
                onClick = { onSave(name, phone) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmDeleteContactSheet(
    contact: EmergencyContactEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val c = Rcx.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = sheetContainerColor(),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.sheetEnter()) {
        GlassSheetWindow()
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                stringResource(R.string.safety_remove_q, contact.name),
                style = RcxType.Section.copy(fontSize = 17.sp),
                color = c.text,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (contact.isPrimary)
                    stringResource(R.string.safety_remove_primary_warn)
                else
                    stringResource(R.string.safety_remove_desc),
                style = RcxType.BodySmall.copy(fontSize = 13.sp),
                color = c.muted,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    stringResource(R.string.safety_keep),
                    onDismiss,
                    secondary = true,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.red.copy(alpha = 0.094f))
                        .border(1.5.dp, c.red.copy(alpha = 0.31f), RoundedCornerShape(16.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onConfirm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.safety_remove),
                        style = RcxType.Button,
                        color = c.red,
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun ShareLocationToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardSurface(shape)
            .toggleable(value = enabled, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onToggle)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SafetyIconTile(Icons.Filled.LocationOn, c.blue)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.safety_offer_share),
                style = RcxType.Label.copy(fontSize = 15.sp),
                color = c.text,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.safety_offer_share_desc),
                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                color = c.muted,
            )
        }
        RcxSwitch(checked = enabled, onCheckedChange = onToggle, modifier = Modifier.clearAndSetSemantics {})
    }
}

/* ── Small shared pieces ──────────────────────────────────────────── */

@Composable
private fun SafetyIconTile(icon: ImageVector, accent: Color) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.094f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = accent)
    }
}

@Composable
private fun SafetyDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Rcx.colors.border))
}

@Composable
private fun SafetyGhostAction(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.063f))
            .border(1.dp, accent.copy(alpha = 0.157f), shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(13.dp), tint = accent)
        Text(label, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = accent)
    }
}

@Composable
private fun SafetySectionHeader(title: String, icon: ImageVector) {
    val c = Rcx.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = c.blue)
        Text(title, style = RcxType.Section.copy(fontSize = 16.sp), color = c.text)
    }
}

@Composable
private fun SafetyFieldLabel(text: String) {
    Text(
        text,
        style = RcxType.MonoTiny,
        color = Rcx.colors.muted,
        modifier = Modifier.padding(bottom = 8.dp, start = 2.dp),
    )
}

@Composable
private fun SafetyFieldError(text: String) {
    Text(
        text,
        style = RcxType.BodySmall.copy(fontSize = 12.sp),
        color = Rcx.colors.red,
        modifier = Modifier.padding(start = 4.dp, top = 5.dp).shake(rememberShakeKey(text)),
    )
}

@Composable
private fun SafetyField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    numeric: Boolean = false,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    val c = Rcx.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, style = RcxType.Body.copy(fontSize = 14.sp), color = c.muted) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = RcxType.Body.copy(fontSize = 15.sp, color = c.text),
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = if (numeric) KeyboardType.Phone else KeyboardType.Text,
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

/** How long the SOS button must be held. Long enough not to fire in a pocket, short enough in a panic. */
private const val SOS_HOLD_MS = 800
