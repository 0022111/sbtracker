package com.sbtracker

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import com.sbtracker.BleConstants
import com.sbtracker.BleManager
import com.sbtracker.BlePacket
import dagger.hilt.android.AndroidEntryPoint
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
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                // Cancel the triggering alert notification.
                if (alertNotifId != 0) nm.cancel(alertNotifId)

                // Show an immediate "timer started" notification.
                val startNotif = NotificationCompat.Builder(context, NotificationChannels.CONTROLS)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Timer")
                    .setContentText("Ready in ${durationSeconds}s")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setOnlyAlertOnce(true)
                    .setTimeoutAfter(durationSeconds * 1000L)
                    .build()
                nm.notify(NOTIFICATION_ID_TIMER, startNotif)

                // Schedule AlarmManager to fire ACTION_TIMER_COMPLETE when the countdown ends.
                // AlarmManager is process-lifetime-independent — no background coroutine needed.
                val completionIntent = PendingIntent.getBroadcast(
                    context, REQ_TIMER_COMPLETE,
                    Intent(context, NotificationActionReceiver::class.java).apply {
                        action = ACTION_TIMER_COMPLETE
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + durationSeconds * 1000L,
                    completionIntent
                )
            }
            ACTION_TIMER_COMPLETE -> {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val notif = NotificationCompat.Builder(context, NotificationChannels.ALERTS)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Timer")
                    .setContentText("Time's up!")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
                nm.notify(NOTIFICATION_ID_TIMER, notif)
            }
            ACTION_DISMISS_ALERT -> {
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                if (notifId != 0) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(notifId)
                }
            }
            ACTION_DISCONNECT_CHARGE -> {
                // The device protocol has no BLE charger-disconnect command.
                // This action is a user reminder to unplug — dismiss the notification only.
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
                if (notifId != 0) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(notifId)
                }
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
        const val ACTION_TIMER_COMPLETE    = "com.sbtracker.action.TIMER_COMPLETE"
        const val ACTION_DISMISS_ALERT     = "com.sbtracker.action.DISMISS_ALERT"
        const val ACTION_DISCONNECT_CHARGE = "com.sbtracker.action.DISCONNECT_CHARGE"

        const val EXTRA_TARGET_TEMP     = "extra_target_temp"
        const val EXTRA_HEATER_MODE     = "extra_heater_mode"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        /** SharedPreferences key for configurable timer duration (seconds). Default: 30. */
        const val PREF_TIMER_DURATION_SECONDS = "timer_duration_seconds"

        /** Fixed notification ID for the timer notification (start + completion). */
        const val NOTIFICATION_ID_TIMER = 220

        // Unique request codes per action so PendingIntent extras are refreshed
        const val REQ_TEMP_UP               = 201
        const val REQ_TEMP_DOWN             = 202
        const val REQ_HEATER_ON             = 203
        const val REQ_HEATER_OFF            = 204
        const val REQ_START_TIMER           = 205
        const val REQ_DISMISS_ALERT_TEMP    = 206
        const val REQ_DISMISS_ALERT_SESSION = 207
        const val REQ_DISMISS_ALERT_CHARGE  = 208
        const val REQ_DISCONNECT_CHARGE     = 209
        const val REQ_TIMER_COMPLETE        = 210
    }
}
