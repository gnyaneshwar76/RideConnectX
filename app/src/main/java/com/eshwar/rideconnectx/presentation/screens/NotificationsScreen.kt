package com.eshwar.rideconnectx.presentation.screens

import com.eshwar.rideconnectx.presentation.theme.edgeShrink
import com.eshwar.rideconnectx.presentation.theme.floaty
import com.eshwar.rideconnectx.presentation.theme.ScreenBackdrop
import com.eshwar.rideconnectx.presentation.theme.cardSurface
import com.eshwar.rideconnectx.presentation.theme.enterRise
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import com.eshwar.rideconnectx.presentation.viewmodel.NotificationGroup
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.data.local.db.NotificationEntity
import com.eshwar.rideconnectx.data.local.db.NotificationKind
import com.eshwar.rideconnectx.presentation.components.BackHeader
import com.eshwar.rideconnectx.presentation.components.NotificationAccessWarning
import com.eshwar.rideconnectx.presentation.components.PostNotificationsWarning
import com.eshwar.rideconnectx.presentation.components.RcxPhoto
import com.eshwar.rideconnectx.presentation.theme.Rcx
import com.eshwar.rideconnectx.presentation.theme.RcxColors
import com.eshwar.rideconnectx.presentation.theme.RcxType
import com.eshwar.rideconnectx.presentation.theme.readableOn
import androidx.compose.ui.graphics.toArgb
import com.eshwar.rideconnectx.presentation.viewmodel.NotificationsViewModel

