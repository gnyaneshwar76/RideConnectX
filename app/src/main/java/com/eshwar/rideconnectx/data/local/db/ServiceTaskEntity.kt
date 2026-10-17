package com.eshwar.rideconnectx.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One item on the "Upcoming Tasks" list.
 *
 * These used to be a hardcoded enum of four — engine oil, brakes, tyres,
 * battery — which meant the list could not be edited, added to or pruned. It is
 * a table now. The same four are seeded on first run so nothing looks empty,
 * but every one of them can be changed or deleted, and a rider who services
 * something the app never thought of can add it.
 */
@Entity(tableName = "service_tasks", indices = [Index(value = ["ownerId"])])
data class ServiceTaskEntity(
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

    val label: String,

    /** How often it comes round, in kilometres. */
    val everyKm: Int,

    /** Sort order on the screen; lower comes first. */
    val position: Int = 0,
)
