package com.eshwar.rideconnectx.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One entry in the maintenance history — a service the rider actually had done.
 *
 * Entered by hand. The scooter does not report its service history over BLE, so
 * inventing entries from ride data would be a guess; these are facts the rider
 * supplies and can edit or delete.
 */
@Entity(tableName = "service_records", indices = [Index(value = ["ownerId"])])
data class ServiceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /**
     * The Google account this row belongs to, or [OwnerScope.GUEST].
     *
     * Every query filters on it. Two accounts share one database file, and
     * before this column existed the second person to sign in on a phone saw
     * the first person's rows — their emergency contact, their rides, their
     * service history. No default: the compiler has to point at every write
     * site, because a row saved under the wrong owner is the bug this prevents.
     */
    val ownerId: String,

    /** Epoch millis of the service date — also the sort key, newest first. */
    val servicedAt: Long,

    /** Where it was done. Free text; the rider's own words. */
    val centre: String,

    /** Odometer reading at the time, whole km. */
    val odometerKm: Int,

    /** Optional — the design marks notes as not required. */
    val notes: String = "",
)
