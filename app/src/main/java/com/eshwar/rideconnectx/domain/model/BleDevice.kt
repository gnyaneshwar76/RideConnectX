package com.eshwar.rideconnectx.domain.model

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val lastSeen: Long = System.currentTimeMillis(),
    val serviceUuids: List<String> = emptyList(),
    val manufacturerData: Boolean = false,
    val rawAdvertisement: String = "",
    val isConnected: Boolean = false
)
