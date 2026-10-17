package com.eshwar.rideconnectx.data.ble

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The phone-side values that ride along in the 0x33 heartbeat: battery, mobile
 * signal and the clock.
 *
 * The cluster shows these next to the Bluetooth glyph, which is why the official
 * app collects exactly the same three things before building the packet.
 */
@Singleton
class PhoneStatusProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Twelve-hour, matching the official app.
     *
     * Captured at 22:51:38, its packet carried `105138` — `hh`, not `HH`. The
     * cluster's own clock reads 12-hour, so this is what it expects.
     */

    /**
     * Debug-only override for the battery field, used to find out what scale the
     * cluster actually wants. Null means "use the real battery".
     */
    @Volatile
    var batteryOverride: Int? = null

    /** Debug-only override for the signal field. Null means "use the real one". */
    @Volatile
    var signalOverride: Int? = null

    /**
     * Battery charge as cluster bars, 0..3.
     *
     * **Settled — do not change.** Confirmed correct by the rider against the
     * official app on 4 August 2026.
     *
     * Swept against the dashboard: `3` drew three bars, `4` also drew three,
     * `5` blanked the indicator. An earlier 0..9 percentage bucket sent `8` for
     * an 88% phone — out of range — which is why the battery drew empty.
     *
     * The indicator has four levels. At 88% it lights three, and the fourth
     * blinks while charging — that blinking segment is the charging animation,
     * not a fourth charge level. The official app renders identically.
     */
    fun batteryBucket(): Int {
        batteryOverride?.let { return it }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return 0
        val percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (percent !in 0..100) return 0

        return when {
            percent >= 75 -> 3
            percent >= 50 -> 2
            percent >= 20 -> 1
            else -> 0
        }
    }

    /**
     * Whether the phone is on charge.
     *
     * Reads the battery status directly rather than using
     * `BatteryManager.isCharging`, which reported false on the test phone
     * (OnePlus, Android 15) while `dumpsys battery` said `status: 2` and the
     * phone was plainly charging over USB.
     */
    fun isCharging(): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val status = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        ) {
            return true
        }

        // Fall back to the sticky broadcast, which every device populates.
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val sticky = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return sticky == BatteryManager.BATTERY_STATUS_CHARGING ||
            sticky == BatteryManager.BATTERY_STATUS_FULL
    }

    /**
     * Signal as 0..3 bars.
     *
     * Mirrors the official app's rules: no SIM or airplane mode reports zero
     * rather than a stale reading, and without READ_PHONE_STATE there is
     * nothing to report, so it reports nothing rather than inventing bars.
     */
    fun signalBars(): Int {
        signalOverride?.let { return it }

        if (isAirplaneModeOn()) return 0

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return 0

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return 0
        if (tm.simState == TelephonyManager.SIM_STATE_ABSENT) return 0

        return try {
            val ss = tm.signalStrength ?: return 0
            val nrRsrp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                tm.dataNetworkType == TelephonyManager.NETWORK_TYPE_NR
            ) {
                ss.cellSignalStrengths.filterIsInstance<CellSignalStrengthNr>().firstOrNull()?.ssRsrp
            } else {
                null
            }
            officialSignalBars(ss.level, nrRsrp)
        } catch (_: SecurityException) {
            0
        }
    }

    companion object {
        /**
         * The official app's rule (C4858B.onSignalStrengthsChanged), copied as is:
         * on 5G standalone it bins NR RSRP (> -90 / -105 / -120 dBm -> 3 / 2 / 1),
         * otherwise getLevel() 0..4 becomes 0,0,1,2,3.
         */
        internal fun officialSignalBars(level: Int, nrRsrp: Int?): Int = when {
            nrRsrp != null -> when {
                nrRsrp > -90 -> 3
                nrRsrp > -105 -> 2
                nrRsrp > -120 -> 1
                else -> 0
            }
            level >= 4 -> 3
            level == 3 -> 2
            level == 2 -> 1
            else -> 0
        }
    }

    private fun isAirplaneModeOn(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    /** Local time as `HHmmss`, the format the cluster's clock field expects. */
    // New formatter each call: a shared one keeps the timezone from when it was built
    // (stale after a timezone change) and SimpleDateFormat is not thread-safe.
    fun clockHHmmss(): String = SimpleDateFormat("hhmmss", Locale.US).format(Calendar.getInstance().time)
}
