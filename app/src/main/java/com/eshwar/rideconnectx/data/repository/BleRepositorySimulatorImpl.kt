package com.eshwar.rideconnectx.data.repository

import com.eshwar.rideconnectx.domain.model.BleDevice
import com.eshwar.rideconnectx.domain.model.OnboardingStatus
import com.eshwar.rideconnectx.domain.model.ScooterTelemetry
import com.eshwar.rideconnectx.domain.repository.BlePacket
import com.eshwar.rideconnectx.domain.repository.BleRepository
import com.eshwar.rideconnectx.domain.repository.ConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleRepositorySimulatorImpl @Inject constructor(
    private val rideLog: com.eshwar.rideconnectx.data.nav.RideLog,
) : BleRepository {

    companion object {
        /** Debug hook (N51): true makes the simulated scan end with nothing found. */
        @Volatile var noScooterInRange: Boolean = false

        /** Debug hook: the advertised name the simulator pretends to be (e.g. SBM010000000, SCE100000000). Default is the Access. */
        @Volatile var deviceName: String = "SAS210217219"

        /**
         * Configures the engine from [name] like the real link does at connect, builds the blank
         * 0x31 frame through the real builder and describes it. No battery or signal lines, so no 0x33.
         */
        /** Frames built exactly as BleRepositoryImpl builds them (same builder, same name helper). */
        internal fun callFrame(caller: String, whatsApp: Boolean): ByteArray =
            com.eshwar.rideconnectx.domain.ProtocolEngine.buildCallPacket(clusterAlertName(caller, "Incoming call"), whatsApp)

        internal fun messageFrame(app: Char, title: String, unread: Int, newAlert: Boolean): ByteArray {
            val who = if (newAlert) clusterAlertName(title, "New message") else ""
            return com.eshwar.rideconnectx.domain.ProtocolEngine.buildMessagePacket(app, who, unread, newAlert)
        }

        fun configureAndDescribe(name: String): String {
            val engine = com.eshwar.rideconnectx.domain.ProtocolEngine
            engine.configureForDevice(name)
            val frame = engine.buildNavigationPacket(clusterCode = 46, distanceMetres = 0, clock = "")
            val p = engine.profile
            return "SIM FRAME model=${p.family} type=0x%02X checksum=%s sum=0x%02X".format(
                frame[1].toInt() and 0xFF,
                if (p.complementChecksum) "complement" else "sum",
                frame[28].toInt() and 0xFF,
            )
        }
    }

    private fun rideLogLink(text: String) = rideLog.diag(
        "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())} $text",
    )

    private val burst = com.eshwar.rideconnectx.data.ble.AlertBurst()

    /** Same as the real writeAlertBurst: 3 writes 200 ms apart through sendPacket (TX lines), then ALERT WRITTEN. */
    private suspend fun writeAlertBurst(what: String, pkt: ByteArray) {
        val results = (1..3).map { i ->
            if (i > 1) delay(200)
            sendPacket(pkt).first()
        }
        rideLogLink("ALERT WRITTEN $what results=$results")
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    init {
        // Same ride-log lines as BleRepositoryImpl so the emulator can prove them (N43).
        val s = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        s.launch {
            var wasUp = false
            _connectionState.collect {
                val isUp = it is ConnectionState.Connected
                if (wasUp != isUp) rideLogLink(if (isUp) "LINK UP" else "LINK DOWN")
                wasUp = isUp
            }
        }
        val alerts = com.eshwar.rideconnectx.data.nav.ClusterAlerts
        val up = { _connectionState.value is ConnectionState.Connected }
        val pe = com.eshwar.rideconnectx.domain.ProtocolEngine
        val loop = com.eshwar.rideconnectx.data.ble.AlertLoop
        // Same loops, builders and arguments as BleRepositoryImpl.startAlertPusher.
        s.launch {
            alerts.callRinging.combine(_connectionState) { r, c -> r.takeIf { c is ConnectionState.Connected } }
                .collectLatest { r ->
                    if (r == null) return@collectLatest
                    val pkt = callFrame(r.caller, r.whatsApp)
                    loop.run(
                        max = loop.CALL_MAX_BURSTS,
                        onEnd = {
                            rideLogLink(
                                if (it >= loop.CALL_MAX_BURSTS) "ALERT LOOP call capped after $it bursts"
                                else "ALERT LOOP call ended after $it bursts",
                            )
                        },
                    ) { writeAlertBurst("call", pkt) }
                }
        }
        s.launch {
            alerts.callEnded.collect { e ->
                if (e.outcome == com.eshwar.rideconnectx.data.nav.ClusterAlerts.CallEnd.ANSWERED && up()) {
                    writeAlertBurst("callAnswered", pe.buildCallPacket(clusterAlertName(e.caller, "Incoming call"), answered = true))
                }
            }
        }
        s.launch {
            alerts.alerts.collectLatest { alert ->
                if (alert.isCall) return@collectLatest
                if (alert.newAlert) burst.setMessageLatch(false) else if (alert.bothZero) burst.setMessageLatch(true)
                if (!up()) {
                    rideLogLink("ALERT DROPPED (scooter not connected): call=false")
                    return@collectLatest
                }
                val pkt = messageFrame(alert.appIdentifier, alert.title, alert.unreadCount, alert.newAlert)
                loop.run(
                    max = if (alert.newAlert) loop.MESSAGE_BURSTS else 1,
                    onEnd = { rideLogLink("ALERT LOOP message ended after $it bursts") },
                ) { writeAlertBurst(if (alert.newAlert) "message" else "RECOUNT message count=${alert.unreadCount}", pkt) }
            }
        }
        s.launch {
            alerts.missedReset.collect {
                if (!up()) {
                    rideLogLink("ALERT DROPPED (scooter not connected): missedReset")
                    return@collect
                }
                writeAlertBurst("missedCall count=0", pe.buildMissedCallPacket(0, ""))
                burst.onClear(isCall = true, isMessage = false, beats = 1)
            }
        }
        s.launch {
            alerts.countsReset.collect {
                if (!up()) {
                    rideLogLink("ALERT DROPPED (scooter not connected): countsReset")
                    return@collect
                }
                writeAlertBurst("missedCall count=0", pe.buildMissedCallPacket(0, ""))
                writeAlertBurst("message count=0", pe.buildMessagePacket('N', "", 0, newAlert = false))
                burst.onClear(isCall = true, isMessage = true, beats = 1)
            }
        }
        s.launch {
            alerts.missedCalls.collect { mc ->
                if (!up()) {
                    rideLogLink("ALERT DROPPED (scooter not connected): missedCall count=${mc.count}")
                    return@collect
                }
                writeAlertBurst("missedCall count=${mc.count}", pe.buildMissedCallPacket(mc.count, mc.caller, mc.whatsApp))
            }
        }
        // Heartbeat 0x33 while connected, same flag logic as the real loop. The simulator has
        // no phone battery/signal source, so those are fixed neutral values (battery 5, bars 3, clock blank).
        s.launch {
            _connectionState.collectLatest { c ->
                if (c !is ConnectionState.Connected) return@collectLatest
                burst.reset()
                var wasMsg = false
                var wasCall = false
                while (true) {
                    alerts.expireStale()
                    val msgNow = alerts.notificationPending.value
                    val callNow = alerts.missedCallPending.value
                    if ((wasMsg && !msgNow) || (wasCall && !callNow)) {
                        burst.onClear(isCall = wasCall && !callNow, isMessage = wasMsg && !msgNow)
                    }
                    wasMsg = msgNow
                    wasCall = callNow
                    sendPacket(
                        pe.buildHeartbeatPacket(
                            batteryBucket = 5, isCharging = false, signalBars = 3, clockHHmmss = "000000",
                            notificationPending = burst.messageFlag, missedCallPending = burst.callFlag,
                        ),
                    ).first()
                    val wait = burst.intervalMs()
                    burst.afterBeat()
                    delay(wait)
                }
            }
        }
    }

    private val _onboardingStatus = MutableStateFlow(OnboardingStatus.NOT_STARTED)
    override val onboardingStatus: Flow<OnboardingStatus> = _onboardingStatus.asStateFlow()

    private val _telemetry = MutableStateFlow(ScooterTelemetry())
    override val telemetry: Flow<ScooterTelemetry> = _telemetry.asStateFlow()

    override fun reconnectLastDevice() = Unit

    override fun shutdown() {
        linkJob?.cancel()
        _connectionState.value = ConnectionState.Idle
    }

    override fun scanDevices(): Flow<List<BleDevice>> = flow {
        if (noScooterInRange) {
            // Like a real empty scan: no emission, the flow just ends (N51).
            delay(3000)
            return@flow
        }
        val mockDevices = listOf(
            BleDevice("$deviceName (virtual)", "00:11:22:33:44:55", -55),
        ).filter { com.eshwar.rideconnectx.data.ble.ScooterCandidateFilter.isClusterNameCandidate(deviceName) }
        while (true) {
            emit(mockDevices)
            delay(2000)
        }
    }

    override fun stopScan() {}

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    private var linkJob: kotlinx.coroutines.Job? = null

    /**
     * Like the real link: the attempt runs in the repository's own scope, so
     * the state stays true whoever called, and collecting is optional.
     */
    override fun connect(address: String): Flow<ConnectionState> {
        linkJob?.cancel()
        linkJob = scope.launch {
            _connectionState.value = ConnectionState.Connecting(address, "Simulated Scooter")
            delay(1500)
            // Same facts the real link records; the simulator never finds a bike service, so a
            // motorcycle name shows the waiting state.
            com.eshwar.rideconnectx.data.ble.LinkDiagnostics.reset()
            com.eshwar.rideconnectx.domain.ProtocolEngine.profileFor(deviceName).let {
                com.eshwar.rideconnectx.data.ble.LinkDiagnostics.setProfile(it.nameCode, it.family)
            }
            com.eshwar.rideconnectx.data.ble.LinkDiagnostics.update { it.copy(gattConnected = true, serviceFound = if (it.family == com.eshwar.rideconnectx.domain.ClusterFamily.MOTORCYCLE) false else true) }
            _connectionState.value = ConnectionState.Connected(address, "Simulated Scooter")
            rideLogLink(configureAndDescribe(deviceName))
            // Example readings so the dashboard has something to show.
            _telemetry.value = ScooterTelemetry(
                odometerKm = 12846, tripAKm = 42.6f, tripBKm = 318.2f, fuelSegments = 4, isValid = true,
            )
            // A short simulated ride: Trip A climbs 1.5 km over ~15 s, so ride
            // recording (Statistics) can be checked on the emulator.
            repeat(15) {
                delay(1000)
                val t = _telemetry.value
                _telemetry.value = t.copy(tripAKm = t.tripAKm + 0.1f, tripBKm = t.tripBKm + 0.1f)
            }
        }
        return _connectionState
    }

    override fun disconnect() {
        linkJob?.cancel()
        com.eshwar.rideconnectx.data.nav.ClusterAlerts.reset()
        _telemetry.value = ScooterTelemetry()
        _connectionState.value = ConnectionState.Disconnected("Disconnected", byUser = true)
    }

    override fun sendPacket(packet: ByteArray): Flow<Boolean> = flow {
        com.eshwar.rideconnectx.data.ble.TxFrameLog.log(rideLog, packet)
        emit(true)
    }

    override fun observeNotifications(): Flow<BlePacket> = flow {
        // Telemetry parsing suspended
    }

    override suspend fun updateOnboardingStatus(status: OnboardingStatus) {
        _onboardingStatus.value = status
    }
}
