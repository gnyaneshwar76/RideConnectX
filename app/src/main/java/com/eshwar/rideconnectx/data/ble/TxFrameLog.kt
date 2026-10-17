package com.eshwar.rideconnectx.data.ble

import com.eshwar.rideconnectx.BuildConfig
import com.eshwar.rideconnectx.data.nav.RideLog
import com.eshwar.rideconnectx.domain.ProtocolEngine
import java.util.concurrent.atomic.AtomicInteger

/** Debug-only: one ride-log line per frame written to the cluster (frames hold caller names). */
object TxFrameLog {
    private val seq = AtomicInteger()

    /** `TX model=<family> type=0xNN hex=<upper hex> seq=<n>`; type is byte 1 of the frame. */
    fun format(model: String, packet: ByteArray, seq: Int): String {
        val type = if (packet.size > 1) packet[1].toInt() and 0xFF else 0
        val hex = packet.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        return "TX model=$model type=0x%02X hex=$hex seq=$seq".format(type)
    }

    /** No-op unless BuildConfig.DEBUG (constant false in release, so R8 drops it). */
    fun log(rideLog: RideLog, packet: ByteArray) {
        if (!BuildConfig.DEBUG) return
        rideLog.diag(format(ProtocolEngine.profile.family.name, packet, seq.incrementAndGet()))
    }
}
