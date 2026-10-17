package com.eshwar.rideconnectx.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eshwar.rideconnectx.data.local.AppSettingsStore
import com.eshwar.rideconnectx.data.local.UserPreferencesStore
import com.eshwar.rideconnectx.domain.model.DistanceUnit
import com.eshwar.rideconnectx.data.repository.ServiceRepository
import com.eshwar.rideconnectx.data.repository.VehicleRepository
import com.eshwar.rideconnectx.domain.model.ServiceStatus
import com.eshwar.rideconnectx.domain.model.Vehicle
import com.eshwar.rideconnectx.domain.model.VehicleColor
import com.eshwar.rideconnectx.domain.repository.BleRepository
import com.eshwar.rideconnectx.domain.usecase.ConnectToDeviceUseCase
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import com.eshwar.rideconnectx.presentation.mvi.UiEffect
import com.eshwar.rideconnectx.presentation.mvi.UiIntent
import com.eshwar.rideconnectx.presentation.mvi.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

data class DashboardUiState(
    val connectionState: ConnectionState = ConnectionState.Idle,
    val deviceAddress: String? = null,
    val deviceName: String = "Scooter",
    // The scooter does not transmit speed in its 0x37 frame, so this stays null
    // on real hardware. Only the simulator ever fills it.
    val speed: Int? = null,
    // Phone battery and signal belong on the vehicle cluster, not here — the
    // dashboard is about the ride, and only one model is electric.
    /** Fuel bar, 0..5 segments, matching the cluster's E-F bar. */
    val fuelSegments: Int? = null,
    val fuel: Int? = null,
    val odometer: Int? = null,
    val tripAKm: Float? = null,
    val tripBKm: Float? = null,
    /** Greeting name — the Google account name or the guest's chosen name. */
    val riderName: String = "Rider",
    /** Short header name; falls back to [riderName] when unset. */
    val nickname: String = "",
    /** The model and paint chosen during setup. */
    val vehicle: Vehicle? = null,
    val vehicleColor: VehicleColor? = null,
) : UiState {
    val isConnected: Boolean get() = connectionState is ConnectionState.Connected
}

sealed class DashboardUiIntent : UiIntent {
    object Connect : DashboardUiIntent()
    object Disconnect : DashboardUiIntent()
}

sealed class DashboardUiEffect : UiEffect {
    data class ShowError(val message: String) : DashboardUiEffect()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val connectToDeviceUseCase: ConnectToDeviceUseCase,
    private val bleRepository: BleRepository,
    private val userPrefs: UserPreferencesStore,
    private val vehicleRepository: VehicleRepository,
    private val serviceRepository: ServiceRepository,
    notifications: com.eshwar.rideconnectx.data.repository.NotificationRepository,
    appSettings: AppSettingsStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** The badge on the bell. */
    val unreadCount: StateFlow<Int> = notifications.unreadCount.stateIn(
        viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), 0,
    )

    /** Feeds the dashboard's Service Reminder card. */
    val serviceStatus: StateFlow<ServiceStatus> = serviceRepository.status.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ServiceStatus(),
    )

    /** The rider's chosen units, so the dashboard agrees with Screen 18. */
    val distanceUnit: StateFlow<DistanceUnit> = appSettings.settings
        .map { it.distanceUnit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DistanceUnit.KM)

    private val TAG = "RCX-BLE"
    private val deviceAddress: String? = savedStateHandle["deviceAddress"]

    private val _uiState = MutableStateFlow(DashboardUiState(deviceAddress = deviceAddress))
    val uiState = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<DashboardUiEffect>()
    val effect = _effect.asSharedFlow()

    init {
        observeConnection()
        observeTelemetry()
        observeRider()
    }

    /**
     * The dashboard used to only ever see a connection it started itself, via a
     * `deviceAddress` argument the nav graph never actually passed — so it read
     * "Offline" even with the vehicle connected. It now follows the shared state.
     */
    private fun observeConnection() {
        viewModelScope.launch {
            bleRepository.connectionState.collect { state ->
                _uiState.update {
                    it.copy(
                        connectionState = state,
                        deviceAddress = (state as? ConnectionState.Connected)?.address
                            ?: it.deviceAddress,
                    )
                }
            }
        }
    }

    /** Keeps the greeting and vehicle card in sync with what the user set up. */
    private fun observeRider() {
        viewModelScope.launch {
            // The greeting used to read the *account* name, so a rider who
            // signed in with Google and then typed a different name in Create
            // Profile was greeted by their Google name for the rest of the app's
            // life. What the rider last set wins; the account name is only the
            // fallback for someone who never typed one.
            combine(
                userPrefs.riderName,
                userPrefs.riderNickname,
                userPrefs.session,
            ) { typed, nickname, session ->
                typed.ifBlank { session.name }.ifBlank { "Rider" } to nickname
            }.collect { (name, nickname) ->
                _uiState.update { it.copy(riderName = name, nickname = nickname) }
            }
        }
        viewModelScope.launch {
            vehicleRepository.selection.collect { sel ->
                _uiState.update {
                    it.copy(
                        vehicle = sel.vehicle,
                        vehicleColor = sel.color ?: sel.vehicle?.colors?.firstOrNull(),
                        deviceName = sel.vehicle?.name ?: it.deviceName,
                    )
                }
            }
        }
    }

    private fun observeTelemetry() {
        viewModelScope.launch {
            bleRepository.telemetry.collect { data ->
                // Before the first good frame there is nothing true to show, so
                // the cards stay empty rather than reading a confident zero.
                // Invalid only happens on sign-out, when the readings are
                // cleared - so clear the cards too, to "—".
                if (!data.isValid) {
                    _uiState.update {
                        it.copy(fuelSegments = null, fuel = null, odometer = null, tripAKm = null, tripBKm = null)
                    }
                    return@collect
                }
                // The Service screen has to work with the scooter out of range,
                // so every reading is cached while the link is up — and the
                // dashboard is the screen that is open while riding.
                serviceRepository.recordOdometer(data.odometerKm)
                _uiState.update {
                    it.copy(
                        fuelSegments = data.fuelSegments,
                        fuel = (data.fuelLevel * 100).toInt(),
                        odometer = data.odometerKm,
                        tripAKm = data.tripAKm,
                        tripBKm = data.tripBKm,
                    )
                }
            }
        }
    }

    fun onIntent(intent: DashboardUiIntent) {
        when (intent) {
            is DashboardUiIntent.Connect -> connect()
            is DashboardUiIntent.Disconnect -> disconnect()
        }
    }

    private fun connect() {
        val address = deviceAddress ?: _uiState.value.deviceAddress ?: return
        // The link runs in the repository's own scope; state arrives via observeConnection().
        connectToDeviceUseCase(address)
    }

    private fun disconnect() {
        Log.d(TAG, "User disconnect requested from ViewModel")
        bleRepository.disconnect()
    }
}
