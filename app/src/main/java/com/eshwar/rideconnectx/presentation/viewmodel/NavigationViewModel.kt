package com.eshwar.rideconnectx.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eshwar.rideconnectx.data.repository.NavigationRelay
import com.eshwar.rideconnectx.domain.model.NavState
import com.eshwar.rideconnectx.domain.repository.BleRepository
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val relay: NavigationRelay,
    private val bleRepository: BleRepository,
    private val bgPrompt: com.eshwar.rideconnectx.core.util.BackgroundRunPromptStore,
    private val mapsOffStore: com.eshwar.rideconnectx.data.local.MapsOffCardStore,
    private val mapsOffTracker: com.eshwar.rideconnectx.data.local.MapsOffTracker,
    @com.eshwar.rideconnectx.core.di.ApplicationScope private val appScope: kotlinx.coroutines.CoroutineScope,
) : ViewModel() {

    val mapsOffState: StateFlow<com.eshwar.rideconnectx.data.local.MapsOffState> =
        mapsOffStore.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            com.eshwar.rideconnectx.data.local.MapsOffState())

    fun dismissMapsOff() { appScope.launch { mapsOffStore.dismiss(System.currentTimeMillis()) } }
    fun mapsOffButtonTapped() { appScope.launch { mapsOffStore.buttonTapped(System.currentTimeMillis()) } }

    /** Null until the saved flag is read. */
    val popupShown: StateFlow<Boolean?> =
        bgPrompt.shown.map<Boolean, Boolean?> { it }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** On the app scope so the flag lands even if the rider leaves the page at once. */
    fun markPopupShown() { appScope.launch { bgPrompt.markShown() } }


    val navState: StateFlow<NavState> =
        relay.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavState.Inactive)

    /**
     * Whether the vehicle is connected at all.
     *
     * This used to read [NavigationRelay.clusterLinked], which only turns true
     * after a maneuver has actually been delivered — so the screen said "not
     * connected" even with the scooter paired. Connection and delivery are two
     * different questions; this is the first one.
     */
    val vehicleConnected: StateFlow<Boolean> =
        bleRepository.connectionState
            .map { it is ConnectionState.Connected }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether maneuvers are actually reaching the cluster. */
    val clusterLinked: StateFlow<Boolean> =
        relay.clusterLinked.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Live speed from the cluster, shown alongside the Maps figures. */
    val speedKmh: StateFlow<Int?> =
        bleRepository.telemetry
            .map { it.speed }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Marks the hand-off so the screen can show "waiting for Maps". */
    fun onHandOffToMaps() { relay.awaitMaps(); mapsOffTracker.onMapsLaunched() }

    fun stopNavigation() = relay.stop()
}
