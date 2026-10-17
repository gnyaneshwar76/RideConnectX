package com.eshwar.rideconnectx.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.eshwar.rideconnectx.core.di.ServiceEntryPoint
import com.eshwar.rideconnectx.data.nav.MapsNotificationParser
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Debug-only hook for driving the navigation pipeline without Google Maps.
 *
 * It exists because the real path can only be exercised on a phone, on a live
 * route, next to a running scooter — and everything upstream of the BLE write
 * deserves to be verified before then. It feeds text through the *same*
 * [MapsNotificationParser] and the same relay that the real listener uses, so a
 * pass here means the parse → relay → UI chain is sound; only the notification
 * delivery itself is stubbed.
 *
 * Lives in `src/debug`, so it is not compiled into a release build at all.
 *
 *     adb shell am broadcast -a com.eshwar.rideconnectx.TEST_NAV \
 *       -e title "Turn left onto MG Road" -e text "400 m · 12 min"
 *
 *     adb shell am broadcast -a com.eshwar.rideconnectx.TEST_NAV_STOP
 */
class NavTestReceiver : BroadcastReceiver() {

    private companion object {
        const val TAG = "RCX-Nav"
        const val ACTION_MANEUVER = "com.eshwar.rideconnectx.TEST_NAV"
        const val ACTION_STOP = "com.eshwar.rideconnectx.TEST_NAV_STOP"

        /** Sends one raw cluster maneuver code — see [sendRawCode]. */
        const val ACTION_CODE = "com.eshwar.rideconnectx.TEST_CODE"

        /**
         * Forces the heartbeat's battery field to a given value, so the scale
         * the cluster expects can be found by looking at the dashboard.
         *
         *     adb shell "am broadcast -n <pkg>/<this> \
         *       -a com.eshwar.rideconnectx.TEST_BATT --ei val 3"
         *
         * `--ei val -1` hands the field back to the real battery level.
         */
        const val ACTION_BATTERY = "com.eshwar.rideconnectx.TEST_BATT"

        /** Same idea for the signal field — see [ACTION_BATTERY]. */
        const val ACTION_SIGNAL = "com.eshwar.rideconnectx.TEST_SIG"

        /**
         * Sends ONE heartbeat with the alert flags forced to given characters.
         *
         * Exists to settle a contradiction in the knowledge base: the JADX
         * decompile says 78 (N) means an alert is PRESENT, while a note written
         * from a packet capture says Y is the alert. We ship Y, and on
         * 18 August the cluster ignored a correctly formed Y missed-call flag,
         * which points at the decompile being the right reading.
         *
         * Usage: broadcast TEST_FLAGS with --es msg N --es call N
         */
        const val ACTION_FLAGS = "com.eshwar.rideconnectx.TEST_FLAGS"

        /**
         * Switches how kilometres are written into the distance fields, so the
         * encoding can be settled at the scooter without a rebuild.
         * `--ei style 0|1|2` - see ProtocolEngine.kmStyle.
         */
        const val ACTION_KMSTYLE = "com.eshwar.rideconnectx.TEST_KMSTYLE"

        /**
         * Test call / message alert through the REAL path: ClusterAlerts ->
         * BleRepositoryImpl alert pusher -> 0x32 (call) / 0x35 (message).
         * `--es who "Asha"`; for a message also `--es app W|N` and `--es text hi`.
         * CALL, MSG and MISSED also add one Notifications row (fixed text, never
         * the message content), as the live listener does.
         */
        const val ACTION_CALL = "com.eshwar.rideconnectx.TEST_CALL"
        const val ACTION_MSG = "com.eshwar.rideconnectx.TEST_MSG"

        /** Missed-call count alert: `--es caller "Asha" --ei count 2 --ez whatsapp true`. */
        const val ACTION_MISSED = "com.eshwar.rideconnectx.TEST_MISSED"

        /**
         * Makes the simulated scan find no scooter (nothing-found Pair screen):
         * `--ez on true|false`. Sets BleRepositorySimulatorImpl.noScooterInRange.
         */
        const val ACTION_SCAN_EMPTY = "com.eshwar.rideconnectx.TEST_SCAN_EMPTY"

        /**
         * Sets the advertised name the simulated scooter uses (e.g. SBM010000000,
         * SCE100000000): `--es name SBM010000000`. Sets BleRepositorySimulatorImpl.deviceName.
         */
        const val ACTION_DEVICE_NAME = "com.eshwar.rideconnectx.TEST_DEVICE_NAME"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val relay = EntryPointAccessors
            .fromApplication(context.applicationContext, ServiceEntryPoint::class.java)
            .navigationRelay()

        when (intent.action) {
            ACTION_KMSTYLE -> {
                val style = intent.getIntExtra("style", 0)
                com.eshwar.rideconnectx.domain.ProtocolEngine.kmStyle = style
                android.util.Log.d("RCX-BLE", "kmStyle set to $style")
                return
            }
            ACTION_CALL -> {
                val who = intent.getStringExtra("who") ?: "Test Caller"
                // Same call the real listener makes for a ringing call.
                com.eshwar.rideconnectx.data.nav.ClusterAlerts.onMissedCall(title = who)
                record(context, com.eshwar.rideconnectx.data.local.db.NotificationKind.CALL, who, "Call")
                Log.d(TAG, "[TEST] call alert who='$who'")
                return
            }
            ACTION_MSG -> {
                val who = intent.getStringExtra("who") ?: "Test Sender"
                val app = intent.getStringExtra("app")?.firstOrNull() ?: 'N'
                val text = intent.getStringExtra("text").orEmpty()
                com.eshwar.rideconnectx.data.nav.ClusterAlerts.onNotification(app, who, text)
                record(context, com.eshwar.rideconnectx.data.local.db.NotificationKind.MESSAGE, who, "New message")
                Log.d(TAG, "[TEST] message alert who='$who' app=$app")
                return
            }
            ACTION_MISSED -> {
                val who = intent.getStringExtra("caller") ?: "Test Caller"
                val count = intent.getIntExtra("count", 1)
                val whatsApp = intent.getBooleanExtra("whatsapp", false)
                com.eshwar.rideconnectx.data.nav.ClusterAlerts.onCallMissed(count, who, whatsApp)
                record(context, com.eshwar.rideconnectx.data.local.db.NotificationKind.CALL, who, "Missed call")
                Log.d(TAG, "[TEST] missed call who='$who' count=$count whatsapp=$whatsApp")
                return
            }
            ACTION_SCAN_EMPTY -> {
                val on = intent.getBooleanExtra("on", true)
                com.eshwar.rideconnectx.data.repository.BleRepositorySimulatorImpl.noScooterInRange = on
                Log.d(TAG, "[TEST] simulated scan empty=$on")
                return
            }
            ACTION_DEVICE_NAME -> {
                val name = intent.getStringExtra("name")?.trim().orEmpty()
                if (name.isBlank()) {
                    Log.w(TAG, "[TEST] device name: no name given")
                    return
                }
                com.eshwar.rideconnectx.data.repository.BleRepositorySimulatorImpl.deviceName = name
                Log.d(TAG, "[TEST] simulated device name=$name")
                return
            }
            ACTION_STOP -> {
                Log.d(TAG, "[TEST] stop")
                relay.stop()
            }

            ACTION_BATTERY -> {
                val value = intent.getIntExtra("val", -1)
                val status = EntryPointAccessors
                    .fromApplication(context.applicationContext, ServiceEntryPoint::class.java)
                    .phoneStatusProvider()
                status.batteryOverride = value.takeIf { it >= 0 }
                Log.d(TAG, "[SWEEP] battery field forced to ${status.batteryOverride ?: "real"}")
            }

            ACTION_SIGNAL -> {
                val value = intent.getIntExtra("val", -1)
                val status = EntryPointAccessors
                    .fromApplication(context.applicationContext, ServiceEntryPoint::class.java)
                    .phoneStatusProvider()
                status.signalOverride = value.takeIf { it >= 0 }
                Log.d(TAG, "[SWEEP] signal field forced to ${status.signalOverride ?: "real"}")
            }

            ACTION_FLAGS -> {
                val msg = intent.getStringExtra("msg")?.firstOrNull() ?: 78.toChar()
                val call = intent.getStringExtra("call")?.firstOrNull() ?: 78.toChar()
                sendForcedFlags(context, msg, call)
            }

            ACTION_CODE -> {
                val code = intent.getIntExtra("code", -1)
                val distance = intent.getIntExtra("dist", 200)
                if (code < 0) {
                    Log.w(TAG, "[SWEEP] no code given")
                    return
                }
                sendRawCode(context, code, distance)
            }

            ACTION_MANEUVER -> {
                val title = intent.getStringExtra("title").orEmpty()
                val text = intent.getStringExtra("text").orEmpty()

                // Same order as the real listener: a reroute is not a maneuver.
                if (MapsNotificationParser.isReroute("$title $text")) {
                    relay.submitReroute("$title | $text")
                    return
                }

                val maneuver = MapsNotificationParser.parse(title, text)
                if (maneuver == null) {
                    Log.w(TAG, "[TEST] not a navigation notification: '$title' / '$text'")
                    return
                }

                Log.d(TAG, "[TEST] ${maneuver.instruction} | ${maneuver.distanceToTurn} | id=${maneuver.maneuverId}")
                relay.submit(maneuver)
            }
        }
    }

