package com.eshwar.rideconnectx.domain.model

/**
 * Whether a service reminder is owed. Pure, so it is testable without a device.
 *
 * A reminder is identified by its level and the service it counts from, so it
 * fires once per service cycle and level — not on every check — and a new or
 * edited service record starts a fresh cycle.
 */
object ServiceReminderRule {
    /** The reminder this status calls for, or null when none is. */
    fun reminderKey(status: ServiceStatus, enabled: Boolean): String? {
        if (!enabled || !status.hasBaseline) return null
        val level = when {
            status.isOverdue -> "overdue"
            status.isDueSoon -> "due_soon"
            else -> return null
        }
        return "$level@${status.lastServiceAt}/${status.lastServiceOdometerKm}"
    }

    fun shouldNotify(key: String?, lastNotified: String): Boolean = key != null && key != lastNotified

    /** "300 days over", "120 km over", or how much is left when due soon. */
    fun detail(status: ServiceStatus): String {
        val km = status.remainingKm
        val days = status.remainingDays
        return when {
            status.isOverdue && km != null && km <= 0 -> "${-km} km over"
            status.isOverdue && days != null -> "${-days} days over"
            km != null && days != null -> "$km km or $days days left"
            days != null -> "$days days left"
            else -> ""
        }
    }
}
