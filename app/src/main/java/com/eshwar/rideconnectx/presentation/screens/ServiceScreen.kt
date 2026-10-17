package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.presentation.theme.sheetEnter
import com.eshwar.rideconnectx.presentation.theme.edgeShrink
import androidx.compose.ui.draw.drawBehind
import com.eshwar.rideconnectx.presentation.theme.floaty
import com.eshwar.rideconnectx.presentation.theme.innerFill
import com.eshwar.rideconnectx.presentation.theme.sheetContainerColor
import com.eshwar.rideconnectx.presentation.theme.GlassSheetWindow
import com.eshwar.rideconnectx.presentation.theme.cardFill
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import com.eshwar.rideconnectx.presentation.theme.cardSurface
import com.eshwar.rideconnectx.presentation.theme.enterRise
import androidx.compose.runtime.LaunchedEffect
import com.eshwar.rideconnectx.core.util.rememberSystemServices
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import com.eshwar.rideconnectx.data.local.OwnerScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.eshwar.rideconnectx.domain.model.IndianCities
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.LocalContext
import com.eshwar.rideconnectx.domain.model.FieldRules
import com.eshwar.rideconnectx.domain.model.ServiceCentrePicker
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.data.local.db.ServiceRecordEntity
import com.eshwar.rideconnectx.domain.model.ServiceStatus
import com.eshwar.rideconnectx.domain.model.UpcomingTask
import androidx.compose.ui.res.stringResource
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.PrimaryButton
import com.eshwar.rideconnectx.presentation.components.RcxHeroBanner
import com.eshwar.rideconnectx.presentation.theme.LocalDistanceUnit
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.viewmodel.RecordError
import com.eshwar.rideconnectx.domain.model.isValidTaskName
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.viewmodel.ServiceViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Screen 18 — Service.
 *
 * Tracks service intervals and maintenance history. The reminder switch
 * lives in Settings → Notifications, with every other notification switch,
 * rather than being duplicated here. Everything is
 * local (Room + DataStore), so it works offline and in Guest Mode; the scooter
 * has no service counter to read, so the schedule is derived from the rider's
 * own records and the odometer the cluster reports.
 */
