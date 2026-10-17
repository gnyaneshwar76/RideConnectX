package com.eshwar.rideconnectx.data.repository

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.eshwar.rideconnectx.MainActivity
import com.eshwar.rideconnectx.data.local.OwnerScope
import com.eshwar.rideconnectx.data.local.db.NotificationDao
import com.eshwar.rideconnectx.data.local.db.NotificationEntity
import com.eshwar.rideconnectx.data.local.db.NotificationKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app notifications.
 *
 * Entirely local: entries are raised by the app's own events, so the list is
 * available offline and in Guest Mode. Nothing is seeded — an account with no
 * activity genuinely has no notifications, and the screen shows its empty state.
 */
@Singleton
class NotificationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: NotificationDao,
    private val owner: OwnerScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val notifications: Flow<List<NotificationEntity>> =
        owner.current.flatMapLatest { dao.observeAll(it) }

    /** Drives the unread badge on the Dashboard bell. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadCount: Flow<Int> =
        owner.current.flatMapLatest { dao.observeUnreadCount(it) }

    /**
     * True while an unread entry of [kind] titled [title] exists, so the same
     * reminder is never listed twice — whatever the "already sent" record
     * says after an update (N13: two "Service overdue" entries).
     */
    suspend fun hasUnread(kind: NotificationKind, title: String, ownerId: String? = null): Boolean =
        dao.countUnread(ownerId ?: owner.currentId(), kind, title) > 0

    /**
     * Raises a notification. Call this from wherever the event happens.
     * [ownerId] pins it to the account the event was about, when known.
     */
    suspend fun notify(kind: NotificationKind, title: String, body: String, ownerId: String? = null) {
        dao.insert(
            NotificationEntity(
                ownerId = ownerId ?: owner.currentId(),
                createdAt = System.currentTimeMillis(),
                title = title,
                body = body,
                kind = kind,
            )
        )
        prune()
    }

    /** 90 days or the newest 500, whichever is smaller. Also call once on app start. */
    suspend fun prune(now: Long = System.currentTimeMillis()) =
        dao.prune(now - RETENTION_MS, KEEP_NEWEST)

    /**
     * The same event as a phone notification. Returns false when it could not
     * be posted — no permission (the Notifications page warns, N7) — so the
     * caller can try again later rather than lose it (N13). Tapping it opens
     * the screen for [kind], as tapping the in-app entry does.
     */
    fun postToPhone(
        channel: String,
        channelName: String,
        id: Int,
        title: String,
        body: String,
        kind: NotificationKind? = null,
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channel, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { kind?.let { putExtra(EXTRA_KIND, it.name) } }
        val open = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            // The app's own monochrome mark. A framework drawable here was the
            // likely reason reminders were posted and at once removed on the
            // phone (N13): SystemUI drops a notification whose icon it cannot
            // inflate, and it lands in history instead of the shade.
            .setSmallIcon(com.eshwar.rideconnectx.R.drawable.ic_launcher_monochrome)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return runCatching { NotificationManagerCompat.from(context).notify(id, notification) }.isSuccess
    }

    companion object {
        /** The [NotificationKind] a tapped phone notification should open. */
        const val EXTRA_KIND = "rcx_notification_kind"
        const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000
        const val KEEP_NEWEST = 500
    }

    suspend fun markRead(id: Long) = dao.markRead(owner.currentId(), id)

    /** Clears the current owner's whole list (N34). */
    suspend fun clearAll() = dao.deleteAllFor(owner.currentId())

    /** Deletes the given rows of the current owner's list (per-row delete). */
    suspend fun delete(ids: List<Long>) = dao.deleteByIdsFor(owner.currentId(), ids)

    suspend fun markAllRead() = dao.markAllRead(owner.currentId())
}
