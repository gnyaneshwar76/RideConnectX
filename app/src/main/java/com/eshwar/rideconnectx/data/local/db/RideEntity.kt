package com.eshwar.rideconnectx.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a ride is in its keep/delete life. DELETED is a hidden tombstone until the cloud copy is gone. */
object KeepState {
    const val PENDING = "PENDING"
    const val KEPT = "KEPT"
    const val DELETED = "DELETED"
}

/** [RideEntity.cloudState] values. */
object CloudState {
    const val NOT_UPLOADED = 0
    const val UPLOADED = 1
    const val PHONE_ONLY = 2   // cloud already held 10 rides
}

/**
 * One completed GPS ride (table v9).
 *
 * Distances are metres and durations milliseconds, so the numbers stay exact;
 * formatting into km / minutes happens in the UI layer. [route] is a Google
 * encoded polyline: precise location history, never written to logs.
 */
@Entity(
    tableName = "rides",
    indices = [Index(value = ["ownerId"]), Index(value = ["keepState", "expiresAt"])],
)
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /**
     * The Google account this row belongs to, or [OwnerScope.GUEST].
     * Every query filters on it. No default: the compiler has to point at every
     * write site, because a row saved under the wrong owner is the bug this prevents.
     */
    val ownerId: String,

    /** Epoch millis when the ride started - also the sort key. */
    val startedAt: Long,
    val endedAt: Long,

    val distanceMeters: Int,
    val durationMillis: Long,

    /** Peak and mean speed in km/h, from GPS. */
    val topSpeedKmh: Int = 0,
    val avgSpeedKmh: Int = 0,

    /** Optional label, e.g. "Morning Commute". */
    val title: String = "",

    val vehicleId: String = "",

    /** Encoded polyline, at most 2000 points. Empty if none. */
    val route: String = "",
    val source: String = "gps",

    /** [KeepState]. */
    val keepState: String = KeepState.PENDING,
    /** endedAt + 30 days while PENDING; null once KEPT. */
    val expiresAt: Long? = null,
    /** [CloudState]. */
    val cloudState: Int = CloudState.NOT_UPLOADED,
    /** "r0".."r9" when uploaded. */
    val cloudSlot: String? = null,
)
