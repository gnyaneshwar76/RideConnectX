package com.eshwar.rideconnectx.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One tankful, logged by the rider at the pump.
 *
 * This is the input to *measured* fuel economy, as opposed to the estimate
 * derived from watching the five-segment fuel bar fall. The cluster sends no
 * litres and no km/L, so the litres can only come from the rider — but once
 * they do, the arithmetic is exact rather than inferred:
 *
 * ```
 * km/L = distance covered on the previous tankful / litres put in now
 * ```
 *
 * A full-to-full fill puts back precisely the fuel burned since the last one,
 * which is what makes that division true. It is also what makes [fullTank]
 * load-bearing rather than decoration.
 *
 * Unused since 11 Sep 2026 — the scooter shows its own mileage on Trip A/B, so
 * the app stopped duplicating it. Table kept only to avoid a schema bump before
 * ship; see RideDatabase.
 */
@Entity(tableName = "refuels", indices = [Index(value = ["ownerId"])])
data class RefuelEntity(
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

    /** Epoch millis of the fill. Local calendar day, like `service_records`. */
    val filledAt: Long,

    /** Odometer at the pump, whole km. The distance between two fills. */
    val odometerKm: Int,

    /**
     * Trip B as the cluster read it at the pump, km.
     *
     * The rider resets Trip B on every fill, so this *is* the distance since
     * the previous fill — which is what makes the very first logged refuel
     * useful, before there is a second odometer reading to subtract from.
     * Zero means it was not recorded.
     */
    val tripBKm: Float,

    /** Litres put in. */
    val litres: Float,

    /** Optional, and only ever displayed — never part of the economy maths. */
    val costRupees: Float = 0f,

    /**
     * Whether the tank was filled to the brim.
     *
     * A partial fill breaks the full-to-full assumption in both directions: its
     * own litres do not account for the distance behind it, and the tank it
     * leaves behind is of unknown level, so the *next* fill's litres do not
     * either. Recorded honestly and excluded, rather than quietly averaged in.
     */
    val fullTank: Boolean = true,

    val notes: String = "",
)
