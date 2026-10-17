package com.eshwar.rideconnectx.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eshwar.rideconnectx.core.util.SystemStateManager
import com.eshwar.rideconnectx.domain.model.BleDevice
import android.util.Log
import com.eshwar.rideconnectx.domain.usecase.ConnectToDeviceUseCase
import com.eshwar.rideconnectx.domain.usecase.ScanDevicesUseCase
import com.eshwar.rideconnectx.domain.repository.BleRepository
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import com.eshwar.rideconnectx.data.local.SessionDataStore
import kotlinx.coroutines.flow.first
import com.eshwar.rideconnectx.presentation.mvi.UiEffect
import com.eshwar.rideconnectx.presentation.mvi.UiIntent
import com.eshwar.rideconnectx.presentation.mvi.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanUiState(
    val devices: List<BleDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Idle,
    val isBluetoothEnabled: Boolean = true,
    val isLocationEnabled: Boolean = true,
    val lastDeviceName: String? = null,
    val lastDeviceAddress: String? = null,
    /**
     * True once a scan has run to its end. Distinguishes "we have not looked
     * yet" from "we looked and there was nothing" — without it the screen sits
     * on an empty list forever with no way to tell the rider what happened.
     */
    val scanCompleted: Boolean = false,
    val error: String? = null
) : UiState

sealed class ScanUiIntent : UiIntent {
    object StartScan : ScanUiIntent()
    object StopScan : ScanUiIntent()
    data class SelectDevice(val address: String) : ScanUiIntent()
    object Disconnect : ScanUiIntent()
}

sealed class ScanUiEffect : UiEffect {
    data class NavigateToDashboard(val address: String) : ScanUiEffect()
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanDevicesUseCase: ScanDevicesUseCase,
    private val connectToDeviceUseCase: ConnectToDeviceUseCase,
    private val bleRepository: BleRepository,
    private val systemStateManager: SystemStateManager,
    private val sessionDataStore: SessionDataStore
) : ViewModel() {

    private val TAG = "RCX-BLE"

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<ScanUiEffect>()
    val effect = _effect.asSharedFlow()

    private var scanJob: Job? = null

    init {
        observeSystemState()
        loadLastSession()
        observeConnection()
    }

    /**
     * Mirrors the repository's app-wide connection state into this screen, so
     * a link opened anywhere (including auto-reconnect at startup) is already
     * reflected when the rider arrives here.
     */
    private fun observeConnection() {
        viewModelScope.launch {
            bleRepository.connectionState.collect { state ->
                _uiState.update {
                    // A scan in progress is this screen's own business; don't let
                    // the repository's Idle overwrite it.
                    if (state is ConnectionState.Idle &&
                        it.connectionState is ConnectionState.Scanning
                    ) it else it.copy(connectionState = state)
                }
            }
        }
    }

    private fun loadLastSession() {
        viewModelScope.launch {
            val address = sessionDataStore.lastDeviceAddress.first()
            val name = sessionDataStore.lastDeviceName.first()
            _uiState.update { it.copy(lastDeviceAddress = address, lastDeviceName = name) }
        }
    }

    private fun observeSystemState() {
        viewModelScope.launch {
            combine(
                systemStateManager.isBluetoothEnabled,
                systemStateManager.isLocationEnabled
            ) { bt, loc ->
                _uiState.update { it.copy(isBluetoothEnabled = bt, isLocationEnabled = loc) }
            }.collect()
        }
    }

    fun onIntent(intent: ScanUiIntent) {
        when (intent) {
            is ScanUiIntent.StartScan -> startScan()
            is ScanUiIntent.StopScan -> stopScan()
            is ScanUiIntent.SelectDevice -> connect(intent.address)
            is ScanUiIntent.Disconnect -> disconnect()
        }
    }

    /**
     * Tapping a device used only to emit a navigation effect — which nothing
     * collected — so no GATT connection was ever attempted. This runs the real
     * connection and mirrors its states into the UI.
     *
     * Scanning is stopped first: the Android BLE stack is unreliable when a
     * connection is opened while a scan is still running.
     */
    private fun connect(address: String) {
        Log.d(TAG, "SelectDevice -> connect($address)")
        // Double tap: one attempt only.
        (_uiState.value.connectionState as? ConnectionState.Connecting)?.let { if (it.address == address) return }
        scanJob?.cancel()
        scanJob = null
        val name = _uiState.value.devices.firstOrNull { it.address == address }?.name
        _uiState.update {
            it.copy(connectionState = ConnectionState.Connecting(address, name ?: "Vehicle"))
        }

        // The repository publishes into the shared state; observeConnection()
        // picks it up, so this only has to drive the attempt.
        connectToDeviceUseCase(address)
    }

    private fun disconnect() {
        Log.d(TAG, "Disconnect requested")
        bleRepository.disconnect()
        _uiState.update { it.copy(connectionState = ConnectionState.Idle, scanCompleted = true) }
    }

    private fun startScan() {
        // Re-entering the screen while already connected must not drop the link.
        // Same while a connect is under way (auto-reconnect at startup): a scan would
        // overwrite "Connecting" and the stack is unreliable scanning mid-connect.
        val now = _uiState.value.connectionState
        if (now is ConnectionState.Connected || now is ConnectionState.Connecting) return

        scanJob?.cancel()
        _uiState.update {
            it.copy(
                connectionState = ConnectionState.Scanning,
                devices = emptyList(),
                scanCompleted = false,
            )
        }
        scanJob = viewModelScope.launch {
            scanDevicesUseCase().collect { devices ->
                _uiState.update { it.copy(devices = devices) }
            }
            // The scanner closes itself after its 30-second timeout; only the
            // scanning state resets, so a connection made mid-scan survives.
            // `scanCompleted` is what lets the screen say "nothing found" and
            // offer to look again, rather than spinning indefinitely.
            _uiState.update {
                if (it.connectionState is ConnectionState.Scanning) {
                    it.copy(connectionState = ConnectionState.Idle, scanCompleted = true)
                } else {
                    it
                }
            }
        }
    }

    /**
     * Leaving the screen stops the radio but must not tear down a live
     * connection — that is what the Disconnect action is for.
     */
    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        bleRepository.stopScan()
        _uiState.update {
            if (it.connectionState is ConnectionState.Scanning) {
                // scanCompleted lets the screen offer "Scan again" after a stop (CHK-40).
                it.copy(connectionState = ConnectionState.Idle, scanCompleted = true)
            } else {
                it
            }
        }
    }
}

/** Which buttons the Pair page shows under the list. Pure, so it can be tested. */
data class PairActions(val stop: Boolean, val scanAgain: Boolean, val connectLast: Boolean)

fun pairActions(s: ScanUiState): PairActions {
    val c = s.connectionState
    val busy = c is ConnectionState.Connecting || c is ConnectionState.Connected
    val scanning = c is ConnectionState.Scanning
    return PairActions(
        stop = scanning,
        scanAgain = !scanning && !busy,
        connectLast = !s.lastDeviceAddress.isNullOrEmpty() &&
            (c is ConnectionState.Disconnected || c is ConnectionState.Failed),
    )
}
