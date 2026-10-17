package com.eshwar.rideconnectx.core.util

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Whether Android will let the app keep the vehicle link alive in the rider's
 * pocket, and a way to ask for it.
 *
 * This is battery-optimisation exemption. It was previously shown as a row that
 * always read "not granted" and, when tapped, opened the app's *App info* page —
 * from which the rider had to know that "Battery" was the sub-page they wanted
 * and that "Unrestricted" was the setting inside it. That is fine if you already
 * know Android; it is a dead end if you do not.
 *
 * [request] fires the system's own one-tap dialog instead, and [isExempt] reads
 * the real state so the row can show a tick once it is on.
 */
class BackgroundRunAccess(private val context: Context) {

    private val power: PowerManager?
        get() = ContextCompat.getSystemService(context, PowerManager::class.java)

    /** True when Android has been told not to doze this app. */
    val isExempt: Boolean
        get() = power?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    /**
     * Asks for the exemption.
     *
     * The direct request shows a single system dialog and is the whole point of
     * this class. Some builds — and some OEM skins — refuse to resolve it, so
     * the battery-optimisation *list* is the fallback, and App info is the last
     * resort. Every step lands the rider somewhere they can actually finish.
     */
    @SuppressLint("BatteryLife")
    fun request() {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val appInfo = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )

        // Without the manifest permission the system dialog closes silently
        // (no exception), which looked like "nothing happens"; skip it then.
        val canAskDirect = context.checkSelfPermission(
            android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val steps = if (canAskDirect) listOf(direct, list, appInfo) else listOf(list, appInfo)

        for (intent in steps) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next one.
            } catch (_: SecurityException) {
                // Some OEMs refuse the direct request outright.
            }
        }
    }
}

/**
 * Live exemption state for Compose, re-read whenever [refresh] is called.
 *
 * The screen calls it on resume, so coming back from the system dialog updates
 * the tick instead of leaving the row claiming the work still needs doing.
 */
@Composable
fun rememberBackgroundRunAccess(): BackgroundRunState {
    val context = LocalContext.current
    val access = remember { BackgroundRunAccess(context) }
    var exempt by remember { mutableStateOf(access.isExempt) }

    return remember(exempt) {
        BackgroundRunState(
            granted = exempt,
            request = access::request,
            refresh = { exempt = access.isExempt },
        )
    }
}

data class BackgroundRunState(
    val granted: Boolean,
    val request: () -> Unit,
    val refresh: () -> Unit,
)
