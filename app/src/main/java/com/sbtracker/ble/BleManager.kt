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
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timeoutJob: Job? = null

    fun startScan() {
        val adapter = adapter ?: run { Log.w(TAG, "no bluetooth adapter"); return }
        if (!adapter.isEnabled) { Log.w(TAG, "bluetooth adapter disabled"); return }

        // Bonded-device shortcut: if the user has paired an S&B device before,
        // connect straight to it. The official app does this and it avoids a
        // full scan cycle when the device happens to be off / not advertising.
        adapter.bondedDevices?.firstOrNull { d -> d.name?.let(::looksLikeStorzBickel) == true }
            ?.let {
                Log.i(TAG, "bonded device found: ${it.name} (${it.address})")
                _state.value = State.Scanning
                connect(it)
                return
            }

        val scanner = adapter.bluetoothLeScanner ?: run { Log.w(TAG, "no BLE scanner"); return }
        _state.value = State.Scanning

        // No ScanFilter: S&B devices don't always include their 128-bit service
        // UUID in the 31-byte advertisement payload. We match on the scan record
        // in onScanResult (name OR service UUID) and confirm via GATT connect.
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .build()
        scanner.startScan(null, settings, scanCallback)
        Log.i(TAG, "scan started")

        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (_state.value is State.Scanning) {
                Log.w(TAG, "scan timeout — no S&B device found in ${SCAN_TIMEOUT_MS / 1000}s")
                stopScan()
            }
        }
    }

    fun stopScan() {
        timeoutJob?.cancel()
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_state.value is State.Scanning) _state.value = State.Disconnected
    }

    fun connect(device: BluetoothDevice) {
        stopScan()
        // Close any stale handle before opening a new one. Without this, every
        // failed attempt leaks a conn ID in the BT stack and eventually the
        // whole GATT client falls over ("Ignore unknown conn ID …").
        closeGatt()
        _state.value = State.Connecting
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        timeoutJob?.cancel()
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        gatt?.disconnect()
        closeGatt()
        _state.value = State.Disconnected
    }

    private fun closeGatt() {
        gatt?.close()
        gatt = null
        char = null
        writeQueue.clear()
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
            val device  = result.device ?: return
            val name    = device.name ?: result.scanRecord?.deviceName
            val uuids   = result.scanRecord?.serviceUuids.orEmpty()
            val hasSbUuid = uuids.any { it.uuid == Protocol.SERVICE_UUID }
            val nameMatch = name?.let(::looksLikeStorzBickel) == true
            // Log everything we see — this is the scan-diagnosis channel.
            Log.d(TAG, "scan: name=$name addr=${device.address} uuids=$uuids rssi=${result.rssi}")
            if (!hasSbUuid && !nameMatch) return
            Log.i(TAG, "scan match: $name (${device.address}) via ${if (hasSbUuid) "uuid" else "name"}")
            connect(device)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed: $errorCode")
            timeoutJob?.cancel()
            _state.value = State.Disconnected
        }
    }

    private fun looksLikeStorzBickel(name: String): Boolean {
        val n = name.uppercase()
        return "STORZ"   in n ||
               "BICKEL"  in n ||
               "VENTY"   in n ||
               "VEAZY"   in n ||
               "CRAFTY"  in n ||
               "MIGHTY"  in n ||
               "VOLCANO" in n ||
               "S&B"     in n
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.i(TAG, "conn state: status=$status newState=$newState addr=${g.device.address}")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.w(TAG, "connected with error status $status — closing")
                        closeGatt()
                        _state.value = State.Disconnected
                        return
                    }
                    // Give the stack a beat before discovering services — some
                    // chipsets need a moment, otherwise discoverServices returns
                    // stale / partial results. Keep the callback snappy, delay
                    // on a background coroutine.
                    scope.launch {
                        delay(600)
                        gatt?.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    closeGatt()
                    _state.value = State.Disconnected
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "service discovery failed: $status")
                disconnect()
                return
            }
            val svc = g.getService(Protocol.SERVICE_UUID)
            val c   = svc?.getCharacteristic(Protocol.CHARACTERISTIC_UUID)
            if (c == null) {
                Log.w(TAG, "primary S&B service not found on ${g.device.address}")
                disconnect()
                return
            }
            char = c
            g.setCharacteristicNotification(c, true)
            val cccd = c.getDescriptor(Protocol.CCCD_UUID)
            if (cccd == null) {
                Log.w(TAG, "CCCD missing — promoting without notifications")
                promoteToConnected(g)
                return
            }
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val ok = g.writeDescriptor(cccd)
            if (!ok) {
                Log.w(TAG, "writeDescriptor enqueue rejected — promoting anyway")
                promoteToConnected(g)
            }
            // Otherwise wait for onDescriptorWrite to promote us.
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
            if (d.uuid == Protocol.CCCD_UUID && _state.value is State.Connecting) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "CCCD write failed: $status — promoting anyway, polling will cover")
                }
                promoteToConnected(g)
                return
            }
            writeQueue.next()
        }
    }

    /** Flip to Connected and hand the GATT to the write queue — single entry point. */
    private fun promoteToConnected(g: BluetoothGatt) {
        _state.value = State.Connected(g.device.address, g.device.name)
        writeQueue.bind(g)
    }

    companion object {
        private const val TAG = "BleManager"
        private const val SCAN_TIMEOUT_MS: Long = 20_000L
    }
}
