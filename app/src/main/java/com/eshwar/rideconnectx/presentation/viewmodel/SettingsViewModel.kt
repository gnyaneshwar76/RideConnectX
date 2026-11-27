package com.eshwar.rideconnectx.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eshwar.rideconnectx.core.di.ApplicationScope
import com.eshwar.rideconnectx.data.local.AppSettingsStore
import com.eshwar.rideconnectx.data.nav.AppTier
import com.eshwar.rideconnectx.data.nav.AppTiers
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.eshwar.rideconnectx.data.local.SessionDataStore
import com.eshwar.rideconnectx.data.local.UserPreferencesStore
import com.eshwar.rideconnectx.data.repository.ServiceRepository
import com.eshwar.rideconnectx.data.repository.VehicleRepository
import com.eshwar.rideconnectx.domain.model.AppSettings
import com.eshwar.rideconnectx.domain.model.DistanceUnit
import com.eshwar.rideconnectx.domain.model.UserSession
import com.eshwar.rideconnectx.domain.repository.BleRepository
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import com.eshwar.rideconnectx.data.nav.ClusterAlerts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Screen 20 — Settings.
 *
 * Every setter writes immediately, which is what the design asks for: a toggle
 * saves the moment it moves, with no Save button anywhere on the screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: AppSettingsStore,
    private val serviceRepository: ServiceRepository,
    private val bleRepository: BleRepository,
    private val session: SessionDataStore,
    userPrefs: UserPreferencesStore,
    vehicleRepository: VehicleRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    private val rideLog: com.eshwar.rideconnectx.data.nav.RideLog,
) : ViewModel() {

    val rideRecorder: StateFlow<Boolean> =
        store.rideRecorder.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setRideRecorder(on: Boolean) = appScope.launch { store.setRideRecorder(on) }

    val gpsCountdown: StateFlow<Boolean> =
        store.gpsCountdown.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setGpsCountdown(on: Boolean) = appScope.launch { store.setGpsCountdown(on) }

    /** The recorded ride log, or null when nothing has been recorded yet. */
    fun rideLogFile(): java.io.File? = rideLog.fileOrNull()

    fun clearRideLog() = rideLog.clear()

    /** Apps that posted a notification, with their switch; fills itself as apps post. */
    val notificationApps: StateFlow<List<NotificationApp>> =
        combine(store.seenPackages, store.appSwitches) { seen, switches ->
            val sms = runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
            buildNotificationApps(seen, switches, sms, ::appLabel)
        }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setAppSwitch(pkg: String, on: Boolean) = appScope.launch { store.setAppSwitch(pkg, on) }

    // Alerts page choices live in AppSettingsStore (DataStore). rcx-nav still has to read them in
    // MapsNotificationListener.relayClusterAlert; the tier switches also flip the per-app switches nav already reads.
    private fun alertFlow(f: kotlinx.coroutines.flow.Flow<Boolean>, default: Boolean): StateFlow<Boolean> =
        f.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), default)
    val clusterAlerts: StateFlow<Boolean> = alertFlow(store.alertsCluster, true)
    val importantOn: StateFlow<Boolean> = alertFlow(store.alertsImportant, true)
    val mediumOn: StateFlow<Boolean> = alertFlow(store.alertsMedium, false)
    val nameOnly: StateFlow<Boolean> = alertFlow(store.alertsNameOnly, false)

    fun setClusterAlerts(on: Boolean) { appScope.launch { store.setAlertsCluster(on) } }
    fun setNameOnly(on: Boolean) { appScope.launch { store.setAlertsNameOnly(on) } }

    fun setTierOn(tier: AppTier, on: Boolean) {
        appScope.launch {
            if (tier == AppTier.IMPORTANT) store.setAlertsImportant(on) else store.setAlertsMedium(on)
            notificationApps.value.filter { it.tier == tier }.forEach { store.setAppSwitch(it.pkg, on) }
        }
    }

    /** Message for the test button (a string resource id), null when nothing to say. */
    private val _testResult = MutableStateFlow<Int?>(null)
    val testResult: StateFlow<Int?> = _testResult

    /** Sends one short message alert through the normal alert path; says so when the scooter is not connected. */
    fun sendTestAlert() {
        if (bleRepository.connectionState.value !is ConnectionState.Connected) {
            _testResult.value = com.eshwar.rideconnectx.R.string.alerts_test_not_connected
            return
        }
        val clean = ClusterAlerts.unreadCount.value == 0 && ClusterAlerts.missedCount.value == 0
        ClusterAlerts.onNotification('N', "RideConnectX", "Test alert")
        _testResult.value = com.eshwar.rideconnectx.R.string.alerts_test_sent
        appScope.launch {
            kotlinx.coroutines.delay(8_000)
            // Leave real unread counts alone; only undo the test's own count and lamp.
            if (clean) ClusterAlerts.resetCounts() else ClusterAlerts.clearNotification()
            _testResult.value = null
        }
    }

    // An app Android hides from us (package visibility) falls back to its last package word.
    private val labels = java.util.concurrent.ConcurrentHashMap<String, String>()
    private fun appLabel(pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            context.packageManager.let { it.getApplicationLabel(it.getApplicationInfo(pkg, 0)).toString() }
        }.getOrElse { fallbackAppLabel(pkg) }
    }

    val settings: StateFlow<AppSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /**
     * Owned by Screen 18, shown here too. Reading the one store keeps the two
     * switches from disagreeing about the same preference.
     */
    val serviceReminders: StateFlow<Boolean> =
        serviceRepository.remindersEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** For the Service row's "Next: in X km" line. */
    val serviceStatus: StateFlow<com.eshwar.rideconnectx.domain.model.ServiceStatus> =
        serviceRepository.status
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.eshwar.rideconnectx.domain.model.ServiceStatus())

    val connectionState: StateFlow<ConnectionState> =
        bleRepository.connectionState
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectionState.Idle)

    /** A scooter was paired before: Connect now has something to reconnect to. */
    val hasSavedScooter: StateFlow<Boolean> = session.lastDeviceAddress
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Reconnects to the last paired scooter (the repository does nothing if there is none). */
    fun connectNow() = bleRepository.reconnectLastDevice()

    fun disconnect() = bleRepository.disconnect()

    /** The vehicle the rider set up, for the Bluetooth section's summary row. */
    val vehicleName: StateFlow<String> = vehicleRepository.selection
        .map { it.vehicle?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /**
     * The account card at the top of Settings — the rider's first question was
     * "where is the profile", and a settings screen with no sign of who is
     * signed in is the reason it was hard to find.
     */
    val account: StateFlow<UserSession> = combine(
        userPrefs.session,
        userPrefs.riderName,
    ) { session, riderName ->
        session.copy(name = riderName.ifBlank { session.name })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSession())

    fun setDistanceUnit(unit: DistanceUnit) = appScope.launch { store.setDistanceUnit(unit) }


    fun setRideNotifications(on: Boolean) = appScope.launch { store.setRideNotifications(on) }
    fun setConnectionAlerts(on: Boolean) = appScope.launch { store.setConnectionAlerts(on) }
    fun setServiceReminders(on: Boolean) =
        appScope.launch { serviceRepository.setRemindersEnabled(on) }

    /**
     * Forgets the paired scooter: drops the link, then clears the stored
     * address so auto-reconnect stops finding it. Both halves are needed —
     * disconnecting alone would let the next app start reconnect.
     */
    fun forgetVehicle() = appScope.launch {
        bleRepository.disconnect()
        session.clearSession()
    }
}

