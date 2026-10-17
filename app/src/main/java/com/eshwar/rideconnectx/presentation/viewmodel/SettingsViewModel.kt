package com.eshwar.rideconnectx.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eshwar.rideconnectx.core.di.ApplicationScope
import com.eshwar.rideconnectx.data.local.AppSettingsStore
import com.eshwar.rideconnectx.data.local.SessionDataStore
import com.eshwar.rideconnectx.data.local.UserPreferencesStore
import com.eshwar.rideconnectx.data.repository.ServiceRepository
import com.eshwar.rideconnectx.data.repository.VehicleRepository
import com.eshwar.rideconnectx.domain.model.AppSettings
import com.eshwar.rideconnectx.domain.model.DistanceUnit
import com.eshwar.rideconnectx.domain.model.UserSession
import com.eshwar.rideconnectx.domain.repository.BleRepository
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Screen 20 — Settings.
 *
 * Every setter writes immediately, which is what the design asks for: a toggle
 * saves the moment it moves, with no Save button anywhere on the screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: AppSettingsStore,
    private val serviceRepository: ServiceRepository,
    private val bleRepository: BleRepository,
    private val session: SessionDataStore,
    userPrefs: UserPreferencesStore,
    vehicleRepository: VehicleRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    private val rideLog: com.eshwar.rideconnectx.data.nav.RideLog,
) : ViewModel() {

    val rideRecorder: StateFlow<Boolean> =
        store.rideRecorder.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setRideRecorder(on: Boolean) = appScope.launch { store.setRideRecorder(on) }

    val gpsCountdown: StateFlow<Boolean> =
        store.gpsCountdown.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setGpsCountdown(on: Boolean) = appScope.launch { store.setGpsCountdown(on) }

    /** The recorded ride log, or null when nothing has been recorded yet. */
    fun rideLogFile(): java.io.File? = rideLog.fileOrNull()

    fun clearRideLog() = rideLog.clear()

    val settings: StateFlow<AppSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /**
     * Owned by Screen 18, shown here too. Reading the one store keeps the two
     * switches from disagreeing about the same preference.
     */
    val serviceReminders: StateFlow<Boolean> =
        serviceRepository.remindersEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val connectionState: StateFlow<ConnectionState> =
        bleRepository.connectionState
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionState.Idle)

    /** The vehicle the rider set up, for the Bluetooth section's summary row. */
    val vehicleName: StateFlow<String> = vehicleRepository.selection
        .map { it.vehicle?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /**
     * The account card at the top of Settings — the rider's first question was
     * "where is the profile", and a settings screen with no sign of who is
     * signed in is the reason it was hard to find.
     */
    val account: StateFlow<UserSession> = combine(
        userPrefs.session,
        userPrefs.riderName,
    ) { session, riderName ->
        session.copy(name = riderName.ifBlank { session.name })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSession())

    fun setDistanceUnit(unit: DistanceUnit) = appScope.launch { store.setDistanceUnit(unit) }
    fun setAutoStartNavigation(on: Boolean) = appScope.launch { store.setAutoStartNavigation(on) }

    fun setAutoConnect(on: Boolean) = appScope.launch { store.setAutoConnect(on) }

    fun setRideNotifications(on: Boolean) = appScope.launch { store.setRideNotifications(on) }
    fun setConnectionAlerts(on: Boolean) = appScope.launch { store.setConnectionAlerts(on) }
    fun setServiceReminders(on: Boolean) =
        appScope.launch { serviceRepository.setRemindersEnabled(on) }

    /**
     * Forgets the paired scooter: drops the link, then clears the stored
     * address so auto-reconnect stops finding it. Both halves are needed —
     * disconnecting alone would let the next app start reconnect.
     */
    fun forgetVehicle() = appScope.launch {
        bleRepository.disconnect()
        session.clearSession()
    }
}
