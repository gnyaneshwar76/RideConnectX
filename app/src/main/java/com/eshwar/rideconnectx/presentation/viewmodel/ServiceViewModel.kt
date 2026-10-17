package com.eshwar.rideconnectx.presentation.viewmodel

import com.eshwar.rideconnectx.data.local.OwnerScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eshwar.rideconnectx.core.di.ApplicationScope
import com.eshwar.rideconnectx.data.local.AppSettingsStore
import com.eshwar.rideconnectx.data.local.db.ServiceRecordEntity
import com.eshwar.rideconnectx.data.repository.ServiceRepository
import com.eshwar.rideconnectx.domain.model.DistanceUnit
import com.eshwar.rideconnectx.domain.model.ServiceStatus
import com.eshwar.rideconnectx.domain.model.UpcomingTask
import com.eshwar.rideconnectx.domain.repository.BleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why a service record was refused, so the form can say so in plain words. */
sealed interface RecordError {
    /** The design's rule: a reading may not be lower than one already recorded. */
    data class OdometerTooLow(val minimumKm: Int) : RecordError
    /** A later record, or the scooter now, already reads lower than this. */
    data class OdometerTooHigh(val maximumKm: Int) : RecordError
    data object DateInFuture : RecordError
    data object OdometerMissing : RecordError
    /** A centre with no letters — an odometer typed into the wrong field. */
    data object CentreInvalid : RecordError
}

/**
 * A centre is a name, so it needs at least one letter. Blank is allowed and
 * stored as "Not recorded". Digits-only used to be saved as the centre when an
 * odometer reading went into the wrong field (rider, 26 Sep).
 */
fun isValidCentre(centre: String): Boolean = com.eshwar.rideconnectx.domain.model.FieldRules.isValidCentre(centre)

/**
 * The readings a service on [servicedAt] may carry. The odometer only goes up,
 * so it must be at least every record from that day or before, and at most
 * every later record. The scooter's current reading counts as "today": a floor
 * for a service logged today, a ceiling for one back-filled from the past.
 *
 * Before 21 Sep the floor was simply the highest reading ever seen, so a
 * service from last year could never be entered (rider, 20 Sep).
 *
 * @param others (servicedAt, odometerKm) of every *other* record
 * @return floor to ceiling; ceiling null when nothing bounds it from above
 */
fun odometerBounds(
    others: List<Pair<Long, Int>>,
    servicedAt: Long,
    currentKm: Int,
    now: Long = System.currentTimeMillis(),
): Pair<Int, Int?> {
    val today = sameDay(servicedAt, now)
    val floor = maxOf(
        others.filter { it.first <= servicedAt }.maxOfOrNull { it.second } ?: 0,
        if (today) currentKm else 0,
    )
    val ceiling = listOfNotNull(
        others.filter { it.first > servicedAt }.minOfOrNull { it.second },
        currentKm.takeIf { !today && it > 0 },
    ).minOrNull()
    return floor to ceiling
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}

@HiltViewModel
class ServiceViewModel @Inject constructor(
    private val repository: ServiceRepository,
    private val nearby: com.eshwar.rideconnectx.data.repository.NearbyServiceCentres,
    bleRepository: BleRepository,
    settings: AppSettingsStore,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    /** The rider's chosen units. Distances are stored in km and shown in this. */
    val distanceUnit: StateFlow<DistanceUnit> = settings.settings
        .map { it.distanceUnit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DistanceUnit.KM)

    val status: StateFlow<ServiceStatus> =
        repository.status.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceStatus())

    val records: StateFlow<List<ServiceRecordEntity>> =
        repository.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val upcomingTasks: StateFlow<List<UpcomingTask>> =
        repository.upcomingTasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val remindersEnabled: StateFlow<Boolean> =
        repository.remindersEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Lowest reading a new record may carry — never below what we already know. */
    val minimumOdometerKm: StateFlow<Int> =
        repository.highestRecordedOdometerKm.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val currentOdometerKm: StateFlow<Int> =
        repository.lastKnownOdometerKm.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Allowed readings for a record dated [servicedAt]; an edit ignores itself. */
    fun boundsFor(id: Long, servicedAt: Long): Pair<Int, Int?> = odometerBounds(
        others = records.value.filter { it.id != id }.map { it.servicedAt to it.odometerKm },
        servicedAt = servicedAt,
        currentKm = currentOdometerKm.value,
    )

    init {
        // Fills the tasks table on first run so the list is never blank.
        appScope.launch { repository.seedDefaultTasksIfEmpty() }

        // The screen has to work with the scooter out of range, so every valid
        // frame is cached while we do have it.
        viewModelScope.launch {
            bleRepository.telemetry.collect { t ->
                if (!t.isValid) return@collect
                repository.recordOdometer(t.odometerKm)
            }
        }
    }

    /**
     * Validates and saves. Returns null on success, or the reason it was refused.
     *
     * Runs on [appScope] because saving closes the form and can navigate away —
     * the mistake that has already cost this project three separate bugs.
     */
    fun save(
        id: Long,
        servicedAt: Long,
        centre: String,
        odometerKm: Int?,
        notes: String,
    ): RecordError? {
        if (!isValidCentre(centre)) return RecordError.CentreInvalid
        if (odometerKm == null || odometerKm <= 0) return RecordError.OdometerMissing
        if (servicedAt > System.currentTimeMillis()) return RecordError.DateInFuture

        val (floor, ceiling) = boundsFor(id, servicedAt)
        if (odometerKm < floor) return RecordError.OdometerTooLow(floor)
        if (ceiling != null && odometerKm > ceiling) return RecordError.OdometerTooHigh(ceiling)

        val record = ServiceRecordEntity(
            id = id,
            // Stamped with the real owner by ServiceRepository on write.
            ownerId = OwnerScope.DRAFT,
            servicedAt = servicedAt,
            centre = centre.trim().ifBlank { "Not recorded" },
            odometerKm = odometerKm,
            notes = notes.trim(),
        )
        appScope.launch {
            if (id == 0L) repository.addRecord(record) else repository.updateRecord(record)
        }
        return null
    }

    fun delete(record: ServiceRecordEntity) {
        appScope.launch { repository.deleteRecord(record) }
    }

    /** Adds a task, or edits one. Blank labels and non-positive intervals are ignored. */
    fun saveTask(id: Long, label: String, everyKm: Int?) {
        appScope.launch { repository.saveTask(id, label, everyKm ?: 0) }
    }

    fun deleteTask(id: Long) {
        appScope.launch { repository.deleteTask(id) }
    }

    private val _nearbyCentres = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    /** For the centre picker (N14); empty until the field is first opened. */
    val nearbyCentres: StateFlow<List<String>> = _nearbyCentres
    private var loadingNearby = false

    /** One lookup per open of the field; [nearby] caches the answer. */
    fun loadNearbyCentres() {
        if (loadingNearby) return
        loadingNearby = true
        viewModelScope.launch {
            try {
                _nearbyCentres.value = nearby.find()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // No list; the field still takes typed text.
            } finally {
                loadingNearby = false
            }
        }
    }

    /** Last (state, city) picked for a service centre, so the picker starts there. */
    val centrePlace: StateFlow<Pair<String, String>> = repository.centrePlace
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "" to "")

    fun rememberCentrePlace(state: String, city: String) {
        appScope.launch { repository.setCentrePlace(state, city) }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        appScope.launch { repository.setRemindersEnabled(enabled) }
    }
}
