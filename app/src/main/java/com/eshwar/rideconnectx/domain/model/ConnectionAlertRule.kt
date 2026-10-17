package com.eshwar.rideconnectx.domain.model

import com.eshwar.rideconnectx.domain.repository.ConnectionState

/** What a change in the scooter link is worth telling the rider. */
enum class ConnectionAlert { CONNECTED, LOST }

/**
 * Pure, so it is testable without a device. A link the rider ends themselves
 * (Disconnected.byUser; the state jumps straight there, a StateFlow would hide
 * a short Disconnecting anyway) is not an alert; only a drop from a live link is.
 */
object ConnectionAlertRule {
    fun alertFor(previous: ConnectionState, current: ConnectionState): ConnectionAlert? = when {
        current is ConnectionState.Connected && previous !is ConnectionState.Connected -> ConnectionAlert.CONNECTED
        previous is ConnectionState.Connected &&
            ((current is ConnectionState.Disconnected && !current.byUser) || current is ConnectionState.Failed) -> ConnectionAlert.LOST
        else -> null
    }
}
