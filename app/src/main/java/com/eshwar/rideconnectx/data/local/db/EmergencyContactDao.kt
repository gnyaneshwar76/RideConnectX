package com.eshwar.rideconnectx.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyContactDao {

    /** Primary first — under stress the one that matters must be at the top. */
    @Query("SELECT * FROM emergency_contacts WHERE ownerId = :owner ORDER BY isPrimary DESC, createdAt ASC")
    fun observeAll(owner: String): Flow<List<EmergencyContactEntity>>

    @Query("SELECT * FROM emergency_contacts WHERE ownerId = :owner ORDER BY isPrimary DESC, createdAt ASC LIMIT 1")
    fun observePrimary(owner: String): Flow<EmergencyContactEntity?>

    @Query("SELECT * FROM emergency_contacts WHERE ownerId = :owner AND normalizedPhone = :phone LIMIT 1")
    suspend fun findByPhone(owner: String, phone: String): EmergencyContactEntity?

    @Query("SELECT * FROM emergency_contacts WHERE ownerId = :owner AND id = :id LIMIT 1")
    suspend fun findById(owner: String, id: Long): EmergencyContactEntity?

    @Query("SELECT COUNT(*) FROM emergency_contacts WHERE ownerId = :owner")
    suspend fun count(owner: String): Int

    /** Oldest surviving contact — who primary falls to when it is deleted. */
    @Query("SELECT * FROM emergency_contacts WHERE ownerId = :owner ORDER BY createdAt ASC LIMIT 1")
    suspend fun firstRemaining(owner: String): EmergencyContactEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(contact: EmergencyContactEntity): Long

    @Update
    suspend fun update(contact: EmergencyContactEntity)

    @Delete
    suspend fun delete(contact: EmergencyContactEntity)

    @Query("UPDATE emergency_contacts SET isPrimary = 0 WHERE ownerId = :owner")
    suspend fun clearPrimary(owner: String)

    @Query("UPDATE emergency_contacts SET isPrimary = 1 WHERE ownerId = :owner AND id = :id")
    suspend fun markPrimary(owner: String, id: Long)

    /** Only one contact may be primary, so the swap has to be atomic. */
    @Transaction
    suspend fun setPrimary(owner: String, id: Long) {
        clearPrimary(owner)
        markPrimary(owner, id)
    }
}
