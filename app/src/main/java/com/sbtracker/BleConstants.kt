package com.sbtracker

import java.util.UUID

/**
 * All UUIDs, command bytes, and protocol constants for the S&B BLE protocol.
 */
object BleConstants {

    // ── Service / Characteristic ──────────────────────────────────────────────

    val SERVICE_UUID:        UUID = UUID.fromString("00000000-5354-4f52-5a26-4249434b454c")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("00000001-5354-4f52-5a26-4249434b454c")

    // Crafty/Mighty+ (older or traditional protocol)
    val CRAFTY_SERVICE_1:    UUID = UUID.fromString("00000001-4c45-4b43-4942-265a524f5453")
    val CRAFTY_SERVICE_2:    UUID = UUID.fromString("00000002-4c45-4b43-4942-265a524f5453")
    val CRAFTY_SERVICE_3:    UUID = UUID.fromString("00000003-4c45-4b43-4942-265a524f5453")

    // Volcano Hybrid
    val VOLCANO_STATE_SVC:   UUID = UUID.fromString("10100000-5354-4f52-5a26-4249434b454c")
    val VOLCANO_STATE_CHAR:  UUID = UUID.fromString("10100001-5354-4f52-5a26-4249434b454c")
    val VOLCANO_CTRL_SVC:    UUID = UUID.fromString("10110000-5354-4f52-5a26-4249434b454c")
    val VOLCANO_CTRL_CHAR:   UUID = UUID.fromString("10110001-5354-4f52-5a26-4249434b454c")

    val CCCD_UUID:           UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // ── Command bytes (byte 0) ────────────────────────────────────────────────

    const val CMD_STATUS:               Byte = 0x01
    const val CMD_INITIAL_RESET:        Byte = 0x02
    const val CMD_EXTENDED:             Byte = 0x04
    const val CMD_IDENTITY:             Byte = 0x05
    const val CMD_BRIGHTNESS_VIBRATION: Byte = 0x06
    const val CMD_FIND_DEVICE:          Byte = 0x0D
    const val CMD_INITIAL_STATUS:       Byte = 0x1D
    const val CMD_DEVICE_NOTIFICATION:  Byte = 0x29.toByte()

    // ── Byte sizes ────────────────────────────────────────────────────────────

    const val PACKET_SIZE = 20
    const val BRIGHTNESS_PACKET_SIZE = 7

    // ── CMD_STATUS byte 1 — write field masks ─────────────────────────────────

    /** Byte 4-5: target temperature (tenths of °C, little-endian). Mask bit 1. */
    const val WRITE_TEMPERATURE    = 0x02

    /** Byte 6: boost offset in °C. Mask bit 2. */
    const val WRITE_BOOST          = 0x04

    /** Byte 7: superboost offset in °C. Mask bit 3. */
    const val WRITE_SUPERBOOST     = 0x08

    /** Bytes 9-10: auto-shutdown timer in seconds (little-endian). Mask bit 4. */
    const val WRITE_AUTO_SHUTDOWN  = 0x10

    /** Byte 11: heater mode (0=off, 1=normal, 2=boost, 3=superboost). Mask bit 5. */
    const val WRITE_HEATER_STATE   = 0x20

    /** Settings flags write. Mask bit 7. */
    const val WRITE_SETTINGS_FLAGS = 0x80

    // ── CMD_BRIGHTNESS_VIBRATION byte 1 — write field masks ───────────────────

    const val WRITE_BRIGHTNESS     = 0x01
    const val WRITE_VIBRATION_LEVEL = 0x08
    const val WRITE_BOOST_TIMEOUT  = 0x10

    // ── Byte 14 — settings flags (bit masks) ──────────────────────────────────

    const val FLAG_UNIT_FAHRENHEIT     = 0x01
    const val FLAG_SETPOINT_REACHED    = 0x02
    const val FLAG_CHARGE_CURRENT_OPT  = 0x08
    const val FLAG_CHARGE_VOLTAGE_LIMIT = 0x20
    const val FLAG_VIBRATION_BOOST_VIZ = 0x40

    // ── Byte 16 — settings2 flags ─────────────────────────────────────────────

    const val FLAG2_PERMANENT_BLE = 0x01

    // ── Hit detection ─────────────────────────────────────────────────────────

    /** Temperature dip (°C) that triggers hit detection. Single source of truth. */
    const val TEMP_DIP_THRESHOLD_C = 2

}
