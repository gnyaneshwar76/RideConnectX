package com.eshwar.rideconnectx.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RefuelDao {

    /** Newest first — the order the history is shown in. */
    @Query("SELECT * FROM refuels WHERE ownerId = :owner ORDER BY odometerKm DESC, filledAt DESC")
    fun observeAll(owner: String): Flow<List<RefuelEntity>>

    /**
     * Oldest first, which is the order the economy is computed in: each fill is
     * measured against the one before it.
     */
    @Query("SELECT * FROM refuels WHERE ownerId = :owner ORDER BY odometerKm ASC, filledAt ASC")
    fun observeChronological(owner: String): Flow<List<RefuelEntity>>

    /** The most recent fill by odometer — what a new entry is validated against. */
    @Query("SELECT * FROM refuels WHERE ownerId = :owner ORDER BY odometerKm DESC LIMIT 1")
    suspend fun latest(owner: String): RefuelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(refuel: RefuelEntity): Long

    @Update
    suspend fun update(refuel: RefuelEntity)

    @Delete
    suspend fun delete(refuel: RefuelEntity)
}