@Composable
fun ServiceScreen(
    onBack: () -> Unit,
    vm: ServiceViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val status by vm.status.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val nearbyCentres by vm.nearbyCentres.collectAsStateWithLifecycle()
    val centrePlace by vm.centrePlace.collectAsStateWithLifecycle()
    val tasks by vm.upcomingTasks.collectAsStateWithLifecycle()
    val minimumKm by vm.minimumOdometerKm.collectAsStateWithLifecycle()
    val unit by vm.distanceUnit.collectAsStateWithLifecycle()

    // null = closed, a record = editing it, a blank record = adding one.
    var editing by remember { mutableStateOf<ServiceRecordEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<ServiceRecordEntity?>(null) }
    // null = closed; id 0 = adding a task; otherwise editing that one.
    var editingTask by remember { mutableStateOf<UpcomingTask?>(null) }

    // Everything below reads the rider's chosen units from here. Distances are
    // always stored in kilometres; only the display changes.
    CompositionLocalProvider(LocalDistanceUnit provides unit) {
    ScreenBackdrop(photo = R.drawable.img_service_hero, accent = c.green) {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val headerList = androidx.compose.foundation.lazy.rememberLazyListState()
            BackHeader(title = stringResource(R.string.service_title), onBack = onBack, lifted = headerList.canScrollBackward)

            val refreshContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
            com.eshwar.rideconnectx.presentation.components.RideRefreshBox(modifier = Modifier.weight(1f), onRefresh = {
                // Re-checks what is due, so a reminder that has come up appears now.
                dagger.hilt.android.EntryPointAccessors.fromApplication(
                    refreshContext, com.eshwar.rideconnectx.core.di.ServiceEntryPoint::class.java,
                ).serviceReminder().checkOnce()
            }) {
            LazyColumn(
                state = headerList,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { Box(Modifier.enterRise(0)) { RcxHeroBanner(R.drawable.img_service_hero) } }

                // Order is what the rider does next, then when, then what they
                // have already done. Next Service used to lead, which is the
                // summary of a schedule the rider had not been shown yet.
                item {
                    Column(Modifier.enterRise(1)) {
                    SectionHeader(
                        stringResource(R.string.service_upcoming_tasks),
                        Icons.Filled.Checklist,
                    )
                    Spacer(Modifier.height(10.dp))
                    UpcomingTasksCard(
                        tasks = tasks,
                        onEdit = { editingTask = it },
                        onAdd = { editingTask = UpcomingTask(0, "", 3_000, null) },
                    )
                    }
                }

                item { Box(Modifier.enterRise(2)) { ServiceStatusCard(status) } }

                item {
                    SectionHeader(
                        stringResource(R.string.service_maintenance_history),
                        Icons.Filled.Build,
                    )
                }

                if (records.isEmpty()) {
                    // Tapping the empty state opens the add sheet. A card that
                    // says "no records yet" and does nothing, above a separate
                    // Add button, makes the rider look for the button — the
                    // thing they just tapped was the obvious control.
                    item {
                        EmptyCard(
                            icon = Icons.Filled.Build,
                            title = stringResource(R.string.service_no_records),
                            description = stringResource(R.string.service_no_records_desc),
                            onAdd = {
                                editing = ServiceRecordEntity(
                                    ownerId = OwnerScope.DRAFT,
                                    servicedAt = startOfToday(),
                                    centre = "",
                                    odometerKm = 0,
                                )
                            }
                        )
                    }
                } else {
                    items(records, key = { it.id }) { record ->
                        // New records slide in, deleted ones fold away.
                        Box(Modifier.animateItem().enterRise(3).edgeShrink(headerList, record.id)) {
                        SwipeToDelete(onDelete = { pendingDelete = record }) {
                        ServiceRecordCard(
                            record = record,
                            onEdit = { editing = record },
                            onDelete = { pendingDelete = record },
                        )
                        }
                        }
                    }
                }

                item {
                    PrimaryButton(
                        label = stringResource(R.string.service_add_record),
                        onClick = {
                            editing = ServiceRecordEntity(
                                ownerId = OwnerScope.DRAFT,
                                servicedAt = startOfToday(),
                                centre = "",
                                // Deliberately empty. Pre-filling it with the
                                // last known reading looked helpful and was a
                                // trap: tapping the field puts the caret where
                                // the finger lands, so typing appends. On the
                                // emulator, correcting a pre-filled 1602 to 900
                                // stored 160,290 km. The last known reading is
                                // shown under the field instead, where it can
                                // inform without being typed into.
                                odometerKm = 0,
                            )
                        },
                        icon = {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp), tint = Color.White)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            }
        }
    }

    editing?.let { record ->
        ServiceRecordSheet(
            record = record,
            bounds = { servicedAt -> vm.boundsFor(record.id, servicedAt) },
            pastCentres = records.sortedByDescending { it.servicedAt }
                .map { it.centre.trim() }
                .filter { it.isNotBlank() && it != "Not recorded" }
                .distinctBy { it.lowercase() },
            nearbyCentres = nearbyCentres,
            onCentreOpen = vm::loadNearbyCentres,
            rememberedState = centrePlace.first,
            rememberedCity = centrePlace.second,
            onPlacePicked = vm::rememberCentrePlace,
            onDismiss = { editing = null },
            onSave = { servicedAt, centre, odo, notes ->
                vm.save(record.id, servicedAt, centre, odo, notes)
            },
        )
    }

    editingTask?.let { task ->
        TaskSheet(
            task = task,
            onDismiss = { editingTask = null },
            onSave = { label, everyKm -> vm.saveTask(task.id, label, everyKm) },
            onDelete = if (task.id == 0L) null else {
                {
                    vm.deleteTask(task.id)
                    editingTask = null
                }
            },
        )
    }

    pendingDelete?.let { record ->
        ConfirmDeleteSheet(
            question = stringResource(R.string.service_delete_record_q),
            summary = "${formatDate(record.servicedAt)} · ${record.centre} · " +
                "${unit.format(record.odometerKm)}. " +
                stringResource(R.string.service_delete_record_desc),
            onDismiss = { pendingDelete = null },
            onConfirm = {
                vm.delete(record)
                pendingDelete = null
            },
        )
    }
    }
}

/* ── Service status ───────────────────────────────────────────────── */

@Composable
private fun ServiceStatusCard(status: ServiceStatus) {
    val c = Rcx.colors
    val unit = LocalDistanceUnit.current
    val shape = RoundedCornerShape(22.dp)
    val accent = when {
        status.isOverdue -> c.red
        status.isDueSoon -> c.amber
        else -> c.green
    }
    val progress by animateFloatAsState(
        targetValue = status.progress,
        animationSpec = tween(700),
        label = "serviceProgress",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardFill()
            .border(1.dp, if (status.isOverdue) accent.copy(alpha = 0.31f) else c.border, shape)
            .padding(20.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // How much of the interval is used, as a ring round the spanner.
            Box(
                Modifier
                    .size(48.dp)
                    .drawBehind {
                        val w = 4.dp.toPx()
                        val inset = androidx.compose.ui.geometry.Offset(w / 2, w / 2)
                        val arc = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                        drawArc(accent.copy(alpha = 0.15f), 0f, 360f, false, inset, arc, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                        drawArc(
                            accent, -90f, 360f * progress.coerceIn(0f, 1f), false, inset, arc,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Build, null, Modifier.size(20.dp), tint = accent)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.service_next_service),
                    style = RcxType.MonoTiny,
                    color = c.muted,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        !status.hasBaseline -> stringResource(R.string.service_not_scheduled)
                        status.isOverdue -> stringResource(R.string.service_overdue)
                        status.isDueSoon -> stringResource(R.string.service_due_soon)
                        else -> stringResource(R.string.service_on_schedule)
                    },
                    style = RcxType.Section.copy(fontSize = 17.sp),
                    color = if (status.hasBaseline) accent else c.text,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (!status.hasBaseline) {
            Text(
                stringResource(
                    R.string.service_intro,
                    unit.format(status.intervalKm),
                    status.intervalDays,
                ),
                style = RcxType.BodySmall.copy(fontSize = 13.sp),
                color = c.muted,
            )
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Speed,
                label = stringResource(R.string.service_remaining),
                value = status.remainingKm?.let { km ->
                    if (km <= 0)
                        stringResource(R.string.service_over, unit.format(-km))
                    else
                        unit.format(km)
                } ?: "—",
                accent = accent,
                muted = status.remainingKm == null,
            )
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.CalendarMonth,
                label = stringResource(R.string.service_days_left),
                value = status.remainingDays?.let { d ->
                    if (d <= 0)
                        stringResource(R.string.service_over, "${-d}")
                    else
                        "$d"
                } ?: "—",
                accent = accent,
                muted = status.remainingDays == null,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Progress indicator — how far through the interval, by whichever of
        // distance and time is further along.
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(c.blue.copy(alpha = 0.094f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(
                    R.string.service_last,
                    unit.format(status.lastServiceOdometerKm ?: 0),
                ),
                style = RcxType.Mono.copy(fontSize = 10.sp),
                color = c.muted,
            )
            Text(
                stringResource(
                    R.string.service_due,
                    status.dueAtKm?.let { unit.format(it) } ?: "—",
                ),
                style = RcxType.Mono.copy(fontSize = 10.sp),
                color = c.muted,
            )
        }

        if (status.currentOdometerKm <= 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.service_connect_note),
                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                color = c.muted,
            )
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    muted: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .innerFill()
            .border(1.dp, c.border, shape)
            .padding(14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(13.dp), tint = c.muted)
            Text(label, style = RcxType.MonoTiny, color = c.muted)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            style = RcxType.Section.copy(fontSize = 19.sp),
            color = if (muted) c.muted else accent,
        )
    }
}

/* ── Upcoming tasks ───────────────────────────────────────────────── */

@Composable
private fun UpcomingTasksCard(
    tasks: List<UpcomingTask>,
    onEdit: (UpcomingTask) -> Unit,
    onAdd: () -> Unit,
) {
    val c = Rcx.colors
    val unit = LocalDistanceUnit.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardSurface(shape)
            .padding(vertical = 6.dp),
    ) {
        tasks.forEach { item ->
            val accent = if (item.isOverdue) c.amber else c.blue
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onEdit(item) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accent.copy(alpha = 0.094f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(item.icon(), null, Modifier.size(16.dp), tint = accent)
                }
                Column(Modifier.weight(1f)) {
                    Text(item.label, style = RcxType.Label, color = c.text)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.service_every, unit.format(item.everyKm)),
                        style = RcxType.BodySmall.copy(fontSize = 11.sp),
                        color = c.muted,
                    )
                }
                Text(
                    when {
                        item.remainingKm == null -> "—"
                        item.remainingKm <= 0 -> stringResource(R.string.service_due_label)
                        else -> stringResource(
                            R.string.service_in,
                            unit.format(item.remainingKm),
                        )
                    },
                    style = RcxType.Mono.copy(fontSize = 11.sp),
                    color = if (item.isOverdue) c.amber else c.muted,
                )
                Icon(
                    Icons.Filled.Edit,
                    stringResource(R.string.service_edit_task_cd, item.label),
                    Modifier.size(14.dp), tint = c.muted.copy(alpha = 0.4f),
                )
            }
        }

        // Adding is part of the list, not a separate button somewhere else.
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = androidx.compose.ui.semantics.Role.Button) { onAdd() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(c.blue.copy(alpha = 0.094f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, null, Modifier.size(16.dp), tint = c.blue)
            }
            Text(
                stringResource(R.string.service_add_task),
                style = RcxType.Label,
                color = c.blue,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Icons are matched to the task's name rather than stored.
 *
 * The list is free text now, so a fixed icon per row is impossible — but a
 * rider who types "Chain lube" should still get something better than a generic
 * dot. Anything unrecognised falls back to a wrench, which is honest.
 */
private fun UpcomingTask.icon(): ImageVector {
    val name = label.lowercase()
    return when {
        "oil" in name -> Icons.Filled.WaterDrop
        "brake" in name -> Icons.Filled.Checklist
        "tyre" in name || "tire" in name || "wheel" in name -> Icons.Filled.TireRepair
        "battery" in name -> Icons.Filled.BatteryChargingFull
        "chain" in name || "belt" in name -> Icons.Filled.Settings
        "filter" in name || "air" in name -> Icons.Filled.FilterAlt
        "spark" in name || "plug" in name -> Icons.Filled.Bolt
        else -> Icons.Filled.Build
    }
}

/* ── History ──────────────────────────────────────────────────────── */

@Composable
private fun ServiceRecordCard(
    record: ServiceRecordEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Rcx.colors
    val unit = LocalDistanceUnit.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardSurface(shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatDate(record.servicedAt), style = RcxType.Label.copy(fontSize = 15.sp), color = c.text)
                Spacer(Modifier.height(3.dp))
                // Brighter and a size up: the muted 12 sp read too close to the
                // card background (rider, 20 Sep).
                Text(record.centre, style = RcxType.BodySmall.copy(fontSize = 13.sp), color = c.text.copy(alpha = 0.78f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Text(
                unit.format(record.odometerKm),
                style = RcxType.Mono.copy(fontSize = 12.sp),
                color = c.cyan,
            )
        }

        if (record.notes.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                record.notes,
                style = RcxType.BodySmall.copy(fontSize = 13.sp),
                color = c.text.copy(alpha = 0.78f),
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostAction(
                stringResource(R.string.service_edit), Icons.Filled.Edit, c.blue, onEdit,
            )
            GhostAction(
                stringResource(R.string.service_delete), Icons.Filled.Delete, c.red, onDelete,
            )
        }
    }
}

@Composable
private fun GhostAction(
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
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(13.dp), tint = accent)
        Text(label, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = accent)
    }
}

