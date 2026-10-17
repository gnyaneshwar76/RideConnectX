package com.eshwar.rideconnectx.data.ble

import android.util.Log

object ScooterCandidateFilter {
    private const val TAG = "RCX-BLE"

    /** Official scan rule (12 chars with SA/SB/SC); "SAS..." Access names pass as before. */
    fun isClusterNameCandidate(name: String?): Boolean =
        com.eshwar.rideconnectx.domain.ProtocolEngine.isClusterName(name)

    /** Advertised name first: the cached device.name is null for a bike the phone never saw. */
    fun pickName(advertised: String?, cached: String?): String? =
        advertised?.takeIf { it.isNotEmpty() } ?: cached

    fun isScooterCandidate(name: String?, address: String, savedAddress: String?): Boolean {
        if (address.equals(savedAddress, ignoreCase = true)) {
            Log.d(TAG, "Candidate found (Saved Address Match): $address")
            return true
        }

        val deviceName = name ?: return false
        val isCandidate = isClusterNameCandidate(deviceName)
        
        if (isCandidate) {
            Log.d(TAG, "Candidate scooter found: Name=$deviceName, Address=$address")
        } else {
            // Logging filtered out devices for debug transparency
            // Log.v(TAG, "Non-candidate device filtered out: $deviceName ($address)")
        }
        
        return isCandidate
    }
}
