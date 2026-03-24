package com.sbtracker

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel

/**
 * Owns device write commands: heater control, temperature, boost, brightness,
 * and hardware toggle operations. Delegates BLE writes to [BleManager].
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val bleManager: BleManager
) : ViewModel() {

    fun startSession(targetTemp: Int) {
        val clamped = targetTemp.coerceIn(40, 230)
        bleManager.sendWrite(BlePacket.buildStatusWrite(
            BleConstants.WRITE_TEMPERATURE or BleConstants.WRITE_HEATER_STATE,
            tempC = clamped, mode = 1
        ))
    }

    fun setHeater(on: Boolean) = bleManager.sendWrite(BlePacket.buildSetHeater(on))

    fun setHeaterMode(mode: Int, currentTarget: Int) {
        bleManager.sendWrite(BlePacket.buildStatusWrite(
            BleConstants.WRITE_HEATER_STATE or BleConstants.WRITE_TEMPERATURE,
            tempC = currentTarget, mode = mode
        ))
    }

    fun setTemp(tempC: Int, currentMode: Int) {
        val clamped = tempC.coerceIn(40, 230)
        val mask = BleConstants.WRITE_TEMPERATURE or BleConstants.WRITE_HEATER_STATE
        bleManager.sendWrite(BlePacket.buildStatusWrite(mask, tempC = clamped, mode = currentMode))
    }

    fun setBoost(offsetC: Int) =
        bleManager.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_BOOST, boostC = offsetC.coerceAtLeast(0)))

    fun setSuperBoost(offsetC: Int) =
        bleManager.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_SUPERBOOST, superBoostC = offsetC.coerceAtLeast(0)))

    fun setAutoShutdown(seconds: Int) =
        bleManager.sendWrite(BlePacket.buildStatusWrite(BleConstants.WRITE_AUTO_SHUTDOWN, shutdownSec = seconds.coerceAtLeast(0)))

    fun toggleVibrationLevel(currentLevel: Int) {
        val next = if (currentLevel > 0) 0 else 1
        bleManager.sendWrite(BlePacket.buildSetVibrationLevel(next))
    }

    fun toggleBoostVisualization(currentViz: Boolean, deviceType: String) {
        bleManager.sendWrite(BlePacket.buildSetBoostVisualization(!currentViz, deviceType))
    }

    fun toggleChargeCurrentOpt(current: Boolean) {
        bleManager.sendWrite(BlePacket.buildSetChargeCurrentOpt(!current))
    }

    fun toggleChargeVoltageLimit(current: Boolean) {
        bleManager.sendWrite(BlePacket.buildSetChargeVoltageLimit(!current))
    }

    fun togglePermanentBle(current: Boolean) {
        bleManager.sendWrite(BlePacket.buildSetPermanentBle(!current))
    }

    fun toggleBoostTimeout(currentTimeout: Int) {
        val next = if (currentTimeout > 0) 0 else 1
        bleManager.sendWrite(BlePacket.buildSetBoostTimeout(next))
    }

    fun setBrightness(level: Int) {
        bleManager.sendWrite(BlePacket.buildSetBrightness(level.coerceIn(1, 9)))
    }

    fun setVibrationLevel(level: Int) =
        bleManager.sendWrite(BlePacket.buildSetVibrationLevel(level.coerceIn(0, 100)))

    fun setBoostTimeout(seconds: Int) =
        bleManager.sendWrite(BlePacket.buildSetBoostTimeout(seconds.coerceIn(0, 255)))
}