/**
 * Swipe a row left to delete it. The row always springs back: the swipe only
 * asks, and the existing "delete this record?" dialog still decides.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val c = Rcx.colors
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) onDelete()
            false
        },
    )
    androidx.compose.material3.SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            // Only while the row is being dragged: the cards are glass, so a
            // resting red panel would show straight through them.
            if (state.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(c.red.copy(alpha = 0.16f)).padding(end = 22.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Filled.Delete, null, Modifier.size(22.dp), tint = c.red) }
        },
    ) { content() }
}

/** How long the "Saved" tick is held before a sheet closes. */
private const val SAVED_HOLD_MS = 900L

@Composable
private fun SavedTick() {
    com.eshwar.rideconnectx.presentation.theme.DrawnTick(androidx.compose.material3.LocalContentColor.current, Modifier.size(20.dp))
}

@Composable
private fun EmptyCard(
    icon: ImageVector,
    title: String,
    description: String,
    onAdd: () -> Unit,
) {
    val c = Rcx.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .cardSurface(shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onAdd)
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .floaty()
                .clip(RoundedCornerShape(17.dp))
                .background(c.blue.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = c.blue)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            style = RcxType.Label.copy(fontSize = 15.sp),
            color = c.text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            style = RcxType.BodySmall.copy(fontSize = 12.sp),
            color = c.muted,
            textAlign = TextAlign.Center,
        )
    }
}