    /** One Notifications row, same shape as the live listener's recordHistory. */
    private fun record(context: Context, kind: com.eshwar.rideconnectx.data.local.db.NotificationKind, who: String, body: String) {
        scope.launch {
            runCatching {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.eshwar.rideconnectx.data.nav.NavHistoryEntryPoint::class.java,
                ).notificationRepository().notify(kind, who.ifBlank { "Unknown" }, body)
            }
        }
    }

    /**
     * Puts one raw maneuver code on the wire so a human can read what the
     * cluster actually draws for it.
     *
     * The translation from Mappls maneuver id to cluster code is recovered from
     * the official app, but *what each cluster code renders* is not written
     * anywhere — it lives in the dashboard firmware. Guessing it is what made
     * the turn arrows wrong. This exists so the mapping can be established by
     * observation instead:
     *
     *     adb shell "am broadcast -n <pkg>/<this>      *       -a com.eshwar.rideconnectx.TEST_CODE --ei code 40 --ei dist 200"
     */
    private fun sendRawCode(context: Context, code: Int, distanceMetres: Int) {
        val ble = EntryPointAccessors
            .fromApplication(context.applicationContext, ServiceEntryPoint::class.java)
            .bleRepository()

        val clock = java.text.SimpleDateFormat("hhmma", java.util.Locale.US)
            .format(java.util.Date()).uppercase()

        val packet = com.eshwar.rideconnectx.domain.ProtocolEngine.buildNavigationPacket(
            clusterCode = code,
            distanceMetres = distanceMetres,
            clock = clock,
        )

        scope.launch {
            val sent = ble.sendPacket(packet).first()
            Log.i(TAG, "[SWEEP] code=$code dist=${distanceMetres}m sent=$sent")
        }
    }

    /**
     * One heartbeat with bytes 14 and 15 forced, bypassing ClusterAlerts.
     *
     * Everything else in the frame is real, so polarity is the only variable.
     */
    private fun sendForcedFlags(context: Context, msgFlag: Char, callFlag: Char) {
        val entry = EntryPointAccessors
            .fromApplication(context.applicationContext, ServiceEntryPoint::class.java)
        val ble = entry.bleRepository()
        val phone = entry.phoneStatusProvider()

        val packet = com.eshwar.rideconnectx.domain.ProtocolEngine.buildHeartbeatPacket(
            batteryBucket = phone.batteryBucket(),
            isCharging = phone.isCharging(),
            signalBars = phone.signalBars(),
            clockHHmmss = phone.clockHHmmss(),
            notificationPending = false,
            missedCallPending = false,
        )
        packet[14] = msgFlag.code.toByte()
        packet[15] = callFlag.code.toByte()
        packet[28] = com.eshwar.rideconnectx.domain.ProtocolEngine.calculateChecksum(packet)

        scope.launch {
            val sent = ble.sendPacket(packet).first()
            Log.i(TAG, "[FLAGS] msg=$msgFlag call=$callFlag sent=$sent")
        }
    }
}
