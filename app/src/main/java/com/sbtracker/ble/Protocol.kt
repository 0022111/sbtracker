package com.sbtracker.ble

import com.sbtracker.data.DeviceInfo
import com.sbtracker.data.DeviceStatus
import com.sbtracker.data.ExtendedData
import java.util.UUID

/**
 * Storz & Bickel BLE protocol — Veazy / Venty / Crafty+ / Mighty+.
 *
 * Single service, single characteristic for most operations. Commands are
 * 20-byte packets keyed by byte 0. Status (0x01) streams via notifications;
 * extended (0x04) and identity (0x05) are polled.
 */
object Protocol {

    // Service / characteristic
    val SERVICE_UUID:        UUID = UUID.fromString("00000000-5354-4f52-5a26-4249434b454c")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("00000001-5354-4f52-5a26-4249434b454c")
    val CCCD_UUID:           UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Commands (byte 0)
    const val CMD_STATUS:   Byte = 0x01
    const val CMD_EXTENDED: Byte = 0x04
    const val CMD_IDENTITY: Byte = 0x05

    // CMD_STATUS write masks (byte 1)
    const val WRITE_TEMPERATURE:  Int = 0x02
    const val WRITE_HEATER_STATE: Int = 0x20

    // Settings flags (byte 14)
    const val FLAG_FAHRENHEIT:       Int = 0x01
    const val FLAG_SETPOINT_REACHED: Int = 0x02

    const val PACKET_SIZE = 20

    /** Heater mode: 0=off, 1=normal, 2=boost, 3=superboost. */
    fun isHeaterOn(mode: Int): Boolean = mode > 0

    /** Identify device family from serial-number prefix. */
    fun detectDeviceType(prefix: String): String = when {
        prefix.startsWith("VZ")                        -> "Veazy"
        prefix.startsWith("VY")                        -> "Venty"
        prefix.startsWith("VH")                        -> "Volcano Hybrid"
        prefix.startsWith("Storz", ignoreCase = true)  -> "Crafty+"
        prefix.startsWith("MIGHTY", ignoreCase = true) -> "Mighty+"
        else                                           -> "Unknown"
    }
}

/**
 * Pure packet codec. No side effects, no logging — build bytes, parse bytes.
 * All Android-free so the protocol can be unit-tested and later ported.
 */
object Packet {

    fun request(cmd: Byte): ByteArray =
        ByteArray(Protocol.PACKET_SIZE).also { it[0] = cmd }

    fun setHeater(on: Boolean): ByteArray =
        ByteArray(Protocol.PACKET_SIZE).also {
            it[0]  = Protocol.CMD_STATUS
            it[1]  = Protocol.WRITE_HEATER_STATE.toByte()
            it[11] = if (on) 0x01 else 0x00
        }

    fun setTemperature(tempC: Int): ByteArray =
        ByteArray(Protocol.PACKET_SIZE).also {
            val tenths = tempC * 10
            it[0] = Protocol.CMD_STATUS
            it[1] = Protocol.WRITE_TEMPERATURE.toByte()
            it[4] = (tenths and 0xFF).toByte()
            it[5] = ((tenths ushr 8) and 0xFF).toByte()
        }

    fun parseStatus(bytes: ByteArray, address: String, deviceType: String): DeviceStatus? {
        if (bytes.size < 17 || bytes[0] != Protocol.CMD_STATUS) return null

        // Bytes 2-3: current temp (°C × 10) — Crafty+/Mighty+ broadcast it here;
        // Venty/Veazy pin it to 0 and we synthesize from target + offsets.
        val rawCurrent = u16(bytes, 2)
        val rawTarget  = u16(bytes, 4)

        val parsedCurrent = if (rawCurrent >= 32760) 0 else rawCurrent / 10
        val targetC       = if (rawTarget  >= 32760) 0 else rawTarget  / 10

        val boostC      = bytes[6].toInt() and 0xFF
        val superBoostC = bytes[7].toInt() and 0xFF
        val heaterMode  = bytes[11].toInt() and 0xFF

        val isSynthetic = parsedCurrent == 0
        val currentC    = if (!isSynthetic) parsedCurrent else when (heaterMode) {
            2 -> targetC + boostC
            3 -> targetC + superBoostC
            0 -> 0
            else -> targetC
        }

        val flags = bytes.getOrNull(14)?.toInt()?.and(0xFF) ?: 0
        return DeviceStatus(
            timestampMs         = System.currentTimeMillis(),
            deviceAddress       = address,
            deviceType          = deviceType,
            currentTempC        = currentC,
            targetTempC         = targetC,
            boostOffsetC        = boostC,
            superBoostOffsetC   = superBoostC,
            batteryLevel        = bytes.getOrNull(8)?.toInt()?.and(0xFF) ?: 0,
            heaterMode          = heaterMode,
            isCharging          = (bytes.getOrNull(13)?.toInt() ?: 0) and 0xFF > 0,
            setpointReached     = (flags and Protocol.FLAG_SETPOINT_REACHED) != 0,
            autoShutdownSeconds = u16(bytes, 9),
            isCelsius           = (flags and Protocol.FLAG_FAHRENHEIT) == 0,
            isSynthetic         = isSynthetic,
        )
    }

    fun parseExtended(bytes: ByteArray, address: String): ExtendedData? {
        if (bytes.size < 7 || bytes[0] != Protocol.CMD_EXTENDED) return null
        return ExtendedData(
            deviceAddress              = address,
            lastUpdatedMs              = System.currentTimeMillis(),
            heaterRuntimeMinutes       = u24(bytes, 1),
            batteryChargingTimeMinutes = u24(bytes, 4),
        )
    }

    fun parseIdentity(bytes: ByteArray, address: String): DeviceInfo? {
        if (bytes.size < 19 || bytes[0] != Protocol.CMD_IDENTITY) return null
        val namePart   = bytes.copyOfRange(9,  15).toString(Charsets.UTF_8).trimEnd('\u0000')
        val prefixPart = bytes.copyOfRange(15, 17).toString(Charsets.UTF_8).trimEnd('\u0000')
        val serial     = prefixPart + namePart
        if (serial.isBlank()) return null
        return DeviceInfo(
            deviceAddress = address,
            lastSeenMs    = System.currentTimeMillis(),
            serialNumber  = serial,
            colorIndex    = bytes[18].toInt() and 0xFF,
            deviceType    = Protocol.detectDeviceType(prefixPart),
        )
    }

    private fun u16(b: ByteArray, i: Int): Int =
        ((b.getOrNull(i)?.toInt() ?: 0) and 0xFF) or
        (((b.getOrNull(i + 1)?.toInt() ?: 0) and 0xFF) shl 8)

    private fun u24(b: ByteArray, i: Int): Int =
        ((b.getOrNull(i)?.toInt() ?: 0) and 0xFF) or
        (((b.getOrNull(i + 1)?.toInt() ?: 0) and 0xFF) shl 8) or
        (((b.getOrNull(i + 2)?.toInt() ?: 0) and 0xFF) shl 16)
}
