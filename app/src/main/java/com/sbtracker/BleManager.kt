package com.sbtracker

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
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Scanning     : ConnectionState()
        object Connecting   : ConnectionState()
        data class Reconnecting(val attempt: Int) : ConnectionState()
        data class Connected(val deviceName: String, val address: String) : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _statusBytes = MutableSharedFlow<Pair<ByteArray, String>>(extraBufferCapacity = 64)
    val statusBytes: SharedFlow<Pair<ByteArray, String>> = _statusBytes.asSharedFlow()

    private val _extendedBytes = MutableSharedFlow<Pair<ByteArray, String>>(extraBufferCapacity = 16)
    val extendedBytes: SharedFlow<Pair<ByteArray, String>> = _extendedBytes.asSharedFlow()

    private val _identityBytes = MutableSharedFlow<Pair<ByteArray, String>>(extraBufferCapacity = 8)
    val identityBytes: SharedFlow<Pair<ByteArray, String>> = _identityBytes.asSharedFlow()

    private val _displaySettingsBytes = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
    val displaySettingsBytes: SharedFlow<ByteArray> = _displaySettingsBytes.asSharedFlow()

    private val _firmwareBytes = MutableSharedFlow<ByteArray>(extraBufferCapacity = 4)
    val firmwareBytes: SharedFlow<ByteArray> = _firmwareBytes.asSharedFlow()

    private val managerScope   = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val commandQueue   = BleCommandQueue()

    private var gatt:          BluetoothGatt?                   = null
    private var txChar:        BluetoothGattCharacteristic?     = null
    @Volatile private var pendingResponse: CompletableDeferred<ByteArray>? = null
    @Volatile private var pendingWriteAck: CompletableDeferred<Boolean>?  = null
    
    private var deviceAddress: String = ""
    @Volatile private var isIntentionalDisconnect = false

    data class ScannedDevice(val device: BluetoothDevice, val name: String, val rssi: Int)

    private val _scannedDevices = MutableSharedFlow<List<ScannedDevice>>(extraBufferCapacity = 4)
    val scannedDevices: SharedFlow<List<ScannedDevice>> = _scannedDevices.asSharedFlow()

    private var activeScanCallback: ScanCallback? = null
    private var pollingJob:         Job?           = null
    private var scanJob:            Job?           = null
    private var reconnectJob:       Job?           = null

    fun startScan() {
        val scanner = adapter()?.bluetoothLeScanner ?: return
        isIntentionalDisconnect = false
        _connectionState.value = ConnectionState.Scanning

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val found = mutableMapOf<String, ScannedDevice>()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = try { result.device.name } catch (_: SecurityException) { null }
                if (name == null || !name.startsWith("S&B ")) return
                found[result.device.address] = ScannedDevice(result.device, name, result.rssi)
            }

            override fun onScanFailed(errorCode: Int) {
                activeScanCallback = null
                _connectionState.value = ConnectionState.Disconnected
            }
        }

        activeScanCallback = cb
        try {
            scanner.startScan(emptyList(), settings, cb)
        } catch (_: SecurityException) {
            _connectionState.value = ConnectionState.Disconnected
            return
        }

        scanJob?.cancel()
        scanJob = managerScope.launch {
            delay(3000L)
            try { scanner.stopScan(cb) } catch (_: SecurityException) {}
            activeScanCallback = null

            val devices = found.values.toList()
            when {
                devices.isEmpty() -> {
                    _connectionState.value = ConnectionState.Disconnected
                }
                devices.size == 1 -> {
                    connectToDevice(devices[0].device)
                }
                else -> {
                    _scannedDevices.emit(devices.sortedByDescending { it.rssi })
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        isIntentionalDisconnect = false
        _connectionState.value = ConnectionState.Connecting
        connect(device)
    }

    fun disconnect() {
        isIntentionalDisconnect = true
        reconnectJob?.cancel()
        pollingJob?.cancel()
        
        activeScanCallback?.let { cb ->
            try { adapter()?.bluetoothLeScanner?.stopScan(cb) } catch (_: SecurityException) {}
            activeScanCallback = null
        }
        
        pendingResponse?.cancel(); pendingResponse = null
        pendingWriteAck?.cancel(); pendingWriteAck = null
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {}
        gatt          = null
        txChar        = null
        deviceAddress = ""
        _connectionState.value = ConnectionState.Disconnected
    }

    fun cleanup() {
        disconnect()
        commandQueue.shutdown()
        managerScope.cancel()
    }

    private fun connect(device: BluetoothDevice) {
        deviceAddress = device.address
        reconnectJob?.cancel()
        try {
            gatt = device.connectGatt(
                context, false, gattCallback, BluetoothDevice.TRANSPORT_LE
            )
        } catch (_: SecurityException) {
            handleUnexpectedDisconnect()
        }
    }

    private fun handleUnexpectedDisconnect() {
        pollingJob?.cancel()
        pendingResponse?.cancel(); pendingResponse = null
        pendingWriteAck?.cancel(); pendingWriteAck = null
        
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
        txChar = null

        if (isIntentionalDisconnect || deviceAddress.isEmpty()) {
            reconnectJob?.cancel(); reconnectJob = null
            _connectionState.value = ConnectionState.Disconnected
            return
        }

        // If we're already trying to reconnect, don't spawn a new job nested inside
        if (reconnectJob?.isActive == true) return

        reconnectJob = managerScope.launch {
            var attempt = 1
            val startTime = System.currentTimeMillis()
            val timeoutMs = 5 * 60 * 1000L // 5 minutes max
            
            while (isActive && !isIntentionalDisconnect) {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    _connectionState.value = ConnectionState.Disconnected
                    break
                }
                
                _connectionState.value = ConnectionState.Reconnecting(attempt)
                
                // Try connecting only if not already connected/connecting
                if (gatt == null) {
                    val device = try { adapter()?.getRemoteDevice(deviceAddress) } catch(_:Exception){ null }
                    if (device != null) {
                        try {
                            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                        } catch(_: SecurityException) {}
                    }
                }
                
                val delayMs = (1000L * (1L shl minOf(attempt, 6).toInt())).coerceAtMost(30000L) // up to 30s
                delay(delayMs)
                attempt++
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        try { gatt.discoverServices() } catch (_: SecurityException) {}
                    } else {
                        handleUnexpectedDisconnect()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    handleUnexpectedDisconnect()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) { 
                handleUnexpectedDisconnect()
                return 
            }
            
            // Try default S&B service (Venty, Veazy, new Crafty+)
            var svc = gatt.getService(BleConstants.SERVICE_UUID)
            var char = svc?.getCharacteristic(BleConstants.CHARACTERISTIC_UUID)
            
            // Fallback to Crafty primary service if default not found
            if (svc == null || char == null) {
                svc = gatt.getService(BleConstants.CRAFTY_SERVICE_2) ?: gatt.getService(BleConstants.CRAFTY_SERVICE_1)
                char = svc?.getCharacteristic(BleConstants.CHARACTERISTIC_UUID)
            }

            if (svc == null || char == null) {
                handleUnexpectedDisconnect()
                return
            }
            
            txChar = char
            try {
                gatt.setCharacteristicNotification(char, true)
                val cccd = char.getDescriptor(BleConstants.CCCD_UUID)
                if (cccd != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(cccd)
                    }
                } else {
                    onNotificationsReady(gatt)
                }
            } catch (_: SecurityException) {
                handleUnexpectedDisconnect()
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == BleConstants.CCCD_UUID && status == BluetoothGatt.GATT_SUCCESS) {
                onNotificationsReady(gatt)
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                handleUnexpectedDisconnect()
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val ack = pendingWriteAck
            if (ack != null && ack.isActive) {
                pendingWriteAck = null
                ack.complete(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            handleNotification(characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleNotification(value)
        }
    }

    private fun onNotificationsReady(gatt: BluetoothGatt) {
        reconnectJob?.cancel() // Stop trying to reconnect if we succeed
        val name = try { gatt.device.name ?: "S&B Device" } catch (_: SecurityException) { "S&B Device" }
        _connectionState.value = ConnectionState.Connected(name, gatt.device.address)

        commandQueue.enqueue {
            // Reference app sequence: CMD 0x02 (Reset), then 0x1D (Status), 0x01 (Basic), 0x04 (Extended)
            
            // CMD 0x02 - Initialization / Firmware request
            sendAndReceive(BlePacket.buildRequest(BleConstants.CMD_INITIAL_RESET))
                ?.let { bytes -> _firmwareBytes.emit(bytes) }

            // CMD 0x1D - Handshake status request
            sendAndReceive(BlePacket.buildRequest(BleConstants.CMD_INITIAL_STATUS))

            // CMD 0x01 - Initial status poll
            sendAndReceive(BlePacket.buildRequest(BleConstants.CMD_STATUS))
                ?.let { bytes -> _statusBytes.emit(bytes to gatt.device.address) }

            // CMD 0x04 - Initial extended data poll
            sendAndReceive(BlePacket.buildRequest(BleConstants.CMD_EXTENDED))
                ?.let { bytes -> _extendedBytes.emit(bytes to gatt.device.address) }

            // CMD 0x06 - Request brightness/vibration (requires 7-byte packet)
            sendAndReceive(BlePacket.buildBrightnessVibrationRequest())
                ?.let { bytes -> _displaySettingsBytes.emit(bytes) }
        }

        startPolling()
    }

    private fun handleNotification(value: ByteArray) {
        val pending = pendingResponse
        if (pending != null && pending.isActive) {
            pendingResponse = null
            pending.complete(value)
            return
        }
        when (value.firstOrNull()) {
            BleConstants.CMD_BRIGHTNESS_VIBRATION -> managerScope.launch { _displaySettingsBytes.emit(value) }
            BleConstants.CMD_FIRMWARE -> managerScope.launch { _firmwareBytes.emit(value) }
            BleConstants.CMD_DEVICE_NOTIFICATION -> Unit
            else -> Unit
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = managerScope.launch {
            var tick = 0
            while (isActive) {
                val addr = deviceAddress
                commandQueue.enqueue {
                    sendAndReceive(BlePacket.buildRequest(BleConstants.CMD_STATUS))?.let { bytes ->
                        _statusBytes.emit(bytes to addr)
                    }
                }
                if (tick % 60 == 0) {
                    commandQueue.enqueue {
                        sendAndReceive(BlePacket.buildRequest(BleConstants.CMD_EXTENDED))?.let { bytes ->
                            _extendedBytes.emit(bytes to addr)
                        }
                    }
                }
                if (tick % 120 == 0) {
                    commandQueue.enqueue {
                        sendAndReceive(BlePacket.buildRequest(BleConstants.CMD_IDENTITY))?.let { bytes ->
                            _identityBytes.emit(bytes to addr)
                        }
                    }
                }
                delay(500L)
                tick++
            }
        }
    }

    fun sendWrite(packet: ByteArray) {
        commandQueue.enqueue { writeWithResponse(packet) }
    }

    fun findDevice() {
        commandQueue.enqueue { writeWithResponse(BlePacket.buildFindDevice()) }
    }

    private suspend fun sendAndReceive(packet: ByteArray): ByteArray? {
        val char = txChar ?: return null
        val currentGatt = gatt ?: return null
        val deferred = CompletableDeferred<ByteArray>()
        pendingResponse = deferred
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentGatt.writeCharacteristic(char, packet, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                char.value = packet
                @Suppress("DEPRECATION")
                currentGatt.writeCharacteristic(char)
            }
            withTimeout(3_000L) { deferred.await() }
        } catch (_: Exception) {
            pendingResponse = null
            null
        }
    }

    private suspend fun writeWithResponse(packet: ByteArray) {
        val char = txChar ?: return
        val currentGatt = gatt ?: return
        val ack = CompletableDeferred<Boolean>()
        pendingWriteAck = ack
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentGatt.writeCharacteristic(char, packet, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                char.value = packet
                @Suppress("DEPRECATION")
                currentGatt.writeCharacteristic(char)
            }
            withTimeout(3_000L) { ack.await() }
        } catch (_: Exception) {
            pendingWriteAck = null
        }
    }

    private fun adapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
}
