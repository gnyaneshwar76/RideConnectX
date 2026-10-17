package com.eshwar.rideconnectx.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceTaskDao {

    @Query("SELECT * FROM service_tasks WHERE ownerId = :owner ORDER BY position ASC, id ASC")
    fun observeAll(owner: String): Flow<List<ServiceTaskEntity>>

    @Query("SELECT COUNT(*) FROM service_tasks WHERE ownerId = :owner")
    suspend fun count(owner: String): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM service_tasks WHERE ownerId = :owner")
    suspend fun nextPosition(owner: String): Int

    @Insert
    suspend fun insert(task: ServiceTaskEntity): Long

    @Insert
    suspend fun insertAll(tasks: List<ServiceTaskEntity>)

    @Update
    suspend fun update(task: ServiceTaskEntity)

    /** Name and distance only, so an edited task keeps its place in the list. */
    @Query("UPDATE service_tasks SET label = :label, everyKm = :everyKm WHERE ownerId = :owner AND id = :id")
    suspend fun rename(owner: String, id: Long, label: String, everyKm: Int)

    @Delete
    suspend fun delete(task: ServiceTaskEntity)
}
