package com.sbtracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service to keep the BLE connection alive and handle background tasks
 * like charging ETA notifications and session tracking.
 */
class BleService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private var viewModel: MainViewModel? = null

    private val CHANNEL_ID = "ble_service_channel"
    private val NOTIFICATION_ID = 101

    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    fun initialize(vm: MainViewModel) {
        this.viewModel = vm
        
        serviceScope.launch {
            combine(vm.latestStatus, vm.connectionState) { status, state ->
                status to state
            }.collect { (status, state) ->
                updateNotification(status, state)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "SB Tracker Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(
        statusText: String = "Monitoring device...",
        contentText: String = "Background tracking active"
    ): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(statusText)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: com.sbtracker.data.DeviceStatus?, state: BleManager.ConnectionState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val title = when (state) {
            is BleManager.ConnectionState.Connected -> "Device Online"
            is BleManager.ConnectionState.Connecting -> "Linking..."
            is BleManager.ConnectionState.Scanning -> "Scanning..."
            is BleManager.ConnectionState.Reconnecting -> "Reconnecting (${state.attempt})..."
            else -> "Disconnected"
        }

        val content = if (status != null && state is BleManager.ConnectionState.Connected) {
            val heaterText = if (status.heaterMode > 0) "${status.currentTempC}°C" else "OFF"
            val batteryText = "Batt: ${status.batteryLevel}%"
            "$heaterText • $batteryText"
        } else {
            "Background tracking active"
        }

        manager.notify(NOTIFICATION_ID, createNotification(title, content))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
