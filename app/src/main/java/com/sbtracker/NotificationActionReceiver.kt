package com.sbtracker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.sbtracker.BleConstants
import com.sbtracker.BleManager
import com.sbtracker.BlePacket
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles notification action button presses for quick device controls
 * (▲ Temp, ▼ Temp, Start Heater, Stop Heater) and alert actions
 * (Start Timer, Dismiss Alert, Disconnect Charge) without opening the app.
 *
 * Actions are defined in [NotificationActionReceiver.Companion].
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var bleManager: BleManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TEMP_UP -> {
                val targetC = intent.getIntExtra(EXTRA_TARGET_TEMP, 185)
                    .plus(5).coerceIn(40, 230)
                val mode = intent.getIntExtra(EXTRA_HEATER_MODE, 1)
                val mask = BleConstants.WRITE_TEMPERATURE or BleConstants.WRITE_HEATER_STATE
                bleManager.sendWrite(BlePacket.buildStatusWrite(mask, tempC = targetC, mode = mode))
            }
            ACTION_TEMP_DOWN -> {
                val targetC = intent.getIntExtra(EXTRA_TARGET_TEMP, 185)
                    .minus(5).coerceIn(40, 230)
                val mode = intent.getIntExtra(EXTRA_HEATER_MODE, 1)
                val mask = BleConstants.WRITE_TEMPERATURE or BleConstants.WRITE_HEATER_STATE
                bleManager.sendWrite(BlePacket.buildStatusWrite(mask, tempC = targetC, mode = mode))
            }
            ACTION_HEATER_ON -> {
                val targetC = intent.getIntExtra(EXTRA_TARGET_TEMP, 185).coerceIn(40, 230)
                val mask = BleConstants.WRITE_TEMPERATURE or BleConstants.WRITE_HEATER_STATE
                bleManager.sendWrite(BlePacket.buildStatusWrite(mask, tempC = targetC, mode = 1))
            }
            ACTION_HEATER_OFF -> {
                bleManager.sendWrite(BlePacket.buildSetHeater(false))
            }
            ACTION_START_TIMER -> {
                val alertNotifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val durationSeconds = prefs.getInt(PREF_TIMER_DURATION_SECONDS, 30)
                // Cancel the alert notification first
                if (alertNotifId != 0) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(alertNotifId)
                }
                // Start countdown notification in a coroutine
                val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                scope.launch {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    try {
                        for (remaining in durationSeconds downTo 0) {
                            val notif = androidx.core.app.NotificationCompat.Builder(context, NotificationChannels.CONTROLS)
                                .setSmallIcon(R.mipmap.ic_launcher)
                                .setContentTitle("Timer")
                                .setContentText(if (remaining > 0) "Ready in ${remaining}s" else "Time's up!")
                                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                                .setOnlyAlertOnce(true)
                                .setOngoing(remaining > 0)
                                .build()
                            nm.notify(NOTIFICATION_ID_TIMER, notif)
                            if (remaining == 0) break
                            delay(1_000L)
                        }
                    } finally {
                        scope.cancel()
                    }
                }
            }
            ACTION_DISMISS_ALERT -> {
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                if (notifId != 0) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(notifId)
                }
            }
            ACTION_DISCONNECT_CHARGE -> {
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                // Dismiss the alert notification
                if (notifId != 0) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(notifId)
                }
                // Send stop-charging command via BLE
                bleManager.sendWrite(BlePacket.buildSetHeater(false))
            }
        }
    }

    companion object {
        const val ACTION_TEMP_UP   = "com.sbtracker.action.TEMP_UP"
        const val ACTION_TEMP_DOWN = "com.sbtracker.action.TEMP_DOWN"
        const val ACTION_HEATER_ON = "com.sbtracker.action.HEATER_ON"
        const val ACTION_HEATER_OFF = "com.sbtracker.action.HEATER_OFF"

        // Alert action buttons (T-074)
        const val ACTION_START_TIMER       = "com.sbtracker.action.START_TIMER"
        const val ACTION_DISMISS_ALERT     = "com.sbtracker.action.DISMISS_ALERT"
        const val ACTION_DISCONNECT_CHARGE = "com.sbtracker.action.DISCONNECT_CHARGE"

        const val EXTRA_TARGET_TEMP     = "extra_target_temp"
        const val EXTRA_HEATER_MODE     = "extra_heater_mode"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        /** SharedPreferences key for configurable timer duration (seconds). Default: 30. */
        const val PREF_TIMER_DURATION_SECONDS = "timer_duration_seconds"

        /** Fixed notification ID for the countdown timer notification. */
        const val NOTIFICATION_ID_TIMER = 202

        // Unique request codes per action so PendingIntent extras are refreshed
        const val REQ_TEMP_UP              = 201
        const val REQ_TEMP_DOWN            = 202
        const val REQ_HEATER_ON            = 203
        const val REQ_HEATER_OFF           = 204
        const val REQ_START_TIMER          = 205
        const val REQ_DISMISS_ALERT_TEMP   = 206
        const val REQ_DISMISS_ALERT_SESSION = 207
        const val REQ_DISMISS_ALERT_CHARGE = 208
        const val REQ_DISCONNECT_CHARGE    = 209
    }
}
