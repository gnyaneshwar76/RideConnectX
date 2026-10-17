package com.eshwar.rideconnectx.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Aggregate figures for the summary cards. */
data class RideTotals(
    val trips: Int,
    val totalMeters: Int,
    val totalMillis: Long,
    val avgSpeed: Double,
)

/** One bar in the Statistics chart. */
data class RideBucket(
    val label: String,
    val meters: Int,
)

/** Every read hides DELETED tombstones; totals count PENDING + KEPT. */
@Dao
interface RideDao {

    @Insert
    suspend fun insert(ride: RideEntity): Long

    @Update
    suspend fun update(ride: RideEntity)

    @Query("SELECT * FROM rides WHERE ownerId = :owner AND keepState <> 'DELETED' ORDER BY startedAt DESC")
    fun observeAll(owner: String): Flow<List<RideEntity>>

    @Query("SELECT * FROM rides WHERE ownerId = :owner AND keepState <> 'DELETED' AND startedAt >= :since ORDER BY startedAt DESC LIMIT 500")
    fun observeSince(owner: String, since: Long): Flow<List<RideEntity>>

    @Query(
        """
        SELECT COUNT(*)                                   AS trips,
               COALESCE(SUM(distanceMeters), 0)           AS totalMeters,
               COALESCE(SUM(durationMillis), 0)           AS totalMillis,
               COALESCE(SUM(distanceMeters) * 3600.0 / NULLIF(SUM(durationMillis), 0), 0.0) AS avgSpeed
        FROM rides
        WHERE ownerId = :owner AND keepState <> 'DELETED' AND startedAt >= :since
        """
    )
    fun observeTotals(owner: String, since: Long): Flow<RideTotals>

    /**
     * Distance grouped by day, for the chart.
     * `startedAt` is epoch millis, so it is divided down to a day index and
     * rendered back as a local date by SQLite.
     */
    @Query(
        """
        SELECT COALESCE(strftime(:format, startedAt / 1000, 'unixepoch', 'localtime'), '') AS label,
               SUM(distanceMeters)                                           AS meters
        FROM rides
        WHERE ownerId = :owner AND keepState <> 'DELETED' AND startedAt >= :since
        GROUP BY label
        ORDER BY MIN(startedAt) ASC
        """
    )
    fun observeBuckets(owner: String, since: Long, format: String): Flow<List<RideBucket>>

    @Query("SELECT * FROM rides WHERE id = :id AND ownerId = :owner AND keepState <> 'DELETED'")
    fun observeOne(owner: String, id: Long): Flow<RideEntity?>

    @Query("SELECT * FROM rides WHERE id = :id")
    suspend fun get(id: Long): RideEntity?

    /** Idempotent: only a PENDING ride changes. */
    @Query("UPDATE rides SET keepState = 'KEPT', expiresAt = NULL WHERE id = :id AND keepState = 'PENDING'")
    suspend fun keep(id: Long)

    @Query("UPDATE rides SET keepState = 'DELETED' WHERE id = :id")
    suspend fun tombstone(id: Long)

    @Query("DELETE FROM rides WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Oldest first, so the earliest rides get the free cloud slots. */
    @Query("SELECT * FROM rides WHERE ownerId = :owner AND keepState = 'KEPT' AND cloudState = 0 ORDER BY endedAt ASC")
    suspend fun toUpload(owner: String): List<RideEntity>

    @Query("SELECT * FROM rides WHERE ownerId = :owner AND keepState = 'DELETED' AND cloudState = 1")
    suspend fun tombstones(owner: String): List<RideEntity>

    /** A cloud slot was freed: rides that found the cloud full try again. */
    @Query("UPDATE rides SET cloudState = 0 WHERE ownerId = :owner AND keepState = 'KEPT' AND cloudState = 2")
    suspend fun reopenPhoneOnly(owner: String)

    @Query("UPDATE rides SET cloudState = :state, cloudSlot = :slot WHERE id = :id")
    suspend fun setCloud(id: Long, state: Int, slot: String?)

    /** All owners. Never touches KEPT rows. Mirrors RideKeepRules.isPurgeable. */
    @Query("DELETE FROM rides WHERE keepState = 'PENDING' AND expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun purge(now: Long): Int

    @Query("SELECT COUNT(*) FROM rides WHERE ownerId = :owner AND startedAt = :startedAt")
    suspend fun countStartedAt(owner: String, startedAt: Long): Int

    @Query("DELETE FROM rides WHERE ownerId = :owner")
    suspend fun clear(owner: String)
}
