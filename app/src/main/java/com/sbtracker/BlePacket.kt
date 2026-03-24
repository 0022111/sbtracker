package com.sbtracker

import android.util.Log
import com.sbtracker.data.DeviceInfo
import com.sbtracker.data.DeviceStatus
import com.sbtracker.data.ExtendedData

/**
 * Builds outgoing BLE packets and parses incoming notification payloads.
 */
object BlePacket {

    fun buildRequest(cmd: Byte): ByteArray =
        ByteArray(BleConstants.PACKET_SIZE).also { it[0] = cmd }

    fun buildBrightnessVibrationRequest(): ByteArray =
        ByteArray(BleConstants.BRIGHTNESS_PACKET_SIZE).also { it[0] = BleConstants.CMD_BRIGHTNESS_VIBRATION }

    fun parseStatus(
        bytes: ByteArray,
        address: String,
        deviceType: String = ""
    ): DeviceStatus? {
        if (bytes.size < 17) return null
        if (bytes[0] != BleConstants.CMD_STATUS) return null

        // Bytes 4-5: target temperature (uint16 LE, tenths of °C).
        // Bytes 2-3: current temperature for Crafty+/Mighty+; always 0x0000 for Venty/Veazy
        // (confirmed by the reactive-volcano reference implementation — those devices don't
        //  broadcast a live current temp in the status packet at all).
        val rawCurrent = (bytes[2].toInt() and 0xFF) or ((bytes[3].toInt() and 0xFF) shl 8)
        val rawTarget  = (bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8)

        val parsedCurrentC = if (rawCurrent >= 32760) 0 else rawCurrent / 10
        val targetTempC    = if (rawTarget  >= 32760) 0 else rawTarget  / 10

        val boostOffsetC      = bytes[6].toInt() and 0xFF
        val superBoostOffsetC = bytes[7].toInt() and 0xFF
        val heaterMode        = bytes[11].toInt() and 0xFF

        // For devices that don't report current temp (Venty/Veazy), fall back to the
        // effective target temp so that graphs and hit-temp recording are meaningful.
        val currentTempC = if (parsedCurrentC > 0) {
            parsedCurrentC
        } else {
            when (heaterMode) {
                2    -> targetTempC + boostOffsetC
                3    -> targetTempC + superBoostOffsetC
                else -> if (heaterMode > 0) targetTempC else 0
            }
        }
        val batteryLevel      = bytes[8].toInt() and 0xFF
        val autoShutdownSecs  = (bytes[9].toInt() and 0xFF) or ((bytes[10].toInt() and 0xFF) shl 8)
        val isCharging        = (bytes[13].toInt() and 0xFF) > 0
        val settingsFlags     = bytes[14].toInt() and 0xFF
        val settings2Flags    = if (bytes.size > 16) bytes[16].toInt() and 0xFF else 0

        val isCelsius          = (settingsFlags and BleConstants.FLAG_UNIT_FAHRENHEIT)  == 0
        val setpointReached    = (settingsFlags and BleConstants.FLAG_SETPOINT_REACHED) != 0
        val chargeCurrentOpt   = (settingsFlags and BleConstants.FLAG_CHARGE_CURRENT_OPT) != 0
        val chargeVoltageLimit = (settingsFlags and BleConstants.FLAG_CHARGE_VOLTAGE_LIMIT) != 0
        val permanentBle       = (settings2Flags and BleConstants.FLAG2_PERMANENT_BLE)  != 0

        // Bit 0x40 = shared vibration/boost-visualization flag.
        // Per the reference implementation, Veazy inverts the meaning of this bit for BOTH fields.
        // Venty does NOT invert — only Veazy has the hardware inversion.
        val rawBit         = (settingsFlags and BleConstants.FLAG_VIBRATION_BOOST_VIZ) != 0
        val vibrationEnabled   = if (deviceType == "Veazy") !rawBit else rawBit
        val boostVisualization = if (deviceType == "Veazy") !rawBit else rawBit

        return DeviceStatus(
            timestampMs            = System.currentTimeMillis(),
            deviceAddress          = address,
            deviceType             = deviceType,
            currentTempC           = currentTempC,
            targetTempC            = targetTempC,
            boostOffsetC           = boostOffsetC,
            superBoostOffsetC      = superBoostOffsetC,
            batteryLevel           = batteryLevel,
            heaterMode             = heaterMode,
            isCharging             = isCharging,
            setpointReached        = setpointReached,
            autoShutdownSeconds    = autoShutdownSecs,
            isCelsius              = isCelsius,
            vibrationEnabled       = vibrationEnabled,
            chargeCurrentOptimization = chargeCurrentOpt,
            chargeVoltageLimit     = chargeVoltageLimit,
            permanentBluetooth     = permanentBle,
            boostVisualization     = boostVisualization
        )
    }

