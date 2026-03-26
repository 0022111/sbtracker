package com.sbtracker

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service to keep the BLE connection alive and handle background tasks
 * like charging ETA notifications and session tracking.
 *
 * Owns a direct reference to [BleManager] so it can independently trigger reconnection
 * on [START_STICKY] restarts — i.e. when the process was killed and the OS brought the
 * service back without MainActivity or BleViewModel being alive yet.
 */
@AndroidEntryPoint
class BleService : Service() {

    @Inject lateinit var bleManager: BleManager

    // Default dispatcher — not Main, this is a background service.
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private var bleViewModel: BleViewModel? = null

    private val NOTIFICATION_ID = 101

    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAllChannels(this)
        startForeground(NOTIFICATION_ID, createNotification())
    }

    fun initialize(vm: BleViewModel) {
        this.bleViewModel = vm

        serviceScope.launch {
            combine(vm.latestStatus, vm.connectionState, vm.sessionStats) { status, state, stats ->
                Triple(status, state, stats)
            }.collect { (status, state, stats) ->
                updateNotification(status, state, stats)
            }
        }
    }

    private fun createNotification(
        statusText: String = "Monitoring device...",
        contentText: String = "Background tracking active",
        actions: List<NotificationCompat.Action> = emptyList()
    ): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NotificationChannels.STATUS)
            .setContentTitle(statusText)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { actions.forEach { addAction(it) } }
            .build()
    }

    private fun buildActionIntent(action: String, targetTemp: Int = 0, heaterMode: Int = 0): PendingIntent {
        val reqCode = when (action) {
            NotificationActionReceiver.ACTION_TEMP_UP   -> NotificationActionReceiver.REQ_TEMP_UP
            NotificationActionReceiver.ACTION_TEMP_DOWN -> NotificationActionReceiver.REQ_TEMP_DOWN
            NotificationActionReceiver.ACTION_HEATER_ON -> NotificationActionReceiver.REQ_HEATER_ON
            else                                        -> NotificationActionReceiver.REQ_HEATER_OFF
        }
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationActionReceiver.EXTRA_TARGET_TEMP, targetTemp)
            putExtra(NotificationActionReceiver.EXTRA_HEATER_MODE, heaterMode)
        }
        return PendingIntent.getBroadcast(
            this, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateNotification(
        status: com.sbtracker.data.DeviceStatus?,
        state: BleManager.ConnectionState,
        stats: SessionTracker.SessionStats
    ) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        var notifActions: List<NotificationCompat.Action> = emptyList()

        val (title, content) = when {
            state is BleManager.ConnectionState.Connected && status != null -> {
                if (status.heaterMode > 0) {
                    // Session active: add ▲ Temp / ▼ Temp / Stop Heater actions
                    notifActions = listOf(
                        NotificationCompat.Action(0, "\u25b2 Temp",
                            buildActionIntent(NotificationActionReceiver.ACTION_TEMP_UP, status.targetTempC, status.heaterMode)),
                        NotificationCompat.Action(0, "\u25bc Temp",
                            buildActionIntent(NotificationActionReceiver.ACTION_TEMP_DOWN, status.targetTempC, status.heaterMode)),
                        NotificationCompat.Action(0, "Stop Heater",
                            buildActionIntent(NotificationActionReceiver.ACTION_HEATER_OFF))
                    )
                    // Session active: "Session Active — 185°C → 195°C"
                    val titleStr = "Session Active \u2014 ${status.currentTempC}\u00b0C \u2192 ${status.targetTempC}\u00b0C"
                    // Content: "Battery: 72% • Hit #4 • 3m 12s"
                    val elapsedSecs = stats.durationSeconds
                    val minutes = elapsedSecs / 60
                    val seconds = elapsedSecs % 60
                    val elapsedStr = "${minutes}m ${seconds}s"
                    val hitStr = if (stats.hitCount > 0) "Hit #${stats.hitCount}" else null
                    val parts = mutableListOf("Battery: ${status.batteryLevel}%")
                    if (hitStr != null) parts.add(hitStr)
                    parts.add(elapsedStr)
                    titleStr to parts.joinToString(" \u2022 ")
                } else if (status.isCharging) {
                    // Charging: no action buttons (heater-off is implied by device charging)
                    val eta = stats.chargeEtaMinutes?.let { " \u2022 ETA ~${it}m" } ?: ""
                    "Charging" to "Battery: ${status.batteryLevel}%$eta"
                } else {
                    // Idle: add Start Heater action
                    notifActions = listOf(
                        NotificationCompat.Action(0, "Start Heater",
                            buildActionIntent(NotificationActionReceiver.ACTION_HEATER_ON, status.targetTempC))
                    )
                    "Device Online" to "Battery: ${status.batteryLevel}% \u2022 Idle"
                }
            }
            state is BleManager.ConnectionState.Connecting -> {
                "Linking..." to "Background tracking active"
            }
            state is BleManager.ConnectionState.Scanning -> {
                "Scanning..." to "Background tracking active"
            }
            state is BleManager.ConnectionState.Reconnecting -> {
                val titleStr = if (state.attempt == 0) "Waiting for device..." else "Reconnecting (${state.attempt})..."
                titleStr to "Background tracking active"
            }
            else -> {
                // Disconnected
                "Disconnected" to "Tap to reconnect"
            }
        }

        manager.notify(NOTIFICATION_ID, createNotification(title, content, notifActions))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bleManager.connectionState.value is BleManager.ConnectionState.Disconnected) {
            val prefs = getSharedPreferences("known_devices_v1", Context.MODE_PRIVATE)
            val lastSerial = prefs.getString("last_serial", null)
            if (lastSerial != null) {
                val address = prefs.getString("dev_${lastSerial}_addr", null)
                if (!address.isNullOrBlank()) bleManager.reconnectToAddress(address)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
