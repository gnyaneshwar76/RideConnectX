package com.eshwar.rideconnectx.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert
    suspend fun insert(notification: NotificationEntity): Long

    @Query("SELECT * FROM notifications WHERE ownerId = :owner ORDER BY createdAt DESC LIMIT 500")
    fun observeAll(owner: String): Flow<List<NotificationEntity>>

    /** Retention: rows older than [cutoff] and anything beyond the newest [keep] go (N91 default: 90 days or 500). */
    @Query(
        "DELETE FROM notifications WHERE createdAt < :cutoff OR id NOT IN " +
            "(SELECT id FROM notifications ORDER BY createdAt DESC, id DESC LIMIT :keep)"
    )
    suspend fun prune(cutoff: Long, keep: Int)

    @Query("SELECT COUNT(*) FROM notifications WHERE ownerId = :owner AND unread = 1")
    fun observeUnreadCount(owner: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM notifications WHERE ownerId = :owner AND unread = 1 AND kind = :kind AND title = :title")
    suspend fun countUnread(owner: String, kind: NotificationKind, title: String): Int

    @Query("UPDATE notifications SET unread = 0 WHERE ownerId = :owner AND id = :id")
    suspend fun markRead(owner: String, id: Long)

    @Query("UPDATE notifications SET unread = 0 WHERE ownerId = :owner")
    suspend fun markAllRead(owner: String)

    @Query("DELETE FROM notifications WHERE ownerId = :owner")
    suspend fun clear(owner: String)

    /** Same rows as [clear]; the name rcx-nav's clearAll() calls. */
    @Query("DELETE FROM notifications WHERE ownerId = :ownerId")
    suspend fun deleteAllFor(ownerId: String)

    /** Deletes only the given ids that belong to [ownerId]; other owners' rows are never touched. */
    @Query("DELETE FROM notifications WHERE ownerId = :ownerId AND id IN (:ids)")
    suspend fun deleteByIdsFor(ownerId: String, ids: List<Long>)
}