    fun buildStatusWrite(
        mask: Int,
        tempC: Int = 0,
        mode: Int = 0,
        boostC: Int = 0,
        superBoostC: Int = 0,
        shutdownSec: Int = 0
    ): ByteArray = ByteArray(BleConstants.PACKET_SIZE).also {
        it[0] = BleConstants.CMD_STATUS
        it[1] = (mask and 0xFF).toByte()
        
        if ((mask and BleConstants.WRITE_TEMPERATURE) != 0) {
            val tenths = tempC * 10
            it[4] = (tenths and 0xFF).toByte()
            it[5] = ((tenths ushr 8) and 0xFF).toByte()
        }
        if ((mask and BleConstants.WRITE_HEATER_STATE) != 0) {
            it[11] = (mode and 0xFF).toByte()
        }
        if ((mask and BleConstants.WRITE_BOOST) != 0) {
            it[6] = (boostC and 0xFF).toByte()
        }
        if ((mask and BleConstants.WRITE_SUPERBOOST) != 0) {
            it[7] = (superBoostC and 0xFF).toByte()
        }
        if ((mask and BleConstants.WRITE_AUTO_SHUTDOWN) != 0) {
            it[9]  = (shutdownSec and 0xFF).toByte()
            it[10] = ((shutdownSec ushr 8) and 0xFF).toByte()
        }
    }

    fun parseFirmware(bytes: ByteArray): String? {
        if (bytes.size < 5) return null
        if (bytes[0] != BleConstants.CMD_INITIAL_RESET) return null
        val fw = "${bytes[1].toInt() and 0xFF}.${bytes[2].toInt() and 0xFF}.${bytes[3].toInt() and 0xFF}.${bytes[4].toInt() and 0xFF}"
        // Bytes 5-8 are bootloader version (if present)
        val bl = if (bytes.size >= 9)
            "${bytes[5].toInt() and 0xFF}.${bytes[6].toInt() and 0xFF}.${bytes[7].toInt() and 0xFF}.${bytes[8].toInt() and 0xFF}"
        else null
        return if (bl != null) "$fw / BL $bl" else fw
    }

    fun parseExtended(bytes: ByteArray, address: String): ExtendedData? {
        if (bytes.size < 7) return null
        if (bytes[0] != BleConstants.CMD_EXTENDED) return null

        val heaterRuntime = (bytes[1].toInt() and 0xFF) or
                            ((bytes[2].toInt() and 0xFF) shl 8) or
                            ((bytes[3].toInt() and 0xFF) shl 16)
        val chargingTime  = (bytes[4].toInt() and 0xFF) or
                            ((bytes[5].toInt() and 0xFF) shl 8) or
                            ((bytes[6].toInt() and 0xFF) shl 16)

        return ExtendedData(
            deviceAddress              = address,
            lastUpdatedMs              = System.currentTimeMillis(),
            heaterRuntimeMinutes       = heaterRuntime,
            batteryChargingTimeMinutes = chargingTime
        )
    }

    fun parseIdentity(bytes: ByteArray, address: String): DeviceInfo? {
        if (bytes.size < 19) return null
        if (bytes[0] != BleConstants.CMD_IDENTITY) return null

        val namePart   = bytes.copyOfRange(9,  15).toString(Charsets.UTF_8).trimEnd('\u0000')
        val prefixPart = bytes.copyOfRange(15, 17).toString(Charsets.UTF_8).trimEnd('\u0000')
        val colorIndex = bytes[18].toInt() and 0xFF
        val serial     = prefixPart + namePart

        return DeviceInfo(
            deviceAddress = address,
            lastSeenMs    = System.currentTimeMillis(),
            serialNumber  = serial,
            colorIndex    = colorIndex,
            deviceType    = detectDeviceType(prefixPart)
        )
    }

    fun parseDisplaySettings(bytes: ByteArray): DisplaySettings? {
        if (bytes.size < 7) return null
        if (bytes[0] != BleConstants.CMD_BRIGHTNESS_VIBRATION) return null
        return DisplaySettings(
            brightness    = (bytes[2].toInt() and 0xFF).coerceIn(1, 9),
            vibrationLevel = bytes[5].toInt() and 0xFF,
            boostTimeout  = bytes[6].toInt() and 0xFF
        )
    }

    fun buildSetHeater(on: Boolean): ByteArray =
        buildStatusWrite(BleConstants.WRITE_HEATER_STATE, mode = if (on) 0x01 else 0x00)

