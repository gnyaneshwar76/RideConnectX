package com.eshwar.rideconnectx.data.ble

import com.eshwar.rideconnectx.domain.ClusterFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Which name sets the checksum rule at connect. The service's own read wins over a saved (possibly older) name. */
object ClusterNameChoice {
    fun pick(resolved: String?, scanName: String?, saved: String?): String? =
        // "Scooter" = BleForegroundService.FALLBACK_DEVICE_NAME (its companion is private)
        resolved ?: scanName?.takeIf { it != "Scooter" } ?: saved
}

/** Only the V-Strom/Gixxer family is asked to bond; every other family is left exactly as it was. */
object BondPolicy {
    fun shouldCreateBond(family: ClusterFamily, bondIsNone: Boolean): Boolean =
        family == ClusterFamily.MOTORCYCLE && bondIsNone
}

/**
 * In-memory facts about the current link, kept in release builds too (release strips Log.d).
 * Holds no names, addresses, message text or coordinates; the vehicle appears only as its
 * profile code (e.g. BM01).
 */
object LinkDiagnostics {
    data class Snapshot(
        val connectedAtMs: Long = 0,
        val gattConnected: Boolean? = null,
        val lastGattStatus: Int? = null,
        val lastDisconnectStatus: Int? = null,
        val mtu: Int? = null,
        val services: List<String> = emptyList(),
        /** null = not checked yet. */
        val serviceFound: Boolean? = null,
        val cccdOk: Int = 0,
        val cccdFailed: Int = 0,
        val lastCccdFailStatus: Int? = null,
        /** none / bonding / bonded / failed / not needed */
        val bond: String = "not needed",
        val createBondResult: Boolean? = null,
        val sent: Map<Int, Int> = emptyMap(),
        val plainSumSent: Boolean? = null,
        val rxCount: Int = 0,
        val lastRxType: Int? = null,
        val profileCode: String = "",
        val family: ClusterFamily? = null,
    ) {
        /** MOTORCYCLE only: connected, but the bike's 0xFEFB service is not found (yet, or ever). */
        val awaitingBike: Boolean get() = family == ClusterFamily.MOTORCYCLE && serviceFound != true
    }

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun reset() { _state.value = Snapshot(connectedAtMs = System.currentTimeMillis()) }
    fun update(f: (Snapshot) -> Snapshot) = _state.update(f)

    fun setProfile(code: String, family: ClusterFamily) = update { it.copy(profileCode = code, family = family) }

    fun onPacketSent(packet: ByteArray, complement: Boolean) = update {
        val type = if (packet.size > 1) packet[1].toInt() and 0xFF else -1
        it.copy(sent = it.sent + (type to ((it.sent[type] ?: 0) + 1)), plainSumSent = !complement)
    }

    fun onRx(frame: ByteArray) = update {
        it.copy(rxCount = it.rxCount + 1, lastRxType = if (frame.size > 1) frame[1].toInt() and 0xFF else null)
    }

    /** 0000fefb-0000-1000-8000-00805f9b34fb -> FEFB; anything else -> its first 8 hex digits. */
    fun shortUuid(uuid: String): String {
        val u = uuid.lowercase()
        return (if (u.startsWith("0000") && u.endsWith("-0000-1000-8000-00805f9b34fb")) u.substring(4, 8) else u.take(8)).uppercase()
    }
}
