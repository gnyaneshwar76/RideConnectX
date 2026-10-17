package com.eshwar.rideconnectx.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred

sealed class GattCommand {
    @SuppressLint("MissingPermission")
    abstract fun execute(gatt: BluetoothGatt): Boolean

    class DiscoverServices : GattCommand() {
        @SuppressLint("MissingPermission")
        override fun execute(gatt: BluetoothGatt): Boolean = gatt.discoverServices()
    }

    class RequestMtu(val mtu: Int) : GattCommand() {
        @SuppressLint("MissingPermission")
        override fun execute(gatt: BluetoothGatt): Boolean = gatt.requestMtu(mtu)
    }

    class WriteDescriptor(val descriptor: BluetoothGattDescriptor, val value: ByteArray) : GattCommand() {
        @SuppressLint("MissingPermission")
        override fun execute(gatt: BluetoothGatt): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    /**
     * [result], when given, is completed with the real outcome: true once the
     * Bluetooth stack accepted the write (or a newer frame replaced this one),
     * false if it was refused after retries or the queue was cleared. Queuing
     * alone proves nothing - that was the "0x06=true" in the log that hid lost
     * writes (CHK-17).
     */
    class WriteCharacteristic(
        val characteristic: BluetoothGattCharacteristic,
        val value: ByteArray,
        val writeType: Int,
        val result: CompletableDeferred<Boolean>? = null,
    ) : GattCommand() {
        @SuppressLint("MissingPermission")
        override fun execute(gatt: BluetoothGatt): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, value, writeType) == BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.writeType = writeType
                @Suppress("DEPRECATION")
                characteristic.value = value
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
        }
    }
}

/**
 * The one-at-a-time rule for GATT operations, with no Android types so it can
 * be unit tested.
 *
 * Android allows one GATT operation in flight. A completion that arrives late
 * (after the "stuck" timeout let the next command go) or that belongs to no
 * command used to start the next command while another was still pending; the
 * stack answered "busy", and the command - possibly the 0x06 caller text - was
 * silently dropped. Now a refused retryable command goes back to the front and
 * is tried again on the next completion or add, up to [maxAttempts].
 */
class CommandQueueCore<C : Any>(
    private val execute: (C) -> Boolean,
    private val replaceKey: (C) -> Any? = { null },
    private val retryable: (C) -> Boolean = { false },
    private val onResult: (C, Outcome) -> Unit = { _, _ -> },
    private val now: () -> Long = { System.currentTimeMillis() },
    private val stuckMs: Long = 1_000L,
    private val maxAttempts: Int = 3,
) {
    enum class Outcome { ACCEPTED, SUPERSEDED, FAILED }

    private class Entry<C>(val cmd: C, var attempts: Int = 0)

    private val queue = ArrayDeque<Entry<C>>()
    private var busy = false
    private var startedAt = 0L

    val pending: Int @Synchronized get() = queue.size

    @Synchronized
    fun add(cmd: C) {
        // A queued command is out of date the moment a newer one with the same key exists.
        replaceKey(cmd)?.let { key ->
            val it = queue.iterator()
            while (it.hasNext()) {
                val e = it.next()
                if (replaceKey(e.cmd) == key) {
                    it.remove()
                    onResult(e.cmd, Outcome.SUPERSEDED)
                }
            }
        }
        queue.addLast(Entry(cmd))
        if (busy && now() - startedAt > stuckMs) busy = false
        pump()
    }

    @Synchronized
    fun completed() {
        busy = false
        pump()
    }

    @Synchronized
    fun clear() {
        busy = false
        val dropped = queue.toList()
        queue.clear()
        dropped.forEach { onResult(it.cmd, Outcome.FAILED) }
    }

    private fun pump() {
        while (!busy) {
            val e = queue.removeFirstOrNull() ?: return
            busy = true
            startedAt = now()
            val ok = try { execute(e.cmd) } catch (_: Exception) { false }
            if (ok) {
                onResult(e.cmd, Outcome.ACCEPTED)
                return
            }
            busy = false
            e.attempts++
            if (retryable(e.cmd) && e.attempts < maxAttempts) {
                queue.addFirst(e)
                return
            }
            onResult(e.cmd, Outcome.FAILED)
        }
    }
}

class GattCommandQueue {
    private companion object {
        const val TAG = "GattCommandQueue"
        const val NAVIGATION = 0x31.toByte()
    }

    @Volatile private var gatt: BluetoothGatt? = null

    /** Called when a command could not be run at all (e.g. the MTU request). */
    @Volatile var onFailed: ((GattCommand) -> Unit)? = null

    private val core = CommandQueueCore<GattCommand>(
        execute = { c ->
            val g = gatt
            if (g == null) false else {
                Log.d(TAG, "Executing command: ${c.javaClass.simpleName}")
                c.execute(g)
            }
        },
        // A queued navigation frame is out of date the moment a newer one exists.
        replaceKey = { c ->
            if (c is GattCommand.WriteCharacteristic && c.value.getOrNull(1) == NAVIGATION) NAVIGATION else null
        },
        retryable = { it is GattCommand.WriteCharacteristic },
        onResult = { c, outcome ->
            val ok = outcome != CommandQueueCore.Outcome.FAILED
            if (c is GattCommand.WriteCharacteristic) c.result?.complete(ok)
            if (!ok) {
                Log.e(TAG, "Command not run: ${c.javaClass.simpleName}")
                onFailed?.invoke(c)
            }
        },
    )

    fun addCommand(command: GattCommand, gatt: BluetoothGatt?) {
        if (gatt == null) {
            Log.e(TAG, "GATT is null, clearing queue.")
            core.clear()
            return
        }
        this.gatt = gatt
        core.add(command)
    }

    fun onCommandCompleted(gatt: BluetoothGatt?) {
        if (gatt != null) this.gatt = gatt
        core.completed()
    }

    fun clear() {
        core.clear()
        gatt = null
    }
}
