package com.sbtracker.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import java.util.ArrayDeque

/**
 * Serializes GATT writes. The Android stack rejects a write issued while a
 * previous op (write / read / descriptor-write) is in flight — so we only
 * dispatch the next op when the callback for the previous one fires.
 */
@SuppressLint("MissingPermission")
class WriteQueue {

    private val queue = ArrayDeque<ByteArray>()
    private var gatt:    BluetoothGatt? = null
    private var char:    BluetoothGattCharacteristic? = null
    private var inFlight = false

    @Synchronized
    fun bind(gatt: BluetoothGatt) {
        this.gatt = gatt
        pump()
    }

    @Synchronized
    fun submit(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, bytes: ByteArray) {
        this.gatt = gatt
        this.char = char
        queue.addLast(bytes)
        pump()
    }

    @Synchronized
    fun next() {
        inFlight = false
        pump()
    }

    @Synchronized
    fun clear() {
        queue.clear()
        inFlight = false
        gatt = null
        char = null
    }

    private fun pump() {
        if (inFlight) return
        val g = gatt ?: return
        val c = char ?: return
        val bytes = queue.pollFirst() ?: return
        c.value = bytes
        inFlight = g.writeCharacteristic(c)
        if (!inFlight) {
            // Write failed to enqueue — don't stall the pipeline.
            pump()
        }
    }
}
