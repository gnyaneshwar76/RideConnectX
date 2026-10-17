package com.eshwar.rideconnectx.presentation.viewmodel

import com.eshwar.rideconnectx.data.local.db.NotificationEntity
import com.eshwar.rideconnectx.data.local.db.NotificationKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Rows with the same kind, sender, text and day, shown as one row. [latest] is
 * the newest. [ids] are every stored row the group stands for (read and delete
 * act on all of them); [count] is how many it shows as "xN".
 */
data class NotificationGroup(
    val latest: NotificationEntity,
    val ids: List<Long>,
    val unread: Boolean,
    val count: Int = ids.size,
)

data class DaySection(val day: LocalDate, val groups: List<NotificationGroup>)

private const val CALL_BODY = "Call"
private const val MISSED_CALL_BODY = "Missed call"

// The dialer wraps the ringing title in bidi marks; the missed-call title is plain.
private val INVISIBLE = Regex("[\\u202A-\\u202E\\u2066-\\u2069\\u200E\\u200F]")
private fun cleanTitle(t: String) = t.replace(INVISIBLE, "").trim()

/**
 * Newest day first, newest group first. N44: a missed call is stored as "Call"
 * (ringing) then "Missed call"; on a day where a sender has a missed call only
 * the Missed call row shows. The "Call" rows stay stored and ride along in its
 * [NotificationGroup.ids], so reading or deleting the row covers them too.
 */
fun groupNotifications(items: List<NotificationEntity>, zone: ZoneId = ZoneId.systemDefault()): List<DaySection> =
    items.sortedByDescending { it.createdAt }
        .groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
        .map { (day, rows) ->
            val groups = rows.groupBy { Triple(it.kind, cleanTitle(it.title), it.body) }
            DaySection(
                day,
                groups.mapNotNull { (key, g) ->
                    val (kind, title, body) = key
                    val missed = groups[Triple(NotificationKind.CALL, title, MISSED_CALL_BODY)]
                    when {
                        kind == NotificationKind.CALL && body == CALL_BODY && missed != null -> null
                        kind == NotificationKind.CALL && body == MISSED_CALL_BODY -> {
                            val rang = groups[Triple(NotificationKind.CALL, title, CALL_BODY)].orEmpty()
                            NotificationGroup(g.first(), (g + rang).map { it.id }, (g + rang).any { it.unread }, count = g.size)
                        }
                        else -> NotificationGroup(g.first(), g.map { it.id }, g.any { it.unread })
                    }
                },
            )
        }
