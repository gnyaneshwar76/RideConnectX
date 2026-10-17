package com.eshwar.rideconnectx.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelSampleDao {

    /** Oldest first — mileage is computed by walking forward through these. */
    @Query("SELECT * FROM fuel_samples WHERE ownerId = :owner ORDER BY odometerKm ASC")
    fun observeAll(owner: String): Flow<List<FuelSampleEntity>>

    @Query("SELECT * FROM fuel_samples WHERE ownerId = :owner ORDER BY id DESC LIMIT 1")
    suspend fun latest(owner: String): FuelSampleEntity?

    @Insert
    suspend fun insert(sample: FuelSampleEntity)

    /**
     * Keeps the table small. Mileage is about recent riding, and an unbounded
     * table would make the estimate drift toward a year-old average.
     */
    @Query("DELETE FROM fuel_samples WHERE ownerId = :owner AND id NOT IN (SELECT id FROM fuel_samples ORDER BY id DESC LIMIT :keep)")
    suspend fun trimTo(owner: String, keep: Int)
}
