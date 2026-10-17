package com.eshwar.rideconnectx.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eshwar.rideconnectx.data.repository.NavigationRelay
import com.eshwar.rideconnectx.domain.model.NavState
import com.eshwar.rideconnectx.domain.repository.BleRepository
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val relay: NavigationRelay,
    private val bleRepository: BleRepository,
) : ViewModel() {

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
    fun onHandOffToMaps() = relay.awaitMaps()

    fun stopNavigation() = relay.stop()
}
