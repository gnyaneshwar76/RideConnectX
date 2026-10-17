package com.eshwar.rideconnectx.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Someone to reach when the rider is in trouble.
 *
 * [normalizedPhone] carries a unique index because the design forbids duplicate
 * contacts, and "same number, different spacing" is the way duplicates actually
 * get in. It is derived, never typed — see `SafetyRepository.normalize`.
 */
@Entity(
    tableName = "emergency_contacts",
    indices = [Index(value = ["ownerId", "normalizedPhone"], unique = true)],
)
data class EmergencyContactEntity(
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

    val name: String,

    /** As the rider typed it — what gets shown. */
    val phone: String,

    /** Digits and a leading `+` only — what duplicates are judged on. */
    val normalizedPhone: String,

    /**
     * The one SOS calls first. Exactly one contact holds this; promoting
     * another demotes the previous one.
     */
    val isPrimary: Boolean = false,

    /** Epoch millis, so the list keeps a stable order. */
    val createdAt: Long = System.currentTimeMillis(),
)