/* ── Record sheet ─────────────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceRecordSheet(
    record: ServiceRecordEntity,
    /** Allowed (floor, ceiling) readings for a given service date. */
    bounds: (Long) -> Pair<Int, Int?>,
    /** Centres from the rider's earlier records, newest first. */
    pastCentres: List<String>,
    /** Nearby centres (OpenStreetMap) or area names, loaded when the field opens. */
    nearbyCentres: List<String>,
    onCentreOpen: () -> Unit,
    /** Last state and city picked; blank the first time. */
    rememberedState: String,
    rememberedCity: String,
    onPlacePicked: (state: String, city: String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (servicedAt: Long, centre: String, odometerKm: Int?, notes: String) -> RecordError?,
) {
    val c = Rcx.colors
    val unit = LocalDistanceUnit.current
    var servicedAt by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(record.servicedAt) }
    var centre by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(record.centre) }
    // Held as TextFieldValue with the pre-filled reading selected, so the first
    // keystroke *replaces* it. Plain text put the cursor after the pre-fill and
    // typing appended: on the emulator, correcting a pre-filled 1602 to 900
    // produced 160,290 km. A pre-filled field the rider has to clear by hand is
    // worse than no pre-fill at all.
    var odometer by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val text = if (record.odometerKm > 0) record.odometerKm.toString() else ""
        mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length)))
    }
    var notes by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(record.notes) }
    var error by remember { mutableStateOf<RecordError?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    // Saved: the button shows a tick for a moment, then the sheet closes.
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) { kotlinx.coroutines.delay(SAVED_HOLD_MS); onDismiss() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetContainerColor(),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.sheetEnter()) {
        GlassSheetWindow()
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                if (record.id == 0L)
                    stringResource(R.string.service_add_record)
                else
                    stringResource(R.string.service_edit_record),
                style = RcxType.Section.copy(fontSize = 17.sp),
                color = c.text,
            )
            Spacer(Modifier.height(18.dp))

            FieldLabel(stringResource(R.string.service_date))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .innerFill()
                    .border(1.dp, c.border, RoundedCornerShape(14.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { showDatePicker = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.CalendarMonth, null, Modifier.size(17.dp), tint = c.blue)
                Text(formatDate(servicedAt), style = RcxType.Body.copy(fontSize = 15.sp), color = c.text)
            }
            if (error is RecordError.DateInFuture) {
                FieldError(stringResource(R.string.service_date_future_error))
            }

            Spacer(Modifier.height(14.dp))

            FieldLabel(stringResource(R.string.service_centre))
            // State → City → centre (N14 redesign, owner-approved 28 Sep).
            // Opening the box steps through a state, that state's cities, then
            // centres for the city: "Near me" and past centres. The last state
            // and city are remembered, so the next open starts at centres.
            // Typing is always allowed and saves as typed.
            var centreOpen by remember { mutableStateOf(false) }
            var pickState by remember { mutableStateOf(rememberedState) }
            var pickCity by remember { mutableStateOf(rememberedCity) }
            val focus = LocalFocusManager.current
            SheetField(
                value = centre,
                onValueChange = { centre = it.take(FieldRules.CENTRE_MAX) },
                placeholder = stringResource(R.string.service_centre_placeholder),
                capitalization = KeyboardCapitalization.Words,
                modifier = Modifier.onFocusChanged {
                    if (it.isFocused && !centreOpen) onCentreOpen()
                    centreOpen = it.isFocused
                },
            )
            val nearOptions = ServiceCentrePicker.options(emptyList(), nearbyCentres, centre).take(6)
            val pastOptions = ServiceCentrePicker.options(pastCentres, emptyList(), centre).take(6)

            // Location off or not allowed: "Near me" offers to turn it on, with
            // the same permission dialog and location switch as setup. Declined
            // hides the section rather than nagging.
            val ctx = LocalContext.current
            var locationDeclined by remember { mutableStateOf(false) }
            val services = rememberSystemServices(onLocationDeclined = { locationDeclined = true })
            fun locationAllowed() = listOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
            ).any { androidx.core.content.ContextCompat.checkSelfPermission(ctx, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
            var locationAllowed by remember { mutableStateOf(locationAllowed()) }
            val askLocation = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {
                locationAllowed = locationAllowed()
                if (!locationAllowed) locationDeclined = true
                else if (!services.locationOn) services.openLocationSettings()
            }
            val needsLocation = !locationAllowed || !services.locationOn
            // Refresh the list the moment location becomes usable.
            LaunchedEffect(locationAllowed, services.locationOn) {
                if (centreOpen && !needsLocation) onCentreOpen()
            }

            if (centreOpen) {
                Column(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .innerFill()
                        .border(1.dp, c.border, RoundedCornerShape(14.dp))
                        .verticalScroll(rememberScrollState()),
                ) {
                    when {
                        // Step 1: the state.
                        // Typing filters the state and city lists; picking one
                        // clears the typing, ready for the centre's name.
                        pickState.isBlank() -> {
                            PickerTitle("Choose your state")
                            IndianCities.states.filter { it.contains(centre.trim(), ignoreCase = true) }
                                .forEach { s -> PickerRow(s) { pickState = s; centre = "" } }
                        }
                        // Step 2: that state's cities (the N15 list, by state).
                        pickCity.isBlank() -> {
                            PickerTitle("$pickState  ·  change") { pickState = "" }
                            IndianCities.citiesIn(pickState).filter { it.contains(centre.trim(), ignoreCase = true) }
                                .forEach { city ->
                                    PickerRow(city) {
                                        pickCity = city
                                        centre = ""
                                        onPlacePicked(pickState, city)
                                    }
                                }
                        }
                        // Step 3: centres for the city.
                        else -> {
                            PickerTitle("$pickState › $pickCity  ·  change") { pickCity = "" }
                            if (!locationDeclined) {
                                PickerTitle("Near me")
                                if (needsLocation) {
                                    PickerRow("Turn on location to see nearby centres", c.blue) {
                                        if (!locationAllowed) {
                                            askLocation.launch(
                                                arrayOf(
                                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                                )
                                            )
                                        } else {
                                            services.openLocationSettings()
                                        }
                                    }
                                }
                                nearOptions.forEach { o -> PickerRow(o) { centre = o; focus.clearFocus() } }
                                // ODbL attribution wherever OpenStreetMap data is shown (CHK-2).
                                if (nearOptions.isNotEmpty()) {
                                    PickerTitle(com.eshwar.rideconnectx.domain.model.LegalContent.OSM_CREDIT)
                                }
                            }
                            if (pastOptions.isNotEmpty()) {
                                PickerTitle("Your past centres")
                                pastOptions.forEach { o -> PickerRow(o) { centre = o; focus.clearFocus() } }
                            }
                            // TODO(R2): Google Places autocomplete for [pickCity] plugs in
                            // here once billing is enabled; until then, typed text saves.
                        }
                    }
                }
            }
            if (error is RecordError.CentreInvalid) {
                FieldError(stringResource(R.string.service_centre_error))
            }
            // A Maps search that is always there — even with no past centres,
            // where the row used to be empty and looked missing (rider, 26 Sep).
            val context = LocalContext.current
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Text(
                    stringResource(R.string.service_find_nearby),
                    style = RcxType.BodySmall.copy(fontSize = 13.sp),
                    color = c.blue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, c.blue.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { openNearbyServiceCentres(context) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            // Always entered in kilometres — that is what the vehicle reads and
            // what gets stored, whatever the display unit is set to.
            FieldLabel(stringResource(R.string.service_odometer))
            OdometerField(
                value = odometer,
                onValueChange = { input ->
                    val digits = input.text.filter(Char::isDigit).take(6)
                    // Keep the caret where the rider put it, clamped to the
                    // text that survived filtering.
                    odometer = input.copy(
                        text = digits,
                        selection = TextRange(input.selection.start.coerceAtMost(digits.length)),
                    )
                },
            )
            when (val e = error) {
                is RecordError.OdometerMissing ->
                    FieldError(stringResource(R.string.service_odometer_error))
                is RecordError.OdometerTooLow ->
                    FieldError(
                        stringResource(
                            R.string.service_odometer_min_error,
                            unit.format(e.minimumKm),
                        )
                    )
                is RecordError.OdometerTooHigh ->
                    FieldError(
                        stringResource(R.string.service_odometer_max_error, unit.format(e.maximumKm))
                    )
                else -> {
                    // Follows the chosen date, so back-filling an old service
                    // shows the range that date allows.
                    val (floor, ceiling) = bounds(servicedAt)
                    val hint = when {
                        ceiling != null -> stringResource(
                            R.string.service_odometer_range, unit.format(floor), unit.format(ceiling),
                        )
                        floor > 0 -> stringResource(R.string.service_odometer_at_least, unit.format(floor))
                        else -> null
                    }
                    hint?.let {
                        Text(
                            it,
                            style = RcxType.BodySmall.copy(fontSize = 12.sp),
                            color = c.text.copy(alpha = 0.72f),
                            modifier = Modifier.padding(start = 4.dp, top = 5.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            FieldLabel(stringResource(R.string.service_notes))
            SheetField(
                value = notes,
                onValueChange = { notes = it.take(FieldRules.NOTES_MAX) },
                placeholder = stringResource(R.string.service_notes_placeholder),
                capitalization = KeyboardCapitalization.Sentences,
                singleLine = false,
            )
            // Tap to add the common ones rather than typing them (rider, 20 Sep).
            val suggestions = androidx.compose.ui.res.stringArrayResource(R.array.service_note_suggestions)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                suggestions.forEach { s ->
                    val added = notes.contains(s, ignoreCase = true)
                    Text(
                        s,
                        style = RcxType.BodySmall.copy(fontSize = 13.sp),
                        color = if (added) c.blue else c.text,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (added) c.blue.copy(alpha = 0.14f) else c.card2)
                            .border(1.dp, if (added) c.blue else c.border, RoundedCornerShape(50))
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                // Tap again to take it back out (rider, 21 Sep).
                                val parts = notes.split(',').map(String::trim).filter(String::isNotBlank)
                                notes = if (added) {
                                    parts.filterNot { it.equals(s, ignoreCase = true) }.joinToString(", ")
                                } else {
                                    (parts + s).joinToString(", ")
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            PrimaryButton(
                label = if (saved) "Saved" else stringResource(R.string.service_save_record),
                onClick = {
                    if (!saved) {
                        error = onSave(servicedAt, centre, odometer.text.toIntOrNull(), notes)
                        saved = error == null
                    }
                },
                icon = if (saved) { { SavedTick() } } else null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = localDateToUtc(servicedAt))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker speaks UTC midnight. Stored and displayed dates
                    // are local, so the calendar day has to be carried across
                    // rather than the instant — otherwise a west-of-UTC rider
                    // sees yesterday.
                    state.selectedDateMillis?.let { servicedAt = utcDateToLocal(it) }
                    showDatePicker = false
                }) { Text(stringResource(android.R.string.ok), color = c.blue) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.service_cancel), color = c.muted)
                }
            },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = c.card),
        ) {
            DatePicker(
                state = state,
                // N37 row 16: the pencil switches to typed date entry.
                showModeToggle = true,
                colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = c.card),
            )
        }
    }
}

/**
 * Add or edit one upcoming task.
 *
 * The list was a hardcoded four with no way to change it, which is what the
 * rider ran into. Every row is editable now, and one that does not apply to
 * their vehicle can simply be deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskSheet(
    task: UpcomingTask,
    onDismiss: () -> Unit,
    onSave: (label: String, everyKm: Int?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = Rcx.colors
    var label by remember { mutableStateOf(task.label) }
    var everyKm by remember {
        mutableStateOf(if (task.everyKm > 0) task.everyKm.toString() else "")
    }
    var showError by remember { mutableStateOf(false) }
    val valid = isValidTaskName(label) && (everyKm.toIntOrNull() ?: 0) > 0
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) { kotlinx.coroutines.delay(SAVED_HOLD_MS); onDismiss() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = sheetContainerColor(),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.sheetEnter()) {
        GlassSheetWindow()
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                if (task.id == 0L)
                    stringResource(R.string.service_add_task)
                else
                    stringResource(R.string.service_edit_task),
                style = RcxType.Section.copy(fontSize = 17.sp),
                color = c.text,
            )
            Spacer(Modifier.height(18.dp))

            FieldLabel(stringResource(R.string.service_task_name))
            SheetField(
                value = label,
                onValueChange = { label = it.take(FieldRules.TASK_MAX); showError = false },
                placeholder = stringResource(R.string.service_task_name_placeholder),
                capitalization = KeyboardCapitalization.Sentences,
            )

            Spacer(Modifier.height(14.dp))

            FieldLabel(stringResource(R.string.service_task_interval))
            SheetField(
                value = everyKm,
                onValueChange = { everyKm = it.filter(Char::isDigit).take(6); showError = false },
                placeholder = "3000",
                numeric = true,
            )
            if (showError) {
                FieldError(stringResource(R.string.service_task_error))
            }

            Spacer(Modifier.height(22.dp))

            PrimaryButton(
                label = if (saved) "Saved" else stringResource(R.string.service_save),
                onClick = {
                    if (saved) Unit
                    else if (valid) { onSave(label, everyKm.toIntOrNull()); saved = true }
                    else showError = true
                },
                icon = if (saved) { { SavedTick() } } else null,
                modifier = Modifier.fillMaxWidth(),
            )

            onDelete?.let { delete ->
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.red.copy(alpha = 0.094f))
                        .border(1.5.dp, c.red.copy(alpha = 0.31f), RoundedCornerShape(16.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = delete),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.service_remove_task),
                        style = RcxType.Button,
                        color = c.red,
                    )
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmDeleteSheet(
    question: String,
    summary: String,
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
                question,
                style = RcxType.Section.copy(fontSize = 17.sp),
                color = c.text,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                summary,
                style = RcxType.BodySmall.copy(fontSize = 13.sp),
                color = c.muted,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    label = stringResource(R.string.service_keep),
                    onClick = onDismiss,
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
                        stringResource(R.string.service_delete),
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
private fun FieldLabel(text: String) {
    Text(
        text,
        style = RcxType.MonoTiny,
        color = Rcx.colors.muted,
        modifier = Modifier.padding(bottom = 8.dp, start = 2.dp),
    )
}

/** Maps searches around the rider; the browser when no Maps app is installed. */
private fun openNearbyServiceCentres(context: android.content.Context) {
    val query = android.net.Uri.encode("two wheeler service centre")
    val maps = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=$query"))
    val web = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=$query"),
    )
    runCatching { context.startActivity(maps) }
        .recoverCatching { context.startActivity(web) }
}