    private fun buildSetSettingsFlag(value: Int, bitMask: Int): ByteArray =
        ByteArray(BleConstants.PACKET_SIZE).also {
            it[0]  = BleConstants.CMD_STATUS
            it[1]  = BleConstants.WRITE_SETTINGS_FLAGS.toByte()
            it[14] = (value   and 0xFF).toByte()
            it[15] = (bitMask and 0xFF).toByte()
        }

    fun buildSetUnit(fahrenheit: Boolean): ByteArray =
        buildSetSettingsFlag(
            value   = if (fahrenheit) BleConstants.FLAG_UNIT_FAHRENHEIT else 0,
            bitMask = BleConstants.FLAG_UNIT_FAHRENHEIT
        )

    fun buildSetBoostVisualization(enabled: Boolean, deviceType: String): ByteArray {
        // Only Veazy inverts the boost-visualization bit; Venty uses it straight.
        val rawBit = if (deviceType == "Veazy") !enabled else enabled
        return buildSetSettingsFlag(
            value   = if (rawBit) BleConstants.FLAG_VIBRATION_BOOST_VIZ else 0,
            bitMask = BleConstants.FLAG_VIBRATION_BOOST_VIZ
        )
    }

    fun buildSetChargeCurrentOpt(enabled: Boolean): ByteArray =
        buildSetSettingsFlag(
            value   = if (enabled) BleConstants.FLAG_CHARGE_CURRENT_OPT else 0,
            bitMask = BleConstants.FLAG_CHARGE_CURRENT_OPT
        )

    fun buildSetChargeVoltageLimit(enabled: Boolean): ByteArray =
        buildSetSettingsFlag(
            value   = if (enabled) BleConstants.FLAG_CHARGE_VOLTAGE_LIMIT else 0,
            bitMask = BleConstants.FLAG_CHARGE_VOLTAGE_LIMIT
        )

    fun buildSetPermanentBle(enabled: Boolean): ByteArray =
        ByteArray(BleConstants.PACKET_SIZE).also {
            it[0]  = BleConstants.CMD_STATUS
            it[1]  = BleConstants.WRITE_SETTINGS_FLAGS.toByte()
            it[16] = if (enabled) BleConstants.FLAG2_PERMANENT_BLE.toByte() else 0x00
            it[17] = BleConstants.FLAG2_PERMANENT_BLE.toByte()  // bit mask
        }

    fun buildSetBrightness(level: Int): ByteArray =
        ByteArray(BleConstants.BRIGHTNESS_PACKET_SIZE).also {
            it[0] = BleConstants.CMD_BRIGHTNESS_VIBRATION
            it[1] = BleConstants.WRITE_BRIGHTNESS.toByte()
            it[2] = level.coerceIn(1, 9).toByte()
        }

    fun buildSetVibrationLevel(level: Int): ByteArray =
        ByteArray(BleConstants.BRIGHTNESS_PACKET_SIZE).also {
            it[0] = BleConstants.CMD_BRIGHTNESS_VIBRATION
            it[1] = BleConstants.WRITE_VIBRATION_LEVEL.toByte()
            it[5] = (level and 0xFF).toByte()
        }

    fun buildSetBoostTimeout(seconds: Int): ByteArray =
        ByteArray(BleConstants.BRIGHTNESS_PACKET_SIZE).also {
            it[0] = BleConstants.CMD_BRIGHTNESS_VIBRATION
            it[1] = BleConstants.WRITE_BOOST_TIMEOUT.toByte()
            it[6] = (seconds and 0xFF).toByte()
        }

    fun buildFindDevice(): ByteArray =
        ByteArray(BleConstants.PACKET_SIZE).also {
            it[0] = BleConstants.CMD_FIND_DEVICE
            it[1] = 0x01
        }

    fun buildFactoryReset(): ByteArray =
        ByteArray(BleConstants.PACKET_SIZE).also {
            it[0]  = BleConstants.CMD_STATUS
            it[1]  = (BleConstants.WRITE_SETTINGS_FLAGS and 0xFF).toByte()
            it[14] = 0x04  // BIT_SETTINGS_FACTORY_RESET
            it[15] = 0x04  // bit mask
        }

    fun detectDeviceType(prefix: String): String = when {
        prefix.startsWith("VZ")                            -> "Veazy"
        prefix.startsWith("VY")                            -> "Venty"
        prefix.startsWith("Storz",  ignoreCase = true)    -> "Crafty+"
        prefix.startsWith("MIGHTY", ignoreCase = true)    -> "Mighty+"
        prefix.startsWith("S\u0026B MIGHTY", ignoreCase = true) -> "Mighty+"
        else                                               -> "Unknown"
    }
}

data class DisplaySettings(
    val brightness:     Int,
    val vibrationLevel: Int,
    val boostTimeout:   Int
)
