package com.eshwar.rideconnectx.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One reading of "odometer X, fuel bar Y", taken whenever the fuel bar moves.
 *
 * The scooter does not transmit fuel economy — it sends an odometer and a
 * five-segment fuel bar, nothing else. Mileage therefore has to be *derived*:
 * how far the vehicle went between one segment dropping and the next.
 *
 * Only the transitions are stored, not every frame. A sample is written when
 * the segment count changes, so a tankful produces a handful of rows rather
 * than thousands.
 */
@Entity(tableName = "fuel_samples", indices = [Index(value = ["ownerId"])])
data class FuelSampleEntity(
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

    /** Odometer at the moment the bar changed, whole km. */
    val odometerKm: Int,

    /** Fuel bar as the cluster draws it, 0..5. */
    val segments: Int,

    val recordedAt: Long = System.currentTimeMillis(),
)
