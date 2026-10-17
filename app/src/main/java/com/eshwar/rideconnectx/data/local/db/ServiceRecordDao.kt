package com.eshwar.rideconnectx.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceRecordDao {

    @Query("SELECT * FROM service_records WHERE ownerId = :owner ORDER BY servicedAt DESC")
    fun observeAll(owner: String): Flow<List<ServiceRecordEntity>>

    /**
     * The most recent service, which is what the next one is measured from.
     * "Most recent" is by odometer, not by date — a record entered late must not
     * push the next service backwards.
     */
    @Query("SELECT * FROM service_records WHERE ownerId = :owner ORDER BY odometerKm DESC LIMIT 1")
    fun observeLatest(owner: String): Flow<ServiceRecordEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ServiceRecordEntity): Long

    @Update
    suspend fun update(record: ServiceRecordEntity)

    @Delete
    suspend fun delete(record: ServiceRecordEntity)
}
