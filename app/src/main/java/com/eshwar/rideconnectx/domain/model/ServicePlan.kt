package com.eshwar.rideconnectx.domain.model

import java.util.concurrent.TimeUnit

/**
 * Everything the Service screen needs to answer "when is my next service?".
 *
 * All of it is computed from the rider's own service records plus the odometer
 * the scooter reports. Nothing here comes off the vehicle directly — the
 * cluster does not transmit a service counter — so [hasBaseline] says plainly
 * whether there is enough information to be talking about a next service at all.
 */
data class ServiceStatus(
    /** Odometer at the last recorded service, or null if there is none. */
    val lastServiceOdometerKm: Int? = null,
    /** Date of the last recorded service, epoch millis, or null. */
    val lastServiceAt: Long? = null,
    /** The most recent odometer reading the app has seen, 0 if never. */
    val currentOdometerKm: Int = 0,
    val intervalKm: Int = 3_000,
    val intervalDays: Int = 90,
) {
    /** Without a service record there is no baseline to count from. */
    val hasBaseline: Boolean get() = lastServiceOdometerKm != null && lastServiceAt != null

    /** Odometer reading the next service is due at. */
    val dueAtKm: Int? get() = lastServiceOdometerKm?.plus(intervalKm)

    /** Epoch millis the next service is due by. */
    val dueByMillis: Long?
        get() = lastServiceAt?.plus(TimeUnit.DAYS.toMillis(intervalDays.toLong()))

    /** Kilometres left, negative once overdue. Null when unknown. */
    val remainingKm: Int?
        get() {
            val due = dueAtKm ?: return null
            if (currentOdometerKm <= 0) return null
            return due - currentOdometerKm
        }

    /** Days left, negative once overdue. Null when unknown. */
    val remainingDays: Int?
        get() {
            val due = dueByMillis ?: return null
            val delta = due - System.currentTimeMillis()
            // Truncating toward zero would call a service due in 20 hours
            // "0 days left" and one 20 hours overdue the same. Round away.
            return Math.ceil(delta.toDouble() / TimeUnit.DAYS.toMillis(1)).toInt()
        }

    /**
     * How far through the interval the rider is, 0f..1f, taking whichever of
     * distance and time is further along — the service is due on the first of
     * the two, so the bar must show the nearer one.
     */
    val progress: Float
        get() {
            val byKm = lastServiceOdometerKm?.let { last ->
                if (currentOdometerKm <= 0) null
                else (currentOdometerKm - last).toFloat() / intervalKm
            }
            val byTime = lastServiceAt?.let { last ->
                val elapsed = System.currentTimeMillis() - last
                elapsed.toFloat() / TimeUnit.DAYS.toMillis(intervalDays.toLong())
            }
            val worst = listOfNotNull(byKm, byTime).maxOrNull() ?: 0f
            return worst.coerceIn(0f, 1f)
        }

    val isOverdue: Boolean
        get() = (remainingKm?.let { it <= 0 } ?: false) ||
            (remainingDays?.let { it <= 0 } ?: false)

    /** Due soon enough to warn about: within 10% of the interval. */
    val isDueSoon: Boolean
        get() = !isOverdue && progress >= 0.9f
}

/**
 * One row of the Upcoming Tasks list, with how far it is away.
 *
 * Was an enum of exactly four items, which is why the list could not be edited.
 * The rider's tasks live in the `service_tasks` table now; this is the view of
 * one of them with its distance worked out.
 */
data class UpcomingTask(
    val id: Long,
    val label: String,
    val everyKm: Int,
    /** Kilometres until this item is due; negative when overdue. Null if unknown. */
    val remainingKm: Int?,
) {
    val isOverdue: Boolean get() = remainingKm != null && remainingKm <= 0
}

/** A task is named, not numbered: "3000" in the name field was a distance typed in the wrong place. */
fun isValidTaskName(label: String): Boolean = FieldRules.isValidTaskName(label)
