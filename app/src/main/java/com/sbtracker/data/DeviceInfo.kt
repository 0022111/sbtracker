package com.sbtracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Identity snapshot of a CMD 0x05 (Device Identity) response.
 *
 * One row per physical device, keyed by BLE MAC address.  Upserted on every
 * identity poll so the latest values are always available without growing the
 * table.  The values rarely change (serial, colour, type are baked into
 * firmware), but if a future firmware update *does* change something, the
 * upsert will capture it automatically.
 *
 * Serial construction: bytes 15-16 (prefix) + bytes 9-14 (name), both UTF-8,
 * null-terminated.  Example: prefix="VZ" + name="ABC123" → serial="VZABC123".
 */
@Entity(tableName = "device_info")
data class DeviceInfo(
    /** BLE MAC address — natural key, one row per physical device. */
    @PrimaryKey val deviceAddress: String,

    /** Epoch ms when this row was last refreshed from a BLE poll. */
    val lastSeenMs: Long,

    /** Full serial number: prefix + name part concatenated. */
    val serialNumber: String,

    /** Raw colour index (byte 18); mapping to colour names is device-specific. */
    val colorIndex: Int,

    /** Inferred device type: "Veazy", "Venty", "Crafty+", "Mighty+", or "Unknown". */
    val deviceType: String
)

@Dao
interface DeviceInfoDao {

    @Upsert
    suspend fun upsert(info: DeviceInfo)

    /** Latest identity row for a device — direct lookup by primary key. */
    @Query("SELECT * FROM device_info WHERE deviceAddress = :address LIMIT 1")
    suspend fun getByAddress(address: String): DeviceInfo?

    /** All known devices, most recently seen first. */
    @Query("SELECT * FROM device_info ORDER BY lastSeenMs DESC")
    suspend fun getAll(): List<DeviceInfo>

    /** Delete all device_info for a device (user-initiated history clear). */
    @Query("DELETE FROM device_info WHERE deviceAddress = :address")
    suspend fun clearAll(address: String)
}
