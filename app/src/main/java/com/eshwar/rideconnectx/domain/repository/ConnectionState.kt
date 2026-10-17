package com.eshwar.rideconnectx.domain.repository

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Scanning : ConnectionState()
    data class Connecting(val address: String, val name: String) : ConnectionState()
    data class Connected(val address: String, val name: String) : ConnectionState()
    object Disconnecting : ConnectionState()
    /** [byUser]: the rider pressed Disconnect (app or shade); no "dropped" alert. */
    data class Disconnected(val reason: String, val byUser: Boolean = false) : ConnectionState()
    data class Failed(val reason: String) : ConnectionState()
    object UnsupportedDevice : ConnectionState()
}
