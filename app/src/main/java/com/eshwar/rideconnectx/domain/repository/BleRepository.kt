package com.eshwar.rideconnectx.domain.repository

import com.eshwar.rideconnectx.domain.model.BleDevice
import com.eshwar.rideconnectx.domain.model.OnboardingStatus
import com.eshwar.rideconnectx.domain.model.ScooterTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface BleRepository {
    val onboardingStatus: Flow<OnboardingStatus>
    val telemetry: Flow<ScooterTelemetry>

    /**
     * The one true connection state for the whole app.
     *
     * [connect] returns a cold, per-caller flow, so each screen that called it
     * saw only its own attempt — which is why Pairing could say "connected"
     * while the Dashboard said "offline" and Navigation said "not connected".
     * Every screen observes this instead.
     */
    val connectionState: StateFlow<ConnectionState>

    fun scanDevices(): Flow<List<BleDevice>>
    fun stopScan()
    fun connect(address: String): Flow<ConnectionState>
    fun disconnect()
    fun sendPacket(packet: ByteArray): Flow<Boolean>
    fun observeNotifications(): Flow<BlePacket>

    /**
     * Reconnects to the last paired vehicle if there is one. Safe to call on
     * every app start; does nothing when nothing was ever paired.
     */
    fun reconnectLastDevice()

    /** Drops the link and stops the BLE service outright. */
    fun shutdown()

    /** The rider is looking at the app again: reconnecting by itself is wanted. */
    fun onAppOpened() {}

    /** Fast radio link while a route is on the cluster; normal otherwise (battery). */
    fun setLowLatency(on: Boolean) {}

    suspend fun updateOnboardingStatus(status: OnboardingStatus)
}

data class BlePacket(
    val characteristicUuid: String,
    val data: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
)
