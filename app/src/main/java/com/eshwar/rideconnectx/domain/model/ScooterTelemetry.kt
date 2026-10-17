package com.eshwar.rideconnectx.domain.model

/**
 * What the scooter actually sends in a 0x37 frame.
 *
 * Decoded 4 August 2026 against the rider's Access 125: the ASCII digits at
 * bytes 2..22 split 9/6/6 into odometer, Trip A and Trip B, and matched the
 * cluster exactly (ODO 1602 km, Trip A 198.7 km, Trip B 1079.2 km).
 *
 * Deliberately absent: **speed**, **fuel economy (km/L)** and **battery
 * voltage**. The cluster shows those, but they are not in the frame — the
 * scooter never transmits them, so nothing downstream should pretend to know
 * them.
 */
data class ScooterTelemetry(
    /** Total distance, whole km. */
    val odometerKm: Int = 0,
    /** Trip A, km to one decimal. */
    val tripAKm: Float = 0f,
    /** Trip B, km to one decimal. */
    val tripBKm: Float = 0f,
    /** Fuel bar, 0..5 segments as drawn E-F on the cluster. */
    val fuelSegments: Int = 0,
    /** True once a well-formed frame has been received. */
    val isValid: Boolean = false,
    /** e-ACCESS battery percent from byte 24; null on every other model. */
    val batteryPercent: Int? = null,
    /** Not transmitted by the scooter. Retained for the simulator only. */
    val speed: Int = 0,
    val fuelLevel: Float = 0f, // 0.0 to 1.0
    val batteryVoltage: Float = 0f,
    val engineTemp: Int = 0,
    val range: Int = 0,
    val isEngineOn: Boolean = false,
    val sideStandActive: Boolean = false,
    val serviceAlert: Boolean = false,
    val oilChangeAlert: Boolean = false,
    val lowBatteryAlert: Boolean = false
)