/** One row of the Settings notification app list. */
data class NotificationApp(val pkg: String, val label: String, val tier: AppTier, val on: Boolean)

/**
 * The apps that posted, grouped by tier (Important first), A to Z inside a group.
 * [seen] holds package names only and never an ignored app; an unlisted package
 * (a dialer that posted a call) counts as Important so it can still be switched.
 * A missing switch is the tier default: Important ON, Medium OFF.
 */
/** Name for a package Android hides or that is uninstalled: known apps by name, else the last word capitalised. */
fun fallbackAppLabel(pkg: String): String = when (pkg) {
    "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
    "org.telegram.messenger", "org.telegram.messenger.web" -> "Telegram"
    "com.instagram.android" -> "Instagram"
    "com.facebook.orca" -> "Messenger"
    "com.facebook.katana" -> "Facebook"
    "com.google.android.gm" -> "Gmail"
    "com.google.android.apps.messaging" -> "Messages"
    "com.google.android.dialer" -> "Phone"
    "org.thoughtcrime.securesms" -> "Signal"
    "com.snapchat.android" -> "Snapchat"
    else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}

fun buildNotificationApps(
    seen: Set<String>,
    switches: Map<String, Boolean>,
    defaultSms: String?,
    labelOf: (String) -> String,
): List<NotificationApp> = seen.map { pkg ->
    val tier = AppTiers.tierOf(pkg, false, defaultSms).let { if (it == AppTier.IGNORED) AppTier.IMPORTANT else it }
    NotificationApp(pkg, labelOf(pkg), tier, switches[pkg] ?: AppTiers.route(tier, null).cluster)
}.sortedWith(compareBy({ it.tier }, { it.label.lowercase() }))
