package com.eshwar.rideconnectx.data.ble

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.eshwar.rideconnectx.MainActivity
import com.eshwar.rideconnectx.R
import com.eshwar.rideconnectx.core.ble.GattCommand
import com.eshwar.rideconnectx.core.ble.GattCommandQueue
import com.eshwar.rideconnectx.domain.model.BleDevice
import com.eshwar.rideconnectx.core.di.ServiceEntryPoint
import com.eshwar.rideconnectx.domain.repository.BleRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

@AndroidEntryPoint
class BleForegroundService : Service() {
    private companion object {
        /** Standard Client Characteristic Configuration descriptor. */
        val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * the cluster GATT, confirmed by live discovery on
         * `SAS210217219`. The official app finds these by index rather than by
         * UUID (`services[3]`, characteristics `0` and `1`), so both routes are
         * used here: match by UUID first, fall back to the index the official
         * app relies on.
         */
        val CLUSTER_SERVICE_UUID: UUID = UUID.fromString("0000fefb-0000-1000-8000-00805f9b34fb")
        val CLUSTER_WRITE_NO_RESPONSE: UUID = UUID.fromString("00000001-0000-1000-8000-008025000000")
        val CLUSTER_NOTIFY: UUID = UUID.fromString("00000002-0000-1000-8000-008025000000")
        val CLUSTER_WRITE: UUID = UUID.fromString("00000003-0000-1000-8000-008025000000")

        const val CLUSTER_SERVICE_INDEX = 3
        const val CLUSTER_WRITE_INDEX = 0

        const val GATT_ERROR_133 = 133
        const val MAX_RETRIES = 3
        const val GATT_SETTLE_MS = 250L
        const val RETRY_DELAY_MS = 600L

        /** Link up but MTU/discovery never finished: give up and let the watcher retry. */
        const val SETUP_TIMEOUT_MS = 12_000L

        /** Shown only when the real advertised name cannot be read. */
        const val FALLBACK_DEVICE_NAME = "Scooter"
    }

    /**
     * Resolved on demand rather than injected: the repository is what binds
     * this service, and it is only needed on teardown.
     */
    private val bleRepository: BleRepository
        get() = EntryPointAccessors
            .fromApplication(applicationContext, ServiceEntryPoint::class.java)
            .bleRepository()

    private val TAG = "RCX-BLE"
    private val binder = LocalBinder()
    private var bluetoothGatt: BluetoothGatt? = null
    private val commandQueue = GattCommandQueue()
    private var connectedDeviceName: String = FALLBACK_DEVICE_NAME

    /**
     * The advertised name of the device currently connected, e.g. `SAS210217219`.
     *
     * Resolved from the [android.bluetooth.BluetoothDevice] at connect time,
     * which works on auto-reconnect where no scan result is cached. Null when it
     * could not be read, so callers can fall back to a stored name rather than
     * writing a placeholder over a real one.
     */
    val resolvedDeviceName: String?
        get() = connectedDeviceName.takeIf { it != FALLBACK_DEVICE_NAME && it.isNotBlank() }

    /** All GATT calls are funnelled through here — see [connect]. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Address of the in-flight attempt, kept so a 133 can be retried. */
    @Volatile private var pendingAddress: String? = null
    private var retryCount = 0

    /** True from the connect request until the link is up or finally failed. */
    val connectInProgress: Boolean get() = pendingAddress != null

    /** Set once service discovery has been judged (cluster found or not). */
    @Volatile private var setupDone = false

    private val setupWatchdog = Runnable {
        if (bluetoothGatt != null && linkedAddress != null && !setupDone) failLink("setup timed out")
    }

    init {
        // The MTU request is optional: if the stack refuses it, go straight on to
        // discovery, or the link sits "connected" with nothing ever written (CHK-19).
        commandQueue.onFailed = { cmd ->
            val gatt = bluetoothGatt
            if (cmd is GattCommand.RequestMtu && gatt != null) {
                Log.w(TAG, "MTU request refused - discovering services anyway")
                commandQueue.addCommand(GattCommand.DiscoverServices(), gatt)
            }
        }
    }

    /** The link is up but unusable. Drop it and report it, so it is retried. */
    private fun failLink(why: String) {
        Log.e(TAG, "Link unusable ($why) - dropping it so it can reconnect")
        closeGatt()
        pendingAddress = null
        retryCount = 0
        _connectionState.tryEmit(BluetoothProfile.STATE_DISCONNECTED)
        updateNotification("Disconnected")
    }

