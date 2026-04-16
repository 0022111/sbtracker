package com.sbtracker.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.sbtracker.R
import com.sbtracker.data.Db
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the single BLE connection for the app.
 *
 * It does three jobs:
 *   1. Keep the GATT link alive through Doze / task death.
 *   2. Poll status at a fast cadence while the heater is on, slow when idle.
 *   3. Drop every inbound packet into the god log.
 */
class BleService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var ble: BleManager
    private lateinit var db:  Db

    private var pollJob: Job? = null
    private var heaterOn = false

    private val _deviceType = MutableStateFlow("Unknown")
    val deviceType: StateFlow<String> = _deviceType.asStateFlow()

    inner class LocalBinder : Binder() {
        val service: BleService get() = this@BleService
    }

    val state: StateFlow<BleManager.State> get() = ble.state

    override fun onCreate() {
        super.onCreate()
        ble = BleManager(this)
        db  = Db.get(this)
        ensureChannel()
        startForegroundWithNotice()
        listenForPackets()
        listenForConnection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        ble.disconnect()
        super.onDestroy()
    }

    fun startScan() = ble.startScan()
    fun disconnect() { pollJob?.cancel(); ble.disconnect() }

    fun setHeater(on: Boolean) = ble.write(Packet.setHeater(on))
    fun setTemperature(tempC: Int) = ble.write(Packet.setTemperature(tempC))

    private fun listenForPackets() = scope.launch {
        for (bytes in ble.packets) {
            val address = (ble.state.value as? BleManager.State.Connected)?.address ?: continue
            when (bytes.firstOrNull()) {
                Protocol.CMD_STATUS -> {
                    val status = Packet.parseStatus(bytes, address, _deviceType.value) ?: continue
                    db.status().insert(status)
                    val nowOn = Protocol.isHeaterOn(status.heaterMode)
                    if (nowOn != heaterOn) {
                        heaterOn = nowOn
                        restartPolling()
                    }
                }
                Protocol.CMD_EXTENDED -> {
                    Packet.parseExtended(bytes, address)?.let { db.extended().upsert(it) }
                }
                Protocol.CMD_IDENTITY -> {
                    Packet.parseIdentity(bytes, address)?.let {
                        _deviceType.value = it.deviceType
                        db.info().upsert(it)
                    }
                }
            }
        }
    }

    /** Restart polling so the interval switches immediately on heater-state change. */
    private fun restartPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            // Request identity + extended once, then stream status.
            ble.write(Packet.request(Protocol.CMD_IDENTITY))
            ble.write(Packet.request(Protocol.CMD_EXTENDED))
            while (isActive) {
                ble.write(Packet.request(Protocol.CMD_STATUS))
                delay(if (heaterOn) FAST_POLL_MS else SLOW_POLL_MS)
                if (heaterOn) {
                    // Refresh lifetime counters occasionally.
                    ble.write(Packet.request(Protocol.CMD_EXTENDED))
                }
            }
        }
    }

    /** Start / stop polling in lock-step with the connection state. */
    private fun listenForConnection() = scope.launch {
        ble.state.collect { s ->
            if (s is BleManager.State.Connected) restartPolling()
            else { pollJob?.cancel(); pollJob = null }
        }
    }

    // ── Foreground notification ─────────────────────────────────────────────

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "BLE Link", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun startForegroundWithNotice() {
        val n: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("SBTracker")
            .setContentText("Tracking your device")
            .setSmallIcon(R.drawable.ic_notif)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    companion object {
        private const val CHANNEL_ID     = "sbtracker.ble"
        private const val NOTIFICATION_ID = 1
        private const val FAST_POLL_MS: Long = 500L
        private const val SLOW_POLL_MS: Long = 30_000L
    }
}
