package com.eshwar.rideconnectx.data.repository

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eshwar.rideconnectx.core.di.ApplicationScope
import com.eshwar.rideconnectx.core.di.ServiceEntryPoint
import com.eshwar.rideconnectx.data.local.ServicePreferencesStore
import com.eshwar.rideconnectx.data.local.db.NotificationKind
import com.eshwar.rideconnectx.domain.model.ServiceReminderRule
import com.eshwar.rideconnectx.domain.model.ServiceStatus
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raises service reminders: one in-app Notifications entry and one phone
 * notification per due-soon or overdue service (see [ServiceReminderRule]).
 *
 * Nothing did this before — the Service screen said "Overdue — 300 days over"
 * while the Notifications list stayed empty and the phone stayed silent
 * (rider, 26 Sep). Checked whenever the status changes while the app runs
 * (records added or edited, odometer, account switch) and once a day by
 * [ServiceReminderWorker] while it doesn't.
 */
@Singleton
class ServiceReminder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: ServiceRepository,
    private val prefs: ServicePreferencesStore,
    private val notifications: NotificationRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val lock = Mutex()
    private var started = false

    fun start() {
        if (started) return
        started = true
        appScope.launch {
            combine(service.ownedStatus, service.remindersEnabled) { owned, on -> Triple(owned.first, owned.second, on) }
                .collect { (owner, status, on) -> check(owner, status, on) }
        }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ServiceReminderWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    suspend fun checkOnce() {
        val (owner, status) = service.ownedStatus.first()
        check(owner, status, service.remindersEnabled.first())
    }

    private suspend fun check(owner: String, status: ServiceStatus, enabled: Boolean) = lock.withLock {
        val key = ServiceReminderRule.reminderKey(status, enabled) ?: return@withLock
        val inApp = ServiceReminderRule.shouldNotify(key, prefs.lastReminder(owner))
        // The phone copy has its own record, written only once it is really
        // posted. Sharing the in-app one meant a reminder raised while the
        // permission was off never reached the shade, even after it was
        // allowed (N13).
        val phone = ServiceReminderRule.shouldNotify(key, prefs.lastReminder(owner, PHONE))
        if (!inApp && !phone) return@withLock
        val title = if (status.isOverdue) "Service overdue" else "Service due soon"
        val body = listOf(
            if (status.isOverdue) "Your scooter is due for a service." else "Your next service is coming up.",
            ServiceReminderRule.detail(status),
        ).filter { it.isNotBlank() }.joinToString(" ")
        Log.d(TAG, "Reminder $key for $owner (inApp=$inApp phone=$phone)")
        if (inApp) {
            prefs.setLastReminder(owner, key)
            if (!notifications.hasUnread(NotificationKind.SERVICE, title, owner)) {
                notifications.notify(NotificationKind.SERVICE, title, body, ownerId = owner)
            }
        }
        if (phone && notifications.postToPhone(
                CHANNEL, "Service reminders", NOTIFICATION_ID, title, body, NotificationKind.SERVICE,
            )
        ) prefs.setLastReminder(owner, key, PHONE)
    }

    private companion object {
        const val TAG = "RCX-Service"
        const val CHANNEL = "service_reminders"
        const val NOTIFICATION_ID = 2
        const val WORK_NAME = "service-reminder"
        const val PHONE = "phone"
    }
}

/** The once-a-day check for when the app is not open. */
class ServiceReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, ServiceEntryPoint::class.java)
            .serviceReminder().checkOnce()
        return Result.success()
    }
}
