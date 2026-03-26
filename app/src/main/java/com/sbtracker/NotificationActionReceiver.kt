package com.sbtracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sbtracker.BleConstants
import com.sbtracker.BleManager
import com.sbtracker.BlePacket
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles notification action button presses for quick device controls
 * (▲ Temp, ▼ Temp, Start Heater, Stop Heater) without opening the app.
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
        }
    }

    companion object {
        const val ACTION_TEMP_UP   = "com.sbtracker.action.TEMP_UP"
        const val ACTION_TEMP_DOWN = "com.sbtracker.action.TEMP_DOWN"
        const val ACTION_HEATER_ON = "com.sbtracker.action.HEATER_ON"
        const val ACTION_HEATER_OFF = "com.sbtracker.action.HEATER_OFF"

        const val EXTRA_TARGET_TEMP = "extra_target_temp"
        const val EXTRA_HEATER_MODE = "extra_heater_mode"

        // Unique request codes per action so PendingIntent extras are refreshed
        const val REQ_TEMP_UP   = 201
        const val REQ_TEMP_DOWN = 202
        const val REQ_HEATER_ON = 203
        const val REQ_HEATER_OFF = 204
    }
}
