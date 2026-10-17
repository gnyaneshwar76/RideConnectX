package com.eshwar.rideconnectx.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eshwar.rideconnectx.core.di.ApplicationScope
import com.eshwar.rideconnectx.data.local.OwnerScope
import com.eshwar.rideconnectx.data.local.UserPreferencesStore
import com.eshwar.rideconnectx.data.local.db.CloudState
import com.eshwar.rideconnectx.data.local.db.KeepState
import com.eshwar.rideconnectx.data.local.db.RideBucket
import com.eshwar.rideconnectx.data.local.db.RideDao
import com.eshwar.rideconnectx.data.local.db.RideEntity
import com.eshwar.rideconnectx.data.local.db.RideTotals
import com.eshwar.rideconnectx.data.remote.FirestoreUserDataSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** The ranges offered by the Statistics filter. */
enum class StatsPeriod(val label: String) {
    TODAY("Today"),
    WEEK("This Week"),
    MONTH("This Month"),
    YEAR("This Year");

    /** Epoch millis for the start of this range. */
    fun since(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        when (this) {
            TODAY -> Unit
            WEEK -> cal.add(Calendar.DAY_OF_YEAR, -6)
            MONTH -> cal.add(Calendar.DAY_OF_YEAR, -29)
            YEAR -> cal.add(Calendar.MONTH, -11)
        }
        return cal.timeInMillis
    }

    /** SQLite strftime pattern controlling how the chart is bucketed. */
    val bucketFormat: String
        get() = when (this) {
            TODAY -> "%H"       // hour of day
            // SQLite has no %a (weekday name): it returned NULL and Statistics
            // crashed on the first recorded ride. %w is 0 (Sun)..6, named in
            // [label].
            WEEK -> "%w"
            MONTH -> "%d/%m"    // day/month
            YEAR -> "%m"        // month number
        }

    /** The chart label for a bucket key from [bucketFormat]. */
    fun label(key: String): String {
        val n = key.toIntOrNull() ?: return key
        return when (this) {
            WEEK -> listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").getOrElse(n) { key }
            YEAR -> listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                .getOrElse(n - 1) { key }
            else -> key
        }
    }
}

/** Pure rules for keep / purge / cloud slots, so they can be unit-tested. */
object RideKeepRules {
    const val EXPIRY_MS = 30L * 24 * 3600 * 1000
    const val MAX_CLOUD = 10

    /** A PENDING ride goes 30 days after it ended unless the rider keeps it. */
    fun expiresAt(endedAt: Long): Long = endedAt + EXPIRY_MS

    /** Same test as RideDao.purge. KEPT and DELETED are never purged. */
    fun isPurgeable(keepState: String, expiresAt: Long?, now: Long): Boolean =
        keepState == KeepState.PENDING && expiresAt != null && expiresAt <= now

    /** Lowest free cloud slot r0..r9, or null when the cloud holds 10. */
    fun pickSlot(used: Set<String>): String? =
        (0 until MAX_CLOUD).map { "r$it" }.firstOrNull { it !in used }

    /** A slot whose ride has this start time: the upload already happened (lost answer). */
    fun slotWithStart(slots: Map<String, Long>, startedAt: Long): String? =
        slots.entries.firstOrNull { it.value == startedAt }?.key
}

/**
 * The three cloud calls the upload needs. rcx-account implements them on
 * FirestoreUserDataSource (fixed slot ids r0..r9, create-only).
 */
interface RideCloud {
    /** Existing slot docs: slot id to startedAt. */
    suspend fun fetchRideSlots(uid: String): Result<Map<String, Long>>
    suspend fun createRideInSlot(uid: String, slot: String, ride: Map<String, Any>): Result<Unit>
    suspend fun deleteRideSlot(uid: String, slot: String): Result<Unit>
}

/**
 * Ride history.
 *
 * Room is the source of truth so Statistics works with no network. A new ride
 * is PENDING (auto-deleted 30 days after it ended). The rider can Keep it
 * (then it may go to the cloud, max 10 rides) or Delete it.
 */