/** A section title in the centre picker; tappable when it goes back a step. */
@Composable
private fun PickerTitle(text: String, onClick: (() -> Unit)? = null) {
    Text(
        text,
        style = RcxType.BodySmall.copy(fontSize = 12.sp),
        color = if (onClick != null) Rcx.colors.blue else Rcx.colors.muted,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick) else Modifier)
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickerRow(text: String, color: Color = Rcx.colors.text, onClick: () -> Unit) {
    Text(
        text,
        style = RcxType.Body.copy(fontSize = 14.sp),
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    )
}

@Composable
private fun FieldError(text: String) {
    Text(
        text,
        style = RcxType.BodySmall.copy(fontSize = 12.sp),
        color = Rcx.colors.red,
        modifier = Modifier.padding(start = 4.dp, top = 5.dp),
    )
}

/**
 * The odometer entry, as a [TextFieldValue] field so the pre-filled reading can
 * arrive selected and be replaced by the first keystroke.
 */
@Composable
private fun OdometerField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
) {
    val c = Rcx.colors
    // Selecting at construction is not enough: tapping into the field puts the
    // caret where the finger landed and typing then *appends* to the pre-fill.
    // That is how correcting 1602 to 900 produced 160,290 km on the emulator.
    // Selecting on the first focus makes the first keystroke replace instead.
    var selectedOnFocus by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                stringResource(R.string.service_reading_at_time),
                style = RcxType.Body.copy(fontSize = 14.sp),
                color = c.muted,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = RcxType.Body.copy(fontSize = 15.sp, color = c.text),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.card2,
            unfocusedContainerColor = c.card2,
            focusedBorderColor = c.blue,
            unfocusedBorderColor = c.border,
            cursorColor = c.blue,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (state.isFocused && !selectedOnFocus) {
                    selectedOnFocus = true
                    onValueChange(value.copy(selection = TextRange(0, value.text.length)))
                }
            },
    )
}

@Composable
private fun SheetField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    modifier: Modifier = Modifier,
) {
    val c = Rcx.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, style = RcxType.Body.copy(fontSize = 14.sp), color = c.muted) },
        singleLine = singleLine,
        shape = RoundedCornerShape(14.dp),
        textStyle = RcxType.Body.copy(fontSize = 15.sp, color = c.text),
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.card2,
            unfocusedContainerColor = c.card2,
            focusedBorderColor = c.blue,
            unfocusedBorderColor = c.border,
            cursorColor = c.blue,
        ),
        modifier = Modifier.fillMaxWidth().then(modifier),
    )
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    val c = Rcx.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = c.blue)
        Text(title, style = RcxType.Section.copy(fontSize = 16.sp), color = c.text)
    }
}

/* ── Formatting ───────────────────────────────────────────────────── */

private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

private fun formatDate(millis: Long): String = dateFormat.format(Date(millis))

/** Midnight today, so a freshly opened form is dated the day, not the second. */
private fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Local instant → the UTC-midnight value Material's date picker expects. */
private fun localDateToUtc(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** The picker's UTC-midnight value → local midnight on the same calendar day. */
private fun utcDateToLocal(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
