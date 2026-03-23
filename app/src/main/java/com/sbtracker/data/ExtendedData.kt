package com.sbtracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert


/**
 * Latest snapshot of a CMD 0x04 (Extended Data) response.
 *
 * One row per physical device, keyed by BLE MAC address.  Upserted on every
 * extended-data poll (~30 s) so only the most recent counters are stored.
 *
 * Both counters are device lifetime totals expressed in minutes; they are
 * stored raw and converted for display only.  Because they are monotonically
 * increasing device-side counters, only the latest value is meaningful.
 */
@Entity(tableName = "extended_data")
data class ExtendedData(
    /** BLE MAC address — natural key, one row per physical device. */
    @PrimaryKey val deviceAddress: String,

    /** Epoch ms when this row was last refreshed from a BLE poll. */
    val lastUpdatedMs: Long,

    /** Cumulative heater-on time in minutes (bytes 1-3 little-endian). */
    val heaterRuntimeMinutes: Int,

    /** Cumulative battery-charging time in minutes (bytes 4-6 little-endian). */
    val batteryChargingTimeMinutes: Int
)

@Dao
interface ExtendedDataDao {

    @Upsert
    suspend fun upsert(data: ExtendedData)

    /** Latest row for a device — direct lookup by primary key. */
    @Query("SELECT * FROM extended_data WHERE deviceAddress = :address LIMIT 1")
    suspend fun getByAddress(address: String): ExtendedData?

    /**
     * Best-available heater runtime reading at or before [beforeMs].
     *
     * Since we now store only the latest value, this returns the current
     * heaterRuntimeMinutes if the device matches.  For session-bracketing
     * purposes the caller should snapshot the value at session start/end
     * from the in-memory [ExtendedData] object rather than relying on a
     * historical DB lookup.
     */
    @Query("SELECT heaterRuntimeMinutes FROM extended_data WHERE deviceAddress = :address LIMIT 1")
    suspend fun getHeaterRuntime(address: String): Int?

    /** Delete all extended_data for a device (user-initiated history clear). */
    @Query("DELETE FROM extended_data WHERE deviceAddress = :address")
    suspend fun clearAll(address: String)
}