@Singleton
class RideRepository @Inject constructor(
    private val dao: RideDao,
    private val prefs: UserPreferencesStore,
    private val owner: OwnerScope,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    private val cloud: com.eshwar.rideconnectx.data.remote.FirestoreUserDataSource,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeRides(period: StatsPeriod): Flow<List<RideEntity>> =
        owner.current.flatMapLatest { dao.observeSince(it, period.since()) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTotals(period: StatsPeriod): Flow<RideTotals> =
        owner.current.flatMapLatest { dao.observeTotals(it, period.since()) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeBuckets(period: StatsPeriod): Flow<List<RideBucket>> =
        owner.current.flatMapLatest {
            dao.observeBuckets(it, period.since(), period.bucketFormat)
                .map { list -> list.map { b -> b.copy(label = period.label(b.label)) } }
        }

    /** One ride for the detail page; null once deleted or if it belongs to someone else. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeRide(id: Long): Flow<RideEntity?> =
        owner.current.flatMapLatest { dao.observeOne(it, id) }

    private val syncLock = Mutex()

    /** Saves a finished GPS ride locally as PENDING. */
    suspend fun recordRide(ride: RideEntity): Long {
        val ownerId = owner.currentId()
        // The same ride can arrive twice (a saved in-progress ride replayed
        // after the app was killed); its start time is its identity.
        if (dao.countStartedAt(ownerId, ride.startedAt) > 0) return -1L
        return dao.insert(
            ride.copy(
                ownerId = ownerId,
                keepState = KeepState.PENDING,
                expiresAt = RideKeepRules.expiresAt(ride.endedAt),
            )
        )
    }

    /** Keep: stops the 30-day timer, then uploads when signed in. Safe to repeat. */
    suspend fun keep(id: Long) {
        dao.keep(id)
        scheduleUpload()
    }

    /**
     * Delete. A ride that is in the cloud becomes a hidden tombstone until the
     * cloud copy is gone (retried by the upload job when offline); any other
     * ride is removed at once. Waits for a running upload so it cannot be orphaned.
     */
    suspend fun delete(id: Long) {
        val tombstoned = syncLock.withLock {
            val ride = dao.get(id) ?: return
            if (ride.cloudState == CloudState.UPLOADED && ride.cloudSlot != null) {
                dao.tombstone(id); true
            } else {
                dao.deleteById(id); false
            }
        }
        if (tombstoned) { syncPending(); scheduleUpload() }
    }

    /** Removes expired PENDING rides of every owner. Returns how many. */
    suspend fun purgeExpired(now: Long = System.currentTimeMillis()): Int = dao.purge(now)

    /** Once at app start: schedules the daily purge, purges now, queues an upload. */
    fun startMaintenance() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "ride-purge", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RidePurgeWorker>(1, TimeUnit.DAYS).build(),
        )
        appScope.launch { runCatching { purgeExpired() } }
        scheduleUpload()
    }

    /** Unique one-time upload job; runs when there is a network. Also call after sign-in. */
    fun scheduleUpload() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "ride-upload", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RideUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    /**
     * Cloud tidy-up for the signed-in account: first finish deletes (tombstones),
     * then upload KEPT rides, oldest first, into the lowest free slot r0..r9.
     * Guests and PENDING rides never upload. Returns false when it should be
     * retried later (offline, or a refused write that was not a slot race).
     */
    suspend fun syncPending(): Boolean = syncLock.withLock {
        val session = prefs.session.first()
        if (!session.syncsToCloud || session.uid.isBlank()) return true
        syncRides(dao, cloud, session.uid)
    }

}

private fun RideEntity.toCloud(): Map<String, Any> = mapOf(
    "startedAt" to startedAt, "endedAt" to endedAt, "distanceMeters" to distanceMeters,
    "durationMillis" to durationMillis, "topSpeedKmh" to topSpeedKmh, "avgSpeedKmh" to avgSpeedKmh,
    "title" to title, "vehicleId" to vehicleId, "route" to route, "source" to source,
)

/** The cloud sync itself (tombstones, slot pick, slot race, full cloud). Split out so it can be tested. */
internal suspend fun syncRides(dao: RideDao, cloud: RideCloud, uid: String): Boolean {
    var freed = false
    for (t in dao.tombstones(uid)) {
        val slot = t.cloudSlot ?: continue
        if (cloud.deleteRideSlot(uid, slot).isFailure) return false
        dao.deleteById(t.id)
        freed = true
    }
    // A slot opened up: rides that found the cloud full get another try.
    if (freed) dao.reopenPhoneOnly(uid)

    val pending = dao.toUpload(uid)
    if (pending.isEmpty()) return true
    var slots = cloud.fetchRideSlots(uid).getOrElse { return false }

    for (ride in pending) {
        val already = RideKeepRules.slotWithStart(slots, ride.startedAt)
        if (already != null) { dao.setCloud(ride.id, CloudState.UPLOADED, already); continue }
        // Each failed try either finds the slot taken by another phone (next slot) or gives up.
        for (attempt in 0 until RideKeepRules.MAX_CLOUD) {
            val slot = RideKeepRules.pickSlot(slots.keys)
            if (slot == null) { dao.setCloud(ride.id, CloudState.PHONE_ONLY, null); break }
            if (cloud.createRideInSlot(uid, slot, ride.toCloud()).isSuccess) {
                slots = slots + (slot to ride.startedAt)
                dao.setCloud(ride.id, CloudState.UPLOADED, slot)
                break
            }
            slots = cloud.fetchRideSlots(uid).getOrElse { return false }
            RideKeepRules.slotWithStart(slots, ride.startedAt)?.let {   // our write got through, only the answer was lost
                dao.setCloud(ride.id, CloudState.UPLOADED, it); break
            }
            if (slot !in slots) return false   // still free: a real failure, not a race
        }
    }
    return true
}

/** Own entry point so this stays inside data/repository. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RideEntryPoint {
    fun rideRepository(): RideRepository
}

/** Daily: deletes PENDING rides older than 30 days. */
class RidePurgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, RideEntryPoint::class.java)
            .rideRepository().purgeExpired()
        return Result.success()
    }
}

/** Uploads KEPT rides and finishes cloud deletes; retries with backoff while offline. */
class RideUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (EntryPointAccessors.fromApplication(applicationContext, RideEntryPoint::class.java)
                .rideRepository().syncPending()
        ) Result.success() else Result.retry()
}