    private val _connectionState = MutableSharedFlow<Int>(replay = 1)
    val connectionState = _connectionState.asSharedFlow()

    /**
     * Emits once the write characteristic has been resolved.
     *
     * `STATE_CONNECTED` arrives before service discovery finishes, so anything
     * sent on connect alone is written into a void — which is exactly what
     * happened to the profile packet on the first real hardware run
     * (`Profile packet sent=false`). Callers that need to *write* wait for this.
     */
    private val _servicesReady = MutableSharedFlow<Unit>(replay = 1)
    val servicesReady = _servicesReady.asSharedFlow()

    /** Cleared on every fresh connect so a stale DISCONNECTED is not replayed. */
    fun resetConnectionState() {
        _connectionState.resetReplayCache()
        _servicesReady.resetReplayCache()
    }

    // replay = 1 and a real buffer, both deliberate.
    //
    // A bare MutableSharedFlow() is replay=0 / buffer=0, and on that flow
    // `tryEmit` can only succeed if a collector happens to be suspended and
    // ready at that instant — otherwise it returns false and throws the frame
    // away. Nothing checked the return value, so every telemetry packet the
    // cluster sent was silently discarded: the dashboard showed no odometer,
    // fuel or trip while the GATT log clearly held the frames.
    //
    // replay = 1 also means a screen that subscribes *after* a frame arrives
    // still sees the latest reading instead of waiting up to 5s for the next.
    private val _notifications = MutableSharedFlow<Pair<String, ByteArray>>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val notifications = _notifications.asSharedFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")
            // A client we already replaced must not drive the current link. On
            // 11 Sep 2026 an old client kept delivering frames while the live
            // one was gone — reads looked healthy, every write failed.
            if (gatt !== bluetoothGatt) {
                Log.w(TAG, "Ignoring state $newState from a stale GATT client — closing it")
                runCatching { gatt.close() }
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT connected to $connectedDeviceName")
                    linkedAddress = gatt.device.address
                    pendingAddress = null
                    retryCount = 0
                    _connectionState.tryEmit(newState)
                    updateNotification("Connected to $connectedDeviceName")
                    setupDone = false
                    mainHandler.removeCallbacks(setupWatchdog)
                    mainHandler.postDelayed(setupWatchdog, SETUP_TIMEOUT_MS)
                    commandQueue.addCommand(GattCommand.RequestMtu(517), gatt)
                }