/**
 * Screen 17 — Notifications.
 *
 * Backed by Room, so it works offline and in Guest Mode. Tapping a row marks it
 * read and jumps to the feature it refers to; [onOpen] receives the kind so the
 * nav graph — not this screen — decides the destination.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpen: (NotificationKind) -> Unit,
    vm: NotificationsViewModel = hiltViewModel(),
) {
    val c = Rcx.colors
    val items by vm.notifications.collectAsStateWithLifecycle()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val unread by vm.unreadCount.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.notifs_clear_title)) },
            text = { Text(stringResource(R.string.notifs_clear_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { vm.clearAll(); confirmClear = false }) {
                    Text(stringResource(R.string.notifs_clear_confirm))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.notifs_cancel))
                }
            },
        )
    }

    ScreenBackdrop(accent = c.amber) {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            BackHeader(
                title = stringResource(R.string.notifs_title),
                onBack = onBack,
                lifted = listState.canScrollBackward,
                right = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Only offered when it would actually do something.
                        if (unread > 0) {
                            HeaderAction(stringResource(R.string.notifs_mark_all), c.blue) { vm.markAllRead() }
                        }
                        if (items.isNotEmpty()) {
                            HeaderAction(stringResource(R.string.notifs_clear), c.red) { confirmClear = true }
                        }
                    }
                },
            )

            if (items.isEmpty()) {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    PostNotificationsWarning(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp))
                    NotificationAccessWarning(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp))
                    EmptyNotifications(Modifier.padding(horizontal = 20.dp).enterRise(0))
                }
            } else {
                val refreshContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
                com.eshwar.rideconnectx.presentation.components.RideRefreshBox(onRefresh = {
                    // Re-checks what is due, so a reminder that has come up appears now.
                    dagger.hilt.android.EntryPointAccessors.fromApplication(
                        refreshContext, com.eshwar.rideconnectx.core.di.ServiceEntryPoint::class.java,
                    ).serviceReminder().checkOnce()
                }) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "warnings") {
                        Column {
                            PostNotificationsWarning(Modifier.padding(bottom = 12.dp))
                            NotificationAccessWarning(Modifier.padding(bottom = 12.dp))
                        }
                    }
                    var n = 0
                    sections.forEach { sec ->
                        item(key = "day${sec.day.toEpochDay()}") { DayHeader(sec.day) }
                        val base = n
                        n += sec.groups.size
                        itemsIndexed(sec.groups, key = { _, g -> g.latest.id }) { idx, g ->
                            val i = base + idx
                            Box(Modifier.animateItem().enterRise(i.coerceAtMost(8)).edgeShrink(listState, g.latest.id)) {
                                SwipeToDelete(onDelete = { vm.delete(g.ids) }) {
                                NotificationRow(
                                    group = g,
                                    onDelete = { vm.delete(g.ids) },
                                    onClick = {
                                        vm.markRead(g.ids)
                                        onOpen(g.latest.kind)
                                    },
                                )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    group: NotificationGroup,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Rcx.colors
    val notification = group.latest
    val accent = notification.kind.accent(c)
    val shape = RoundedCornerShape(18.dp)
    val unread = group.unread
    val person = notification.kind == NotificationKind.CALL || notification.kind == NotificationKind.MESSAGE
    val unreadLabel = stringResource(R.string.notifs_unread)
    val deleteLabel = stringResource(R.string.common_delete)
    val openLabel = stringResource(R.string.notifs_open)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            // Unread rows carry a tint of their own accent; read rows go flat.
            .background(if (unread) accent.copy(alpha = if (c.isDark) 0.047f else 0.031f) else c.card)
            .border(1.dp, if (unread) accent.copy(alpha = 0.157f) else c.border, shape)
            .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick)
            .semantics {
                if (unread) stateDescription = unreadLabel
                // TalkBack alternative to the swipe.
                customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
            }
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(if (person) CircleShape else RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.094f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                notification.kind.icon(),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
        }

        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    notification.title,
                    style = RcxType.Label.copy(fontSize = 14.sp),
                    color = c.text,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                kindLabel(notification)?.let {
                    Text(it, style = RcxType.MonoTiny.copy(fontSize = 10.sp), color = accent)
                }
                if (group.count > 1) {
                    Text(
                        "x${group.count}",
                        style = RcxType.MonoTiny.copy(fontSize = 10.sp),
                        color = c.muted,
                        modifier = Modifier.clip(CircleShape).background(c.border).padding(horizontal = 6.dp, vertical = 1.dp)
                            // TalkBack said "x 3"; spoken as "3 times".
                            .semantics { contentDescription = "${group.count} times" },
                    )
                }
                if (unread) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(c.blue))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                notification.body,
                style = RcxType.BodySmall.copy(fontSize = 12.sp),
                color = c.muted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                clockTime(notification.createdAt),
                style = RcxType.MonoTiny.copy(fontSize = 10.sp),
                // Muted nudged until 4.5:1 on the card (plain muted is 4.48 in dark).
                color = Color(readableOn(c.muted.toArgb().toLong() and 0xFFFFFFFFL, c.card.toArgb().toLong() and 0xFFFFFFFFL)),
            )
        }

        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = c.muted.copy(alpha = 0.31f),
            modifier = Modifier.size(14.dp).padding(top = 4.dp),
        )
    }
}

@Composable
private fun EmptyNotifications(modifier: Modifier = Modifier) {
    val c = Rcx.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .cardSurface(RoundedCornerShape(20.dp))
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The photograph replaces the icon tile here rather than sitting above
        // it: an empty state is the one place with room to spare, and a still
        // life reads as "nothing to do" far better than a bell glyph does.
        RcxPhoto(
            res = R.drawable.img_empty_notifications,
            ratio = 1f,
            modifier = Modifier
                .size(132.dp)
                .floaty()
                .clip(RoundedCornerShape(24.dp)),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.notifs_empty), style = RcxType.Label.copy(fontSize = 15.sp), color = c.text)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.notifs_empty_body),
            style = RcxType.BodySmall.copy(fontSize = 12.sp),
            color = c.muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun kindLabel(n: NotificationEntity): String? = when (n.kind) {
    NotificationKind.CALL ->
        stringResource(if (n.body == "Missed call") R.string.notifs_label_missed else R.string.notifs_label_call)
    NotificationKind.MESSAGE -> stringResource(R.string.notifs_label_message)
    else -> null
}

@Composable
private fun HeaderAction(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = RcxType.BodySmall.copy(fontSize = 12.sp), color = color) }
}

@Composable
private fun DayHeader(day: LocalDate) {
    val today = LocalDate.now()
    val text = when (day) {
        today -> stringResource(R.string.notifs_today)
        today.minusDays(1) -> stringResource(R.string.notifs_yesterday)
        else -> day.format(DateTimeFormatter.ofPattern("d MMM"))
    }
    Text(
        text,
        style = RcxType.Label.copy(fontSize = 12.sp),
        color = Rcx.colors.muted,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).semantics { heading() },
    )
}

private fun NotificationKind.icon(): ImageVector = when (this) {
    NotificationKind.SERVICE -> Icons.Filled.Build
    NotificationKind.VEHICLE -> Icons.Filled.TwoWheeler
    NotificationKind.RIDE -> Icons.AutoMirrored.Filled.DirectionsBike
    NotificationKind.SAFETY -> Icons.Filled.Shield
    NotificationKind.SYSTEM -> Icons.Filled.Info
    NotificationKind.CALL -> Icons.Filled.Call
    NotificationKind.MESSAGE -> Icons.Filled.Chat
}

private fun NotificationKind.accent(c: RcxColors): Color = when (this) {
    NotificationKind.SERVICE -> c.amber
    NotificationKind.VEHICLE -> c.blue
    NotificationKind.RIDE -> c.cyan
    NotificationKind.SAFETY -> c.red
    NotificationKind.SYSTEM -> c.green
    NotificationKind.CALL -> c.green
    NotificationKind.MESSAGE -> c.blue
}

/** Clock time of the row; the day is in the section header. */
private fun clockTime(millis: Long): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(millis))
