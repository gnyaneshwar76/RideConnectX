package com.eshwar.rideconnectx.data.repository

import com.eshwar.rideconnectx.data.local.OwnerScope
import com.eshwar.rideconnectx.data.local.ServicePreferencesStore
import com.eshwar.rideconnectx.data.local.db.ServiceRecordDao
import com.eshwar.rideconnectx.data.local.db.ServiceRecordEntity
import com.eshwar.rideconnectx.data.local.db.ServiceTaskDao
import com.eshwar.rideconnectx.data.local.db.ServiceTaskEntity
import com.eshwar.rideconnectx.domain.model.ServiceStatus
import com.eshwar.rideconnectx.domain.model.UpcomingTask
import com.eshwar.rideconnectx.domain.model.isValidTaskName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service history and the schedule derived from it.
 *
 * Entirely local — Room for the records, DataStore for the reminder settings —
 * so the screen works offline and in Guest Mode, which is what the design asks
 * for. Nothing is seeded: a rider with no records genuinely has no history.
 */
@Singleton
class ServiceRepository @Inject constructor(
    private val dao: ServiceRecordDao,
    private val taskDao: ServiceTaskDao,
    private val prefs: ServicePreferencesStore,
    private val owner: OwnerScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val records: Flow<List<ServiceRecordEntity>> =
        owner.current.flatMapLatest { dao.observeAll(it) }

    /** Re-queried whenever the owner changes, never cached across a sign-out. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val latestRecord = owner.current.flatMapLatest { dao.observeLatest(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val allTasks = owner.current.flatMapLatest { taskDao.observeAll(it) }

    val remindersEnabled: Flow<Boolean> = prefs.remindersEnabled

    val status: Flow<ServiceStatus> = statusFrom(latestRecord)

    /**
     * The status with the account it was worked out for. Pairing [OwnerScope]
     * with [status] separately could match a new account with the previous
     * one's service for a moment — enough to send a reminder to the wrong one.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val ownedStatus: Flow<Pair<String, ServiceStatus>> =
        owner.current.flatMapLatest { id -> statusFrom(dao.observeLatest(id)).map { id to it } }

    private fun statusFrom(latestRecord: Flow<ServiceRecordEntity?>): Flow<ServiceStatus> = combine(
        latestRecord,
        prefs.lastKnownOdometerKm,
        prefs.intervalKm,
        prefs.intervalDays,
        prefs.remindBeforeKm,
    ) { latest, odo, intervalKm, intervalDays, remindBeforeKm ->
        ServiceStatus(
            lastServiceOdometerKm = latest?.odometerKm,
            lastServiceAt = latest?.servicedAt,
            // A service entered at a higher reading than we have ever seen is
            // still the truth about the vehicle, so it raises the current one.
            currentOdometerKm = maxOf(odo, latest?.odometerKm ?: 0),
            intervalKm = intervalKm,
            intervalDays = intervalDays,
            remindBeforeKm = remindBeforeKm,
        )
    }

    /**
     * The rider's checks, each measured from the last service.
     *
     * Read from a table now rather than a fixed enum, so the list can be
     * edited, added to and pruned. [seedDefaultTasksIfEmpty] fills it the first
     * time so it is never blank on a fresh install.
     */
    val upcomingTasks: Flow<List<UpcomingTask>> =
        combine(status, allTasks) { s, tasks ->
            tasks.map { task ->
                val remaining = s.lastServiceOdometerKm?.let { last ->
                    if (s.currentOdometerKm <= 0) null
                    else last + task.everyKm - s.currentOdometerKm
                }
                UpcomingTask(
                    id = task.id,
                    label = task.label,
                    everyKm = task.everyKm,
                    remainingKm = remaining,
                )
            }
        }

    /**
     * Puts the four standard checks in on first run.
     *
     * Seeding rather than hardcoding: they are a helpful starting point, not a
     * fixed truth, and an Access owner who has their brakes done every 2,000 km
     * should be able to say so.
     */
    private val seedLock = kotlinx.coroutines.sync.Mutex()

    suspend fun seedDefaultTasksIfEmpty() = seedLock.withLock {
        // Locked so two screens opening together cannot both see an empty table.
        val ownerId = owner.currentId()
        if (taskDao.count(ownerId) > 0) return@withLock
        taskDao.insertAll(
            // Ready plan for the connected model (Access schedule when unknown); see ModelServicePlan.
            com.eshwar.rideconnectx.domain.model.ModelServicePlan
                .forFamily(com.eshwar.rideconnectx.domain.ProtocolEngine.profile.family).tasks
                .mapIndexed { i, (label, km) -> ServiceTaskEntity(ownerId = ownerId, label = label, everyKm = km, position = i) }
        )
    }

    suspend fun saveTask(id: Long, label: String, everyKm: Int) {
        val ownerId = owner.currentId()
        val clean = label.trim()
        if (!isValidTaskName(clean) || everyKm <= 0) return
        if (id == 0L) {
            taskDao.insert(
                ServiceTaskEntity(
                    ownerId = ownerId,
                    label = clean,
                    everyKm = everyKm,
                    position = taskDao.nextPosition(ownerId),
                )
            )
        } else {
            // A whole-row update reset the position to 0, so an edited task
            // jumped to the top of the list (AUD-5).
            taskDao.rename(ownerId, id, clean, everyKm)
        }
    }

    suspend fun deleteTask(id: Long) {
        taskDao.delete(ServiceTaskEntity(id = id, ownerId = owner.currentId(), label = "", everyKm = 0))
    }

    /**
     * The highest odometer reading on record — what a new entry must not go
     * below. Exposed so the form can say *why* a reading was rejected.
     */
    /** What the scooter itself last reported, 0 when never seen. */
    val lastKnownOdometerKm: Flow<Int> = prefs.lastKnownOdometerKm

    val highestRecordedOdometerKm: Flow<Int> = combine(
        latestRecord,
        prefs.lastKnownOdometerKm,
    ) { latest, odo -> maxOf(latest?.odometerKm ?: 0, odo) }

    /**
     * A record's own reading is **not** written into the odometer cache.
     *
     * It used to be, and that made a mistyped entry permanent: the cache only
     * moves forward, so deleting the bad record left the inflated reading
     * behind and the screen was stuck saying "Overdue" with no way back short
     * of clearing app data. Found on the emulator, 13 August 2026 — a record
     * entered as 160,290 km could not be undone.
     *
     * Nothing is lost by this: [status] already takes the higher of the cached
     * odometer and the latest record, so a record still raises the current
     * reading for as long as it exists. The cache stays what it claims to be —
     * what the *vehicle* last reported.
     */
    // The owner is stamped here, not in the screen: the UI builds a draft with
    // OwnerScope.DRAFT and this is the last point before the row is stored.
    suspend fun addRecord(record: ServiceRecordEntity) {
        dao.insert(record.copy(ownerId = owner.currentId()))
    }

    suspend fun updateRecord(record: ServiceRecordEntity) {
        dao.update(record.copy(ownerId = owner.currentId()))
    }

    suspend fun deleteRecord(record: ServiceRecordEntity) = dao.delete(record)

    suspend fun setRemindersEnabled(enabled: Boolean) = prefs.setRemindersEnabled(enabled)

    val remindBeforeKm: Flow<Int> = prefs.remindBeforeKm
    suspend fun setRemindBeforeKm(km: Int) = prefs.setRemindBeforeKm(km)

    val centrePlace: Flow<Pair<String, String>> = prefs.centrePlace
    suspend fun setCentrePlace(state: String, city: String) = prefs.setCentrePlace(state, city)

    /** Called whenever a valid telemetry frame arrives. */
    suspend fun recordOdometer(km: Int) = prefs.recordOdometer(km)
}