                BluetoothProfile.STATE_CONNECTING -> {
                    Log.d(TAG, "GATT connecting")
                    _connectionState.tryEmit(newState)
                    updateNotification("Connecting to scooter...")
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT disconnected (status=$status)")
                    linkedAddress = null
                    // A 133 means the attempt never really got off the ground.
                    // Retry silently rather than flashing "disconnected" at the
                    // rider and giving up on the first try.
                    if (willRetry(status)) {
                        updateNotification("Retrying…")
                        retry()
                    } else {
                        pendingAddress = null
                        retryCount = 0
                        _connectionState.tryEmit(newState)
                        updateNotification("Disconnected")
                        cleanup()
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "onMtuChanged: mtu=$mtu, status=$status")
            if (gatt !== bluetoothGatt) return
            commandQueue.onCommandCompleted(gatt)
            commandQueue.addCommand(GattCommand.DiscoverServices(), gatt)
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== bluetoothGatt) return
            Log.d(TAG, "onServicesDiscovered: status=$status")
            commandQueue.onCommandCompleted(gatt)
            
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed with status=$status")
                failLink("service discovery status=$status")
                return
            }

            val services = gatt.services
            Log.i(TAG, "===== GATT PROFILE for $connectedDeviceName =====")
            Log.i(TAG, "Services discovered: ${services.size}")

            // The vendor protocol is not published, so the full profile is
            // logged on every connect. This is the raw material for working out
            // the cluster handshake — nothing here assumes a packet format.
            services.forEach { service ->
                Log.i(TAG, "SERVICE ${service.uuid}")
                service.characteristics.forEach { char ->
                    Log.i(TAG, "  CHAR ${char.uuid} props=${describeProps(char.properties)}")
                }
            }
            Log.i(TAG, "===== END GATT PROFILE =====")

            resolveWriteCharacteristic(gatt)

            // Subscribing to every notify characteristic means whatever the
            // scooter volunteers is captured, rather than guessed at.
            services.flatMap { it.characteristics }
                .filter { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 }
                .forEach { enableNotifications(gatt, it) }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            // A closed client's late completion would free the queue while the live one's write is in flight.
            if (gatt !== bluetoothGatt) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Notification enabled success: ${descriptor.characteristic.uuid}")
            } else {
                Log.d(TAG, "Notification enabled failed: ${descriptor.characteristic.uuid}")
            }
            commandQueue.onCommandCompleted(gatt)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (gatt !== bluetoothGatt) return
            commandQueue.onCommandCompleted(gatt)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            // Only the live client's frames count; a stale one repeats them.
            if (gatt !== bluetoothGatt) return
            // Full frame, not truncated: the 0x37 telemetry field mapping is
            // still being worked out and every byte matters.
            Log.d(TAG, "RX UUID=${characteristic.uuid} len=${value.size} hex=${value.toHexString()}")
            Log.d(TAG, "RX ascii=${value.joinToString("") { b -> if (b in 32..126) b.toInt().toChar().toString() else "." }}")
            _notifications.tryEmit(characteristic.uuid.toString() to value)
        }
        
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (gatt !== bluetoothGatt) return
            @Suppress("DEPRECATION")
            val value = characteristic.value
            Log.d(TAG, "Characteristic changed received (Legacy): UUID=${characteristic.uuid}, Length=${value?.size ?: 0}")
            value?.let { _notifications.tryEmit(characteristic.uuid.toString() to it) }
        }
    }

    /** The scooter the current client is actually connected to, or null. */
    @Volatile
    private var linkedAddress: String? = null

    /** The characteristic packets go out on, once services are discovered. */
    @Volatile
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    val isReadyToWrite: Boolean get() = writeCharacteristic != null && bluetoothGatt != null

    /**
     * Finds the outgoing characteristic.
     *
     * By UUID first, because that is unambiguous. Falling back to the service
     * and characteristic *index* the official app uses covers the case where a
     * different firmware revision exposes different UUIDs but the same layout.
     */
    private fun resolveWriteCharacteristic(gatt: BluetoothGatt) {
        // WRITE_NO_RESPONSE (00000001) first, deliberately. The official app's
        // transmit does `service.getCharacteristics().get(0)` — index 0 is the
        // write-no-response characteristic, not the plain WRITE at index 2.
        // Preferring 00000003 meant every packet went out on a line the cluster
        // does not read: writes were accepted by the stack, but the dashboard
        // showed nothing.
        val byUuid = gatt.getService(CLUSTER_SERVICE_UUID)?.let { service ->
            service.getCharacteristic(CLUSTER_WRITE_NO_RESPONSE)
                ?: service.getCharacteristic(CLUSTER_WRITE)
        }

        // No positional fallback. The old `services[3].characteristics[0]`
        // rule meant *any* peer that answered at the remembered address with
        // four services and a writable characteristic in that slot received the
        // rider's name, caller names and message previews — the address alone
        // was being treated as proof of the scooter. Requiring 0xFEFB makes the
        // vehicle identify itself before anything personal is written to it.
        // It also gates `servicesReady`, which the profile and alert writes
        // both await, so an unrecognised peer is simply never written to.
        val resolved = byUuid

        writeCharacteristic = resolved
        setupDone = true
        mainHandler.removeCallbacks(setupWatchdog)

        if (resolved == null) {
            Log.e(
                TAG,
                "No $CLUSTER_SERVICE_UUID write characteristic — not the cluster; nothing will be sent",
            )
        } else {
            Log.i(
                TAG,
                "Write characteristic: ${resolved.uuid} " +
                    "(${describeProps(resolved.properties)}) on service ${resolved.service.uuid}",
            )
            _servicesReady.tryEmit(Unit)
        }
    }

    /**
     * Sends one 30-byte packet. Returns false when there is nothing to write to,
     * so callers can report honestly rather than assume delivery.
     */
    fun writePacket(packet: ByteArray): Boolean = writePacketTracked(packet) != null

    /**
     * Queues the packet and returns a result that completes with what really
     * happened (true = the Bluetooth stack accepted it). Null when there is
     * nothing to write to.
     */
    @SuppressLint("MissingPermission")
    fun writePacketTracked(packet: ByteArray): kotlinx.coroutines.Deferred<Boolean>? {
        val gatt = bluetoothGatt
        val characteristic = writeCharacteristic

        if (gatt == null || characteristic == null) {
            Log.w(TAG, "writePacket ignored — not connected or characteristic unresolved")
            return null
        }

        val writeType =
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }

        Log.d(TAG, "Data Packet-Main ${packet.toHexString()}")
        val result = kotlinx.coroutines.CompletableDeferred<Boolean>()
        commandQueue.addCommand(
            GattCommand.WriteCharacteristic(characteristic, packet, writeType, result),
            gatt,
        )
        return result
    }

    /**
     * HIGH shortens the radio's connection interval (~11-15 ms instead of up to
     * ~50 ms), so a turn reaches the cluster at once. Only while a route is
     * running - it costs battery - and BALANCED again when it ends.
     */
    @SuppressLint("MissingPermission")
    fun setLowLatency(on: Boolean) {
        mainHandler.post {
            val priority = if (on) BluetoothGatt.CONNECTION_PRIORITY_HIGH else BluetoothGatt.CONNECTION_PRIORITY_BALANCED
            val ok = runCatching { bluetoothGatt?.requestConnectionPriority(priority) }.getOrNull()
            Log.d(TAG, "Connection priority ${if (on) "HIGH" else "BALANCED"} requested=$ok")
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private fun describeProps(props: Int) = buildList {
        if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NR")
        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
        if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
    }.joinToString("|").ifEmpty { "NONE" }

    /**
     * Turns on notifications for [char]: the local flag alone is not enough —
     * the peer only starts sending once its Client Characteristic Configuration
     * descriptor has been written too.
     */
    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        if (!gatt.setCharacteristicNotification(char, true)) {
            Log.w(TAG, "setCharacteristicNotification failed for ${char.uuid}")
            return
        }

        val descriptor = char.getDescriptor(CCC_DESCRIPTOR_UUID)
        if (descriptor == null) {
            Log.w(TAG, "No CCC descriptor on ${char.uuid}")
            return
        }

        val value = if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }

        Log.d(TAG, "Enabling notifications on ${char.uuid}")
        commandQueue.addCommand(GattCommand.WriteDescriptor(descriptor, value), gatt)
    }

    inner class LocalBinder : Binder() {
        fun getService(): BleForegroundService = this@BleForegroundService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundServiceWithNotification()
    }

    /**
     * A `connectedDevice` foreground service is only allowed to start while the
     * app actually holds a Bluetooth runtime permission. Without that guard this
     * threw SecurityException straight out of onCreate and took the process
     * down — which is what happened to any rider who skipped the permission
     * step and then opened Pair Vehicle.
     */
    private fun startForegroundServiceWithNotification(): Boolean {
        val notification = createNotification("Starting RideConnectX...")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Location too, when allowed, so the GPS metre countdown keeps
                // working with the screen off. Android refuses it when location
                // is not granted (or the app is in the background), so fall
                // back to Bluetooth only - the link must never fail over this.
                val withLocation = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                val locationGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.ACCESS_FINE_LOCATION,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val started = locationGranted && runCatching {
                    startForeground(1, notification, withLocation)
                }.isSuccess
                if (!started) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(1, notification)
            }
            true
        } catch (e: Exception) {
            // SecurityException on a missing permission, ForegroundServiceStartNot
            // AllowedException if we were launched from the background. Neither is
            // worth a crash: the pairing screen already reports missing permissions.
            Log.e(TAG, "Could not start foreground service — stopping", e)
            stopSelf()
            false
        }
    }

    /**
     * Opens a GATT link to [deviceAddress].
     *
     * Three things here are load-bearing on real hardware:
     *
     *  - **TRANSPORT_LE is explicit.** The three-argument `connectGatt` defaults
     *    to `TRANSPORT_AUTO`, which on a dual-mode device — and the official
     *    cluster is dual-mode, since it also carries calls and SMS — can pick
     *    BR/EDR instead of LE and fail with status 133.
     *  - **The call is posted to the main thread.** It used to run on
     *    Dispatchers.IO via the repository's flatMapLatest; off-main GATT calls
     *    are a known source of status 133 and silent no-ops.
     *  - **close() is followed by a settle delay.** Reusing the stack too
     *    quickly after a close is the other classic 133.
     */
    @SuppressLint("MissingPermission")
    fun connect(deviceAddress: String) {
        mainHandler.post {
            Log.d(TAG, "Connect requested: $deviceAddress")
            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = manager.adapter
            if (adapter == null || !adapter.isEnabled) {
                Log.e(TAG, "Bluetooth adapter null or disabled, cannot connect")
                _connectionState.tryEmit(BluetoothProfile.STATE_DISCONNECTED)
                return@post
            }

            val device = try {
                adapter.getRemoteDevice(deviceAddress)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Malformed address", e)
                _connectionState.tryEmit(BluetoothProfile.STATE_DISCONNECTED)
                return@post
            }

            connectedDeviceName = device.name ?: FALLBACK_DEVICE_NAME

            // Reconnect paths (the watcher, app start, the pairing screen) can
            // ask for the scooter we are already linked to. Tearing that link
            // down to rebuild it is how the dashboard went dark mid-test on
            // 11 Sep 2026. Report it as connected and keep it.
            if (linkedAddress == deviceAddress && bluetoothGatt != null && writeCharacteristic != null) {
                Log.d(TAG, "Already linked to $deviceAddress — keeping the live link")
                _connectionState.tryEmit(BluetoothProfile.STATE_CONNECTED)
                _servicesReady.tryEmit(Unit)
                return@post
            }

            pendingAddress = deviceAddress
            retryCount = 0

            closeGatt()
            mainHandler.postDelayed({ openGatt(device) }, GATT_SETTLE_MS)
        }
    }

    @SuppressLint("MissingPermission")
    private fun openGatt(device: BluetoothDevice) {
        Log.d(TAG, "connectGatt(TRANSPORT_LE) -> ${device.address}, attempt ${retryCount + 1}")
        bluetoothGatt = device.connectGatt(
            this,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE,
        )
    }

    /**
     * Status 133 is the Android stack's catch-all GATT error and is very often
     * transient. Retrying after a full close is the accepted workaround.
     */
    private fun willRetry(status: Int): Boolean =
        status == GATT_ERROR_133 && pendingAddress != null && retryCount < MAX_RETRIES

    @SuppressLint("MissingPermission")
    private fun retry() {
        val address = pendingAddress ?: return
        retryCount++
        Log.w(TAG, "status=133 — retry $retryCount/$MAX_RETRIES for $address")
        closeGatt()

        mainHandler.postDelayed({
            val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            adapter?.getRemoteDevice(address)?.let(::openGatt)
        }, RETRY_DELAY_MS)
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        mainHandler.removeCallbacks(setupWatchdog)
        bluetoothGatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        bluetoothGatt = null
        linkedAddress = null
        writeCharacteristic = null
        commandQueue.clear()
    }

    fun getDeviceByAddress(address: String): BleDevice? {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: return null
        return try {
            val device = adapter.getRemoteDevice(address)
            BleDevice(name = device.name, address = device.address, rssi = 0)
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        Log.d(TAG, "User disconnect requested")
        // Clearing this first stops the 133 path from "helpfully" reconnecting
        // straight after a deliberate disconnect.
        pendingAddress = null
        retryCount = 0
        mainHandler.post { bluetoothGatt?.disconnect() }
    }

    @SuppressLint("MissingPermission")
    private fun cleanup() {
        closeGatt()
    }

    /**
     * Closing the app from recents used to leave the scooter still showing the
     * Bluetooth glyph, because the GATT client was never closed. It is now.
     */
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed — closing GATT")
        pendingAddress = null
        retryCount = 0
        mainHandler.removeCallbacksAndMessages(null)
        closeGatt()
        super.onDestroy()
    }

    /**
     * The rider swiped the app out of recents.
     *
     * `stopSelf()` alone was not enough: the repository holds a binding, and a
     * bound service is not destroyed by stopSelf — which is why the scooter kept
     * showing the Bluetooth glyph. Going through the repository makes it unbind
     * first, so the service really does go away.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed — shutting down BLE")
        closeGatt()
        runCatching { bleRepository.shutdown() }
            .onFailure { Log.e(TAG, "Shutdown via repository failed", it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, createNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ride_connect_x",
                "RideConnectX BLE Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val disconnectIntent = Intent(this, BleForegroundService::class.java).apply {
            action = "ACTION_DISCONNECT"
        }
        val disconnectPendingIntent = PendingIntent.getService(this, 1, disconnectIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "ride_connect_x")
            .setContentTitle("RideConnectX")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectPendingIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_DISCONNECT") {
            // Through the repository, so it also marks this as the rider's own
            // disconnect; otherwise the reconnect watcher undid it within 15 s.
            runCatching { bleRepository.disconnect() }.onFailure { disconnect() }
        } else {
            // Ensure service is in foreground if started again
            startForegroundServiceWithNotification()
        }
        // NOT_STICKY, deliberately: START_STICKY had Android resurrecting this
        // after the rider swiped the app away, which is exactly what they did
        // not want. The connection is re-established when the app is reopened.
        return START_NOT_STICKY
    }
}
