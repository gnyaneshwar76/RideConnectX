package com.eshwar.rideconnectx.data.repository

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.eshwar.rideconnectx.data.ble.BleForegroundService
import com.eshwar.rideconnectx.data.ble.AlertBurst
import com.eshwar.rideconnectx.data.ble.AlertLoop
import com.eshwar.rideconnectx.data.ble.BleScannerImpl
import com.eshwar.rideconnectx.data.ble.ReconnectPolicy
import com.eshwar.rideconnectx.data.ble.WatchAction
import com.eshwar.rideconnectx.data.ble.ScooterCandidateFilter
import com.eshwar.rideconnectx.data.local.ServicePreferencesStore.Companion.MAX_PLAUSIBLE_ODO_KM
import com.eshwar.rideconnectx.data.local.SessionDataStore
import com.eshwar.rideconnectx.domain.model.BleDevice
import com.eshwar.rideconnectx.domain.model.OnboardingStatus
import com.eshwar.rideconnectx.domain.model.ScooterTelemetry
import com.eshwar.rideconnectx.domain.repository.BlePacket
import com.eshwar.rideconnectx.domain.repository.BleRepository
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: BleScannerImpl,
    private val sessionDataStore: SessionDataStore,
    private val userPreferences: com.eshwar.rideconnectx.data.local.UserPreferencesStore,
    private val phoneStatus: com.eshwar.rideconnectx.data.ble.PhoneStatusProvider,
    private val appSettings: com.eshwar.rideconnectx.data.local.AppSettingsStore,
    private val rideLog: com.eshwar.rideconnectx.data.nav.RideLog,
) : BleRepository {
    private val TAG = "RCX-BLE"
    private val linkPrefs by lazy { context.getSharedPreferences("ble_link", Context.MODE_PRIVATE) }
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var bleService: BleForegroundService? = null
    private val _isServiceBound = MutableStateFlow(false)
    
    /**
     * True once the rider has explicitly disconnected or forgotten the vehicle.
     *
     * Without it the watcher below would treat a deliberate disconnect as a
     * dropout and reconnect within seconds, making the Disconnect button look
     * broken.
     */
    @Volatile private var userDisconnected = false

    /**
     * True until the rider opens the app, and again once they swipe it away.
     *
     * The rider's report, 30 Sep: after removing the app from recents - even
     * after Force stop - it was running again with its notification up. Android
     * re-binds the Maps listener, which restarts the process, and the watcher
     * below then reconnected and started the foreground service by itself.
     * shutdown() never told the watcher to stop either, so a swipe was undone
     * three seconds later.
     */
    @Volatile private var dormant = closedByRider()

    /**
     * Whether the app was last closed on purpose (swiped away, backed out of, or
     * force-stopped), as opposed to Android killing it to free memory.
     *
     * 1 Oct ride: starting dormant in EVERY new process meant that when Android
     * restarted the app in the background mid-ride, the scooter was never
     * reconnected until the app was opened by hand.
     */
    private fun closedByRider(): Boolean {
        if (linkPrefs.getBoolean(KEY_CLOSED, false)) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val last = runCatching {
            context.getSystemService(android.app.ActivityManager::class.java)
                .getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()?.reason
        }.getOrNull()
        return last == android.app.ApplicationExitInfo.REASON_USER_REQUESTED ||
            last == android.app.ApplicationExitInfo.REASON_USER_STOPPED
    }

    private fun setClosed(closed: Boolean) {
        dormant = closed
        // commit, not apply: the process can die straight after a swipe.
        linkPrefs.edit().putBoolean(KEY_CLOSED, closed).commit()
    }

    /** The newest connection attempt; replaced on every retry. */
    private var linkJob: kotlinx.coroutines.Job? = null

    /**
     * Keeps trying to bring the link back while the app is alive.
     *
     * **This did not exist.** `reconnectLastDevice()` was called from exactly one
     * place - `MainActivity.onCreate` when `savedInstanceState == null` - so it
     * only ever fired on a *cold app launch*. Switching the scooter off and on
     * with the app open left it disconnected forever, and the only way back was
     * to kill and reopen the app. The rider hit this on 18 August and reported
     * it as 'not reconnecting even though I opened app and kept there'.
     *
     * It also masked a second problem: with no link there is no heartbeat, so
     * the message and missed-call lamps could never light either.
     *
     * Backs off from 3s to 15s so a scooter that is simply out of range does not
     * sit there scanning and draining the phone.
     */
    private fun startReconnectWatcher() {
        repositoryScope.launch {
            var delayMs = ReconnectPolicy.MIN_MS
            // Let the constructor finish first: the state flow is declared after init.
            kotlinx.coroutines.delay(1_000)
            while (true) {
                val address = sessionDataStore.lastDeviceAddress.first()
                val action = ReconnectPolicy.decide(
                    connected = _connectionState.value is ConnectionState.Connected,
                    dormant = dormant,
                    userDisconnected = userDisconnected,
                    autoConnect = appSettings.settings.first().autoConnect,
                    hasAddress = !address.isNullOrBlank(),
                    connectInProgress = bleService?.connectInProgress == true,
                )
                // Sleeps instead of polling every 3 s: with the app closed or
                // linked there is no timer at all (CHK-15). The search length
                // for a switched-off scooter is unchanged (owner decision).
                when (action) {
                    WatchAction.WAIT_FOR_DROP -> {
                        _connectionState.first { it !is ConnectionState.Connected }
                        delayMs = ReconnectPolicy.MIN_MS
                        kotlinx.coroutines.delay(ReconnectPolicy.MIN_MS)
                    }
                    WatchAction.SLEEP_UNTIL_WOKEN -> {
                        wake.receive()
                        delayMs = ReconnectPolicy.MIN_MS
                    }
                    WatchAction.SLEEP_LONG ->
                        withTimeoutOrNull(ReconnectPolicy.IDLE_RECHECK_MS) { wake.receive() }
                    WatchAction.WAIT_SHORT -> kotlinx.coroutines.delay(ReconnectPolicy.MIN_MS)
                    WatchAction.CONNECT -> {
                        Log.d(TAG, "Reconnect watcher: retrying $address (next in ${delayMs}ms)")
                        startLink(address!!, fromWatcher = true)
                        kotlinx.coroutines.delay(delayMs)
                        // Widen the gap only while it keeps failing.
                        delayMs = ReconnectPolicy.nextDelay(delayMs)
                    }
                }
            }
        }
    }

    private fun rideLogLink(text: String) = rideLog.diag(
        "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())} $text",
    )

    /** Wakes the watcher when the rider acts (open, connect, reconnect). */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** The call / message flag as the cluster should see it; see [AlertBurst]. */
    private val burst = AlertBurst()

    /** Makes the heartbeat loop send now instead of waiting out its interval. */
    private val heartbeatWake = Channel<Unit>(Channel.CONFLATED)

    // Declared before init: the init-time collectors and pushers read these.
    private val odometerGuard = OdometerGuard()

    @Volatile private var lastTeleLog: String? = null
    private val _telemetry = MutableStateFlow(ScooterTelemetry())
    override val telemetry: Flow<ScooterTelemetry> = _telemetry.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)

    /** Single source of truth — see [BleRepository.connectionState]. */
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()


    /**
     * Pushes an alert to the cluster the moment it arrives.
     *
     * The lamp used to ride only on the 5s heartbeat, so a message landing just
     * after a tick waited up to five seconds. The rider's objection, 18 August,
     * is the right one: a lamp that appears seconds late - or a lamp with no
     * sender name - makes them take the phone out mid-road to see who it is,
     * which is the hazard the cluster is supposed to remove.
     *
     * Two things go out, in this order:
     *  1. `0x06`, carrying the app, the sender and the message. This is what
     *     puts a NAME on the dashboard. `buildNotificationPacket` had existed
     *     since the protocol work and was never called from anywhere, so the
     *     name had genuinely never been transmitted.
     *  2. after a short gap, the `0x33` heartbeat carrying the flag, repeated
     *     about every second for a few beats and then cleared.
     *
     * End to end this is a BLE write, so tens of milliseconds - bounded by the
     * radio, not by our polling.
     */
    private fun startAlertPusher() {
        val alerts = com.eshwar.rideconnectx.data.nav.ClusterAlerts
        // Call: repeat 0x32 (full name every time) while ringing; collectLatest
        // cancels the loop at answer / end / reject and on disconnect.
        repositoryScope.launch {
            alerts.callRinging.combine(_connectionState) { r, c -> r.takeIf { c is ConnectionState.Connected } }
                .collectLatest { r ->
                    if (r == null) return@collectLatest
                    val pkt = com.eshwar.rideconnectx.domain.ProtocolEngine.buildCallPacket(
                        clusterAlertName(r.caller, "Incoming call"), r.whatsApp,
                    )
                    AlertLoop.run(
                        max = AlertLoop.CALL_MAX_BURSTS,
                        onEnd = {
                            rideLogLink(
                                if (it >= AlertLoop.CALL_MAX_BURSTS) "ALERT LOOP call capped after $it bursts"
                                else "ALERT LOOP call ended after $it bursts",
                            )
                        },
                    ) {
                        writeAlertBurst("call", pkt)
                    }
                }
        }
        // Answered: one burst with byte 23 '2', as the official app does, then nothing.
        repositoryScope.launch {
            alerts.callEnded.collect { e ->
                if (e.outcome != com.eshwar.rideconnectx.data.nav.ClusterAlerts.CallEnd.ANSWERED) return@collect
                if (_connectionState.value !is ConnectionState.Connected) return@collect
                writeAlertBurst("callAnswered", com.eshwar.rideconnectx.domain.ProtocolEngine.buildCallPacket(clusterAlertName(e.caller, "Incoming call"), answered = true))
            }
        }
        // Messages: new = 3 bursts 1 s apart (AlertLoop); recount = one burst, 'N', no blink.
        repositoryScope.launch {
            alerts.alerts.collectLatest { alert ->
                if (alert.isCall) return@collectLatest // the ringing loop owns calls
                if (alert.newAlert) burst.setMessageLatch(false) else if (alert.bothZero) burst.setMessageLatch(true)
                if (_connectionState.value !is ConnectionState.Connected) {
                    rideLogLink("ALERT DROPPED (scooter not connected): call=false")
                    return@collectLatest
                }
                // Never the message body (N81): no printable-ASCII sender -> fixed label.
                val who = if (alert.newAlert) clusterAlertName(alert.title, "New message") else ""
                val pkt = com.eshwar.rideconnectx.domain.ProtocolEngine.buildMessagePacket(
                    alert.appIdentifier, who, alert.unreadCount, alert.newAlert,
                )
                AlertLoop.run(
                    max = if (alert.newAlert) AlertLoop.MESSAGE_BURSTS else 1,
                    onEnd = { rideLogLink("ALERT LOOP message ended after $it bursts") },
                ) { writeAlertBurst(if (alert.newAlert) "message" else "RECOUNT message count=${alert.unreadCount}", pkt) }
            }
        }
        // Missed-call notification removed (official m9116f): 0x34 count 0, byte 15 'Y' one beat.
        repositoryScope.launch {
            alerts.missedReset.collect {
                if (_connectionState.value !is ConnectionState.Connected) {
                    rideLogLink("ALERT DROPPED (scooter not connected): missedReset")
                    return@collect
                }
                writeAlertBurst("missedCall count=0", com.eshwar.rideconnectx.domain.ProtocolEngine.buildMissedCallPacket(0, ""))
                burst.onClear(isCall = true, isMessage = false, beats = 1)
                heartbeatWake.trySend(Unit)
            }
        }
        // Clear pressed: count 0 on both, heartbeat flags 'Y' for one beat.
        repositoryScope.launch {
            alerts.countsReset.collect {
                if (_connectionState.value !is ConnectionState.Connected) {
                    rideLogLink("ALERT DROPPED (scooter not connected): countsReset")
                    return@collect
                }
                val pe = com.eshwar.rideconnectx.domain.ProtocolEngine
                writeAlertBurst("missedCall count=0", pe.buildMissedCallPacket(0, ""))
                writeAlertBurst("message count=0", pe.buildMessagePacket('N', "", 0, newAlert = false))
                burst.onClear(isCall = true, isMessage = true, beats = 1)
                heartbeatWake.trySend(Unit)
            }
        }
    }

    /** One alert burst: 3 writes 200 ms apart, logged as ALERT WRITTEN. */
    private suspend fun writeAlertBurst(what: String, pkt: ByteArray) {
        val results = (1..ALERT_WRITES).map { i ->
            if (i > 1) kotlinx.coroutines.delay(ALERT_GAP_MS)
            sendPacket(pkt).first()
        }
        rideLogLink("ALERT WRITTEN $what results=$results")
    }

    /** Missed-call 0x34: same 3 writes, 200 ms apart, once. */
    private fun startMissedCallPusher() {
        repositoryScope.launch {
            com.eshwar.rideconnectx.data.nav.ClusterAlerts.missedCalls.collect { mc ->
                if (_connectionState.value !is ConnectionState.Connected) {
                    rideLogLink("ALERT DROPPED (scooter not connected): missedCall count=${mc.count}")
                    return@collect
                }
                writeAlertBurst(
                    "missedCall count=${mc.count}",
                    com.eshwar.rideconnectx.domain.ProtocolEngine.buildMissedCallPacket(mc.count, mc.caller, mc.whatsApp),
                )
            }
        }
    }

    init {
        // Last known readings first, so the dashboard has something true to show
        // before (or without) a live frame. Marked valid because these ARE real
        // readings from this vehicle - just not necessarily current.
        //
        // Followed, not read once: sign-out clears the cache and sign-in restores
        // the account's own copy from the cloud, and the dashboard must follow
        // both. A cleared cache always wins, even mid-disconnect, so the next
        // account never sees the previous one's readings.
        repositoryScope.launch {
            sessionDataStore.cachedTelemetry.collect { c ->
                if (c == null) {
                    _telemetry.value = ScooterTelemetry()
                    return@collect
                }
                // Live frames also land in the cache; while connected they are
                // already on screen.
                if (_connectionState.value is ConnectionState.Connected) return@collect
                _telemetry.value = ScooterTelemetry(
                    odometerKm = c.odometerKm,
                    tripAKm = c.tripAKm,
                    tripBKm = c.tripBKm,
                    fuelSegments = c.fuelSegments,
                    fuelLevel = c.fuelSegments / 5f,
                    batteryPercent = c.batteryPercent,
                    isValid = true,
                )
            }
        }
        // Remember a close-by-rider read from the exit reason, so a later
        // Android kill cannot make the next process forget it (CHK-16).
        if (dormant) linkPrefs.edit().putBoolean(KEY_CLOSED, true).apply()
        startReconnectWatcher()
        startAlertPusher()
        startMissedCallPusher()
        repositoryScope.launch {
            observeNotifications().collect { packet ->
                val telemetry = com.eshwar.rideconnectx.domain.ProtocolEngine.parseTelemetry(packet.data)
                telemetry?.let {
                    if (!odometerGuard.accept(_telemetry.value.odometerKm, it.odometerKm)) {
                        Log.w(TAG, "Odometer ${it.odometerKm} km held back until a second frame confirms it")
                        return@let
                    }
                    _telemetry.value = it
                    // Diagnostic: shows whether the cluster streams Trip A and
                    // whether it climbs while riding — ride stats are built from
                    // Trip A increases. Logged only on change, so it is not spammy.
                    val teleLine = "TELE tripA=${it.tripAKm} odoKm=${it.odometerKm} " +
                        "tripB=${it.tripBKm} fuel=${it.fuelSegments} batt=${it.batteryPercent}"
                    if (teleLine != lastTeleLog) { lastTeleLog = teleLine; rideLogLink(teleLine) }
                    // Persisted so the dashboard survives a dropout and a restart.
                    //
                    // The parser now rejects a bad byte-28 checksum, but a
                    // frame can still pass by chance. The service odometer is
                    // guarded by `isPlausibleOdometer`, but this cache is what
                    // the dashboard card actually shows and it had no guard at
                    // all — which is how a 6,001,923 km reading stayed on screen
                    // against a real 2,248. Don't persist a reading the service
                    // store would refuse; the live value is still shown.
                    if (it.odometerKm > MAX_PLAUSIBLE_ODO_KM) {
                        Log.w(TAG, "Not caching implausible odometer ${it.odometerKm} km")
                        return@let
                    }
                    sessionDataStore.cacheTelemetry(
                        odometerKm = it.odometerKm,
                        tripAKm = it.tripAKm,
                        tripBKm = it.tripBKm,
                        fuelSegments = it.fuelSegments,
                        batteryPercent = it.batteryPercent,
                    )
                }
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleForegroundService.LocalBinder
            bleService = binder.getService()
            _isServiceBound.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            _isServiceBound.value = false
        }
    }

    /**
     * True when the app holds the Bluetooth permissions the platform demands
     * before a `connectedDevice` foreground service may run. Starting the
     * service without them is an immediate SecurityException.
     */
    private val hasBluetoothPermission: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            ).all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun ensureServiceStarted(): Boolean {
        if (!hasBluetoothPermission) {
            Log.w(TAG, "Bluetooth permission not granted — not starting BLE service")
            return false
        }
        if (!_isServiceBound.value) {
            val intent = Intent(context, BleForegroundService::class.java)
            val bound = runCatching {
                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)
            // Android 12+ refuses startForegroundService when the process was
            // restarted in the background (CHK-18). The bound service still
            // carries the link, so only give up when both failed.
            val started = runCatching { context.startForegroundService(intent) }
                .onFailure { Log.w(TAG, "startForegroundService refused: ${it.javaClass.simpleName}") }
                .isSuccess
            if (!bound && !started) return false
        }
        return true
    }

    override val onboardingStatus: Flow<OnboardingStatus> = sessionDataStore.onboardingStatus.map {
        try { OnboardingStatus.valueOf(it) } catch (e: Exception) { OnboardingStatus.NOT_STARTED }
    }

    override suspend fun updateOnboardingStatus(status: OnboardingStatus) {
        sessionDataStore.saveOnboardingStatus(status.name)
    }

    override fun scanDevices(): Flow<List<BleDevice>> {
        if (!ensureServiceStarted()) return flowOf(emptyList())
        return scanner.scanDevices()
    }

    override fun stopScan() {
        scanner.stopScan()
    }

    /**
     * Starts the link to [address] and returns the app-wide [connectionState].
     *
     * The attempt runs in the repository's own scope, so the state stays true
     * whoever called: it used to be published only while the caller kept
     * collecting, and a ViewModel that was cleared froze it (dashboard said
     * Connected with the scooter off, no reconnect after a first pairing -
     * CHK-14). Collecting the result is optional and cancelling the collector
     * changes nothing.
     */
    override fun connect(address: String): Flow<ConnectionState> {
        Log.d(TAG, "Connect requested: $address")
        userDisconnected = false
        if (dormant) setClosed(false)
        wake.trySend(Unit)
        startLink(address)
        return _connectionState
    }

    /**
     * Opens the link. A connect already running or finished for the same
     * address is left alone (restarting it leaked a GATT client).
     * [fromWatcher] attempts never override a close or a Disconnect by the
     * rider; checked under the same lock [shutdown] takes, so a swipe-away
     * cannot be undone by a watcher attempt already on its way.
     */
    @Synchronized
    private fun startLink(address: String, fromWatcher: Boolean = false) {
        if (fromWatcher && (dormant || userDisconnected)) return
        val cur = _connectionState.value
        val same = (cur is ConnectionState.Connected && cur.address == address) ||
            (cur is ConnectionState.Connecting && cur.address == address)
        if (same && linkJob?.isActive == true) return

        val bluetooth = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
        if (bluetooth?.adapter?.isEnabled == false) {
            _connectionState.value = ConnectionState.Failed("Bluetooth is turned off.")
            return
        }
        if (!ensureServiceStarted()) {
            _connectionState.value = ConnectionState.Failed("Bluetooth permission is required to connect.")
            return
        }
        linkJob?.cancel()
        linkJob = repositoryScope.launch {
            linkStates(address).collect {
                val wasUp = _connectionState.value is ConnectionState.Connected
                _connectionState.value = it
                if (it is ConnectionState.Disconnected) com.eshwar.rideconnectx.data.nav.ClusterAlerts.reset() // official C4272e, no packet
                val isUp = it is ConnectionState.Connected
                if (wasUp != isUp) rideLogLink(if (isUp) "LINK UP" else "LINK DOWN")
            }
        }
    }

    /** The service's link states for [address], mapped; the replay cache is cleared first so a stale DISCONNECTED is not seen. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun linkStates(address: String): Flow<ConnectionState> {
        return _isServiceBound.filter { it }.flatMapLatest {
            val service = bleService
                ?: return@flatMapLatest flowOf(ConnectionState.Failed("Service unavailable"))

            val name = service.getDeviceByAddress(address)?.name ?: "Scooter"
            val savedAddress = sessionDataStore.lastDeviceAddress.first()

            if (!ScooterCandidateFilter.isScooterCandidate(name, address, savedAddress)) {
                Log.d(TAG, "Unsupported device rejected: $address ($name)")
                return@flatMapLatest flowOf(ConnectionState.UnsupportedDevice)
            }

            service.resetConnectionState()

            service.connectionState
                .onStart {
                    // Runs once the collector is attached, so no state is missed.
                    Log.d(TAG, "Subscribed; issuing connect for $address")
                    service.connect(address)
                }
                .map { state ->
                    when (state) {
                        android.bluetooth.BluetoothProfile.STATE_CONNECTED -> {
                            Log.d(TAG, "GATT connected: $address")
                            // Cancel the previous wait before starting a new
                            // one. Without this, a peer that never resolved
                            // 0xFEFB left a coroutine parked on `servicesReady`
                            // forever — and the reconnect watcher retries every
                            // 3-30 s, so they accumulated. Worse, `servicesReady`
                            // is a SharedFlow: when the *real* scooter later
                            // connected and emitted, every parked collector woke
                            // and wrote ITS OWN stale address into the session,
                            // re-poisoning the very value this check protects.
                            sessionSaveJob?.cancel()
                            sessionSaveJob = repositoryScope.launch {
                                // Not trusted until the cluster identifies
                                // itself. A remembered address is not proof of
                                // the vehicle, and saving the peer here is what
                                // made the *next* silent auto-reconnect treat an
                                // impostor as the scooter. `servicesReady` only
                                // fires once the 0xFEFB write characteristic
                                // resolved.
                                bleService?.servicesReady?.first()
                                // Prefer the name the service resolved from the
                                // connected device: on auto-reconnect there is
                                // no cached scan result, so [name] above is the
                                // placeholder. Storing that loses the model,
                                // and `configureForDevice` reads the checksum
                                // branch out of it — right for the Access only
                                // by luck, wrong for an e-ACCESS or TFT.
                                val resolved = service.resolvedDeviceName
                                    ?: sessionDataStore.lastDeviceName.first()
                                    ?: name
                                sessionDataStore.saveSession(address, resolved)
                            }
                            // Status first: it is a single packet, and starting
                            // it after the ten profile repeats left the cluster
                            // without battery or signal for ten seconds.
                            startHeartbeat()
                            // The cluster's WELCOME line comes from the profile
                            // packet, so it goes out as part of connecting.
                            sendProfile()
                            ConnectionState.Connected(address, name)
                        }
                        android.bluetooth.BluetoothProfile.STATE_CONNECTING ->
                            ConnectionState.Connecting(address, name)
                        android.bluetooth.BluetoothProfile.STATE_DISCONNECTED ->
                            ConnectionState.Disconnected("Scooter disconnected")
                        else -> ConnectionState.Failed("Connection failed")
                    }
                }
        }
    }

    /**
     * Reconnects to the last vehicle. Called on app start; a failure is silent
     * because the rider did not ask for anything — if the bike is off or out of
     * range the pairing screen is still there.
     */
    override fun onAppOpened() {
        if (dormant) setClosed(false)
        wake.trySend(Unit)
    }

    override fun reconnectLastDevice() {
        userDisconnected = false
        setClosed(false)
        wake.trySend(Unit)
        repositoryScope.launch {
            val address = sessionDataStore.lastDeviceAddress.first()
            if (address.isNullOrBlank()) {
                Log.d(TAG, "No previously paired vehicle — nothing to reconnect")
                return@launch
            }
            if (_connectionState.value is ConnectionState.Connected) return@launch

            Log.d(TAG, "Auto-reconnecting to $address")
            startLink(address)
        }
    }

    override fun disconnect() {
        Log.d(TAG, "User disconnect requested")
        // Stops the reconnect watcher from immediately undoing this.
        userDisconnected = true
        linkJob?.cancel()
        heartbeatJob?.cancel()
        profileJob?.cancel()
        burst.reset()
        com.eshwar.rideconnectx.data.nav.ClusterAlerts.reset()
        bleService?.disconnect()
        _connectionState.value = ConnectionState.Disconnected("Disconnected", byUser = true)
    }

    /**
     * Full teardown: drops the GATT link, unbinds and stops the service.
     *
     * Called when the app is cleared from recents. Until there is an explicit
     * "allow background activity" setting, closing the app means closing the
     * connection — the scooter should not keep showing the Bluetooth glyph.
     */
    @Synchronized
    override fun shutdown() {
        Log.d(TAG, "Shutdown requested — dropping link and stopping service")
        setClosed(true)
        linkJob?.cancel()
        profileJob?.cancel()
        heartbeatJob?.cancel()
        burst.reset()
        bleService?.disconnect()
        scanner.stopScan()

        if (_isServiceBound.value) {
            runCatching { context.unbindService(serviceConnection) }
            _isServiceBound.value = false
        }
        bleService = null

        runCatching {
            context.stopService(Intent(context, BleForegroundService::class.java))
        }

        _connectionState.value = ConnectionState.Idle
        // Deliberately NOT cleared. Wiping it here is what made every reading
        // vanish the moment the rider disconnected, replacing an odometer they
        // had just read with a dash. The values stay as last-known.
    }

    /**
     * Writes [packet] to the vehicle.
     *
     * Reports what actually happened: false when there is no link or the write
     * characteristic has not been resolved, so the Navigation screen says
     * "not relaying" instead of claiming a delivery that never left the phone.
     */
    override fun setLowLatency(on: Boolean) {
        bleService?.setLowLatency(on)
    }

    override fun sendPacket(packet: ByteArray): Flow<Boolean> = flow {
        val service = bleService
        com.eshwar.rideconnectx.data.ble.TxFrameLog.log(rideLog, packet)
        val tracked = if (service == null || !service.isReadyToWrite) null else service.writePacketTracked(packet)
        if (tracked == null) {
            Log.d(TAG, "sendPacket(${packet.size} bytes) - no writable link")
            emit(false)
            return@flow
        }
        // True only once the Bluetooth stack took the write, not when it was queued.
        emit(withTimeoutOrNull(WRITE_WAIT_MS) { tracked.await() } ?: false)
    }

    /**
     * Sends the rider's name so the cluster can greet them, mirroring what the
     * official app does on connect.
     */
    /** The pending "save this peer once it proves itself" wait. One at a time. */
    private var sessionSaveJob: kotlinx.coroutines.Job? = null

    private var profileJob: kotlinx.coroutines.Job? = null

    /**
     * The official app does not send the profile once on connect — it sends it
     * from a repeating timer (`C4956y.run()`). A single write on connect may
     * simply be missed, which is the most likely reason the cluster never
     * printed WELCOME. This mirrors the repeat.
     */
    // Synchronized: every connect() collector calls this on STATE_CONNECTED,
    // often at the same instant. Unsynchronized, two callers each cancel the
    // same old job and each launch a new one, and the loser is orphaned for good.
    @Synchronized
    private fun sendProfile() {
        profileJob?.cancel()
        profileJob = repositoryScope.launch {
            // Waits for service discovery rather than firing on STATE_CONNECTED:
            // the write characteristic does not exist yet at that point, which
            // the first hardware run proved by reporting sent=false.
            bleService?.servicesReady?.first()

            val clusterName = sessionDataStore.lastDeviceName.first()

            // The checksum branch depends on the vehicle model, which the
            // official app reads out of the advertised name.
            com.eshwar.rideconnectx.domain.ProtocolEngine.configureForDevice(clusterName)

            // Byte 27 answers "is this the same cluster as last time?". Read the
            // previous value *before* overwriting it, or the comparison is
            // always true and the greeting never changes.
            val previousCluster = sessionDataStore.previousClusterName.first()
            val flag = com.eshwar.rideconnectx.domain.ProtocolEngine.profileFlagFor(
                deviceName = clusterName,
                previousDeviceName = previousCluster,
            )
            clusterName?.takeIf { it.isNotBlank() }?.let { sessionDataStore.rememberCluster(it) }

            val name = userPreferences.riderName.first().ifBlank { return@launch }
            val packet = com.eshwar.rideconnectx.domain.ProtocolEngine.buildProfilePacket(name, flag)

            repeat(PROFILE_REPEATS) { attempt ->
                val sent = sendPacket(packet).first()
                Log.d(
                    TAG,
                    "Profile '$name' flag='$flag' (prev=$previousCluster now=$clusterName) " +
                        "attempt ${attempt + 1}/$PROFILE_REPEATS sent=$sent",
                )
                // Delay *after* the write, and skip it on the final pass, so the
                // greeting burst finishes as early as it can.
                if (attempt < PROFILE_REPEATS - 1) kotlinx.coroutines.delay(PROFILE_INTERVAL_MS)
            }
        }
    }

    private var heartbeatJob: kotlinx.coroutines.Job? = null

    /**
     * Sends the 0x33 status packet on a repeating timer for as long as the link
     * is up — phone battery, signal bars, the clock, and the message and
     * missed-call indicators.
     *
     * The official app does the same from `C4956y.run()`. It has to repeat:
     * the cluster shows the *last* status it received, so a single write would
     * leave a stale battery reading on the dashboard for the whole ride.
     */
    // Synchronized for the same reason as [sendProfile]. Without it the 11 Sep
    // 2026 log showed seven heartbeat loops alive at once — every beat written
    // seven times in the same millisecond from seven threads.
    @Synchronized
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        com.eshwar.rideconnectx.data.nav.ClusterAlerts.reset()
        burst.reset()
        heartbeatJob = repositoryScope.launch {
            bleService?.servicesReady?.first()

            var failures = 0
            var wasMsg = false
            var wasCall = false
            while (kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.isActive == true) {
                // Bytes 14/15 are CLEAR flags: 'N' normally, 'Y' for a few beats
                // only when an alert ends (hold over, call ended). See AlertBurst.
                com.eshwar.rideconnectx.data.nav.ClusterAlerts.expireStale()
                val msgNow = com.eshwar.rideconnectx.data.nav.ClusterAlerts.notificationPending.value
                val callNow = com.eshwar.rideconnectx.data.nav.ClusterAlerts.missedCallPending.value
                if ((wasMsg && !msgNow) || (wasCall && !callNow)) {
                    burst.onClear(isCall = wasCall && !callNow, isMessage = wasMsg && !msgNow)
                }
                wasMsg = msgNow
                wasCall = callNow
                logSignalChange()
                val packet = com.eshwar.rideconnectx.domain.ProtocolEngine.buildHeartbeatPacket(
                    batteryBucket = phoneStatus.batteryBucket(),
                    isCharging = phoneStatus.isCharging(),
                    signalBars = phoneStatus.signalBars(),
                    clockHHmmss = phoneStatus.clockHHmmss(),
                    notificationPending = burst.messageFlag,
                    missedCallPending = burst.callFlag,
                )

                val sent = sendPacket(packet).first()
                val waitMs = burst.intervalMs()
                if (sent) {
                    failures = 0
                    burst.afterBeat()
                } else if (++failures >= 3 || bleService?.isReadyToWrite != true) {
                    Log.d(TAG, "Heartbeat not written ($failures) - link gone, stopping")
                    return@launch
                }

                withTimeoutOrNull(waitMs) { heartbeatWake.receive() }
            }
        }
    }

    private var lastSignalLog: String? = null

    /** Logging only: one SIGNAL line when the value that goes into the heartbeat changes. */
    @android.annotation.SuppressLint("MissingPermission")
    private fun logSignalChange() {
        try {
            val bars = phoneStatus.signalBars()
            var level = -1
            var nr = false
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                level = tm?.signalStrength?.level ?: -1
                nr = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    tm?.dataNetworkType == android.telephony.TelephonyManager.NETWORK_TYPE_NR
            }
            val line = "SIGNAL level=$level bars=$bars nr=$nr"
            if (line != lastSignalLog) {
                lastSignalLog = line
                rideLogLink(line)
            }
        } catch (_: Exception) {
            // logging must never stop the heartbeat
        }
    }

    private companion object {
        const val WRITE_WAIT_MS = 3_000L
        const val ALERT_WRITES = 3
        const val ALERT_GAP_MS = 200L
        const val KEY_CLOSED = "closed_by_rider"
        /** Matches the official app's ~200 ms cadence for repeated writes. */
        const val PROFILE_INTERVAL_MS = 1000L
        const val PROFILE_REPEATS = 10
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeNotifications(): Flow<BlePacket> {
        return _isServiceBound.filter { it }.flatMapLatest {
            bleService?.notifications?.map { (uuid, data) ->
                BlePacket(uuid, data)
            } ?: emptyFlow()
        }
    }
}

/**
 * Keeps a garbled telemetry frame off the dashboard.
 *
 * 30 Sep 2026, on the road: the odometer flashed ~3 lakh km while the scooter
 * reconnected, then went back. The byte-28 check lets roughly 1 in 128 bad
 * frames through (two accepted branches), and nothing guarded what was SHOWN -
 * only what was cached. A real reading repeats in every frame, a garbled one
 * does not, so a reading far from the last one is shown only once the next
 * frame agrees with it.
 */
internal class OdometerGuard(private val maxStepKm: Int = 5) {
    private var unconfirmed = -1

    fun accept(lastKm: Int, km: Int): Boolean {
        if (km <= 0 || km > MAX_PLAUSIBLE_ODO_KM) return false
        if (lastKm <= 0 || km in lastKm..lastKm + maxStepKm || km == unconfirmed) {
            unconfirmed = -1
            return true
        }
        unconfirmed = km
        return false
    }
}

/** Sender name for the cluster: the name if it has printable ASCII, else [label]. Never the body. */
internal fun clusterAlertName(name: String, label: String): String =
    if (com.eshwar.rideconnectx.domain.ProtocolEngine.sanitizeString(name).isEmpty()) label else name
