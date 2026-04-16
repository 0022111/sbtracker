package com.sbtracker.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin wrapper over [BluetoothGatt]. Exposes a hot flow of connection state
 * and a channel of inbound packets. Writes are serialized via [WriteQueue] so
 * concurrent GATT ops never overlap.
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    sealed interface State {
        data object Disconnected : State
        data object Scanning     : State
        data object Connecting   : State
        data class  Connected(val address: String, val name: String?) : State
    }

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _state = MutableStateFlow<State>(State.Disconnected)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _packets = Channel<ByteArray>(Channel.UNLIMITED)
    val packets: Channel<ByteArray> = _packets

    private var gatt: BluetoothGatt? = null
    private var char: BluetoothGattCharacteristic? = null
    private val writeQueue = WriteQueue()

    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        _state.value = State.Scanning
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(Protocol.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    fun stopScan() {
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_state.value is State.Scanning) _state.value = State.Disconnected
    }

    fun connect(device: BluetoothDevice) {
        stopScan()
        _state.value = State.Connecting
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        char = null
        writeQueue.clear()
        _state.value = State.Disconnected
    }

    fun write(bytes: ByteArray) {
        val c = char ?: return
        val g = gatt ?: return
        writeQueue.submit(g, c, bytes)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            // First match wins. Ignore subsequent results that were queued before
            // stopScan() unregistered us.
            if (_state.value !is State.Scanning) return
            val device = result.device ?: return
            connect(device)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    g.close()
                    gatt = null
                    char = null
                    writeQueue.clear()
                    _state.value = State.Disconnected
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(Protocol.SERVICE_UUID)
            val c   = svc?.getCharacteristic(Protocol.CHARACTERISTIC_UUID)
            if (c == null) {
                Log.w(TAG, "primary service not found on ${g.device.address}")
                disconnect()
                return
            }
            char = c
            g.setCharacteristicNotification(c, true)
            c.getDescriptor(Protocol.CCCD_UUID)?.let { d ->
                d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(d)
            }
            _state.value = State.Connected(g.device.address, g.device.name)
            writeQueue.bind(g)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            _packets.trySend(c.value.copyOf())
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            _packets.trySend(c.value.copyOf())
            writeQueue.next()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            writeQueue.next()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            writeQueue.next()
        }
    }

    companion object { private const val TAG = "BleManager" }
}
