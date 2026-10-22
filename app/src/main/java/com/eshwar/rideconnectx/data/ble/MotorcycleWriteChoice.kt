package com.eshwar.rideconnectx.data.ble

import android.bluetooth.BluetoothGattCharacteristic as C
import com.eshwar.rideconnectx.domain.ClusterFamily

/** True only for the V-Strom/Gixxer family: the only one allowed to use the 0xFFF0 service. */
internal fun usesFff0Path(family: ClusterFamily): Boolean = family == ClusterFamily.MOTORCYCLE

/**
 * Index of the characteristic to write on in the FFF0 service, from (short UUID, properties) pairs:
 * characteristic 0 if writable, else FFF2, else the first writable, else null.
 */
internal fun pickMotorcycleWriteIndex(chars: List<Pair<String, Int>>): Int? {
    val writeMask = C.PROPERTY_WRITE or C.PROPERTY_WRITE_NO_RESPONSE
    fun writable(i: Int) = chars[i].second and writeMask != 0
    if (chars.isNotEmpty() && writable(0)) return 0
    return chars.indices.firstOrNull { writable(it) && chars[it].first == "FFF2" }
        ?: chars.indices.firstOrNull { writable(it) }
}

/**
 * Which characteristics get a CCCD write. NOTIFY always (unchanged). INDICATE-only only on the
 * motorcycle family: the Access 0xFEFB service has an INDICATE char (00000004) that was never
 * subscribed, and that must stay so.
 */
internal fun shouldSubscribe(props: Int, family: ClusterFamily): Boolean =
    props and C.PROPERTY_NOTIFY != 0 ||
        (usesFff0Path(family) && props and C.PROPERTY_INDICATE != 0)
