package com.sbtracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Time-series snapshot of a CMD 0x01 (Status) response.
 *
 * Composite index on (deviceAddress, timestampMs) covers every query:
 * range scans (getStatusForRange), latest-N (observeRecentStatus), and all
 * aggregate summary queries (getBatteryAtStart/End, getAvgTemp, etc.).
 */
@Entity(
    tableName = "device_status",
    indices   = [Index(
        name  = "index_device_status_deviceAddress_timestampMs",
        value = ["deviceAddress", "timestampMs"]
    )]
)
data class DeviceStatus(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs:   Long,
    val deviceAddress: String,
    val deviceType:    String,

    // ── Temperatures ──────────────────────────────────────────────────────────
    /** Actual heater temperature (°C). */
    val currentTempC:      Int = 0,
    /** Set-point temperature (°C). */
    val targetTempC:       Int,
    /** Boost delta above target (raw °C, byte 6). */
    val boostOffsetC:      Int,
    /** Superboost delta above target (raw °C, byte 7). */
    val superBoostOffsetC: Int,

    // ── Operating state ───────────────────────────────────────────────────────
    val batteryLevel:     Int,
    val heaterMode:       Int,
    val isCharging:       Boolean,
    val setpointReached:  Boolean,
    val autoShutdownSeconds: Int,

    // ── Settings flags ────────────────────────────────────────────────────────
    val isCelsius:                 Boolean,
    val vibrationEnabled:          Boolean,
    val chargeCurrentOptimization: Boolean,
    val chargeVoltageLimit:        Boolean,
    val permanentBluetooth:        Boolean = false,
    val boostVisualization:        Boolean = false,
    val isSynthetic:               Boolean = false
)

@Dao
interface DeviceStatusDao {
    @Insert
    suspend fun insert(status: DeviceStatus): Long

    @Query("SELECT DISTINCT deviceAddress FROM device_status")
    suspend fun getAllDeviceAddresses(): List<String>

    @Query("SELECT * FROM device_status WHERE deviceAddress = :address ORDER BY timestampMs ASC")
    suspend fun getAllForAddress(address: String): List<DeviceStatus>

    @Query("SELECT * FROM device_status WHERE deviceAddress = :address AND timestampMs >= :startMs AND timestampMs <= :endMs ORDER BY timestampMs ASC")
    suspend fun getStatusForRange(address: String, startMs: Long, endMs: Long): List<DeviceStatus>

    @Query("SELECT * FROM device_status WHERE deviceAddress = :address ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecentStatus(address: String, limit: Int): Flow<List<DeviceStatus>>

    @Query("SELECT * FROM device_status WHERE deviceAddress = :address ORDER BY timestampMs DESC LIMIT 1")
    suspend fun getLatestForAddress(address: String): DeviceStatus?

    @Query("SELECT * FROM device_status WHERE deviceAddress = :address AND timestampMs >= :startMs AND timestampMs <= :endMs ORDER BY timestampMs ASC")
    fun observeStatusForRange(address: String, startMs: Long, endMs: Long): Flow<List<DeviceStatus>>

    // ── Summary computation queries ───────────────────────────────────────────

    /** Battery level at (or just after) [startMs] — the opening reading for a session.
     *  [endMs] upper-bounds the search so readings from a later session are never returned. */
    @Query("SELECT batteryLevel FROM device_status WHERE deviceAddress = :address AND timestampMs >= :startMs AND timestampMs <= :endMs ORDER BY timestampMs ASC LIMIT 1")
    suspend fun getBatteryAtStart(address: String, startMs: Long, endMs: Long): Int?

    /** Battery level at (or just before) [endMs], bounded to the session window. */
    @Query("SELECT batteryLevel FROM device_status WHERE deviceAddress = :address AND timestampMs >= :startMs AND timestampMs <= :endMs ORDER BY timestampMs DESC LIMIT 1")
    suspend fun getBatteryAtEnd(address: String, startMs: Long, endMs: Long): Int?

    /** Average heater temperature (°C) while heater was on, for the given time window. */
    @Query("SELECT AVG(currentTempC) FROM device_status WHERE deviceAddress = :address AND timestampMs BETWEEN :startMs AND :endMs AND heaterMode > 0")
    suspend fun getAvgTempForRange(address: String, startMs: Long, endMs: Long): Float?

    /** Peak heater temperature (°C) across all status rows in the given time window. */
    @Query("SELECT MAX(currentTempC) FROM device_status WHERE deviceAddress = :address AND timestampMs BETWEEN :startMs AND :endMs")
    suspend fun getPeakTempForRange(address: String, startMs: Long, endMs: Long): Int?

    /** Timestamp of the first row where setpointReached = 1 — used to derive heat-up time. */
    @Query("SELECT MIN(timestampMs) FROM device_status WHERE deviceAddress = :address AND timestampMs BETWEEN :startMs AND :endMs AND setpointReached = 1")
    suspend fun getFirstSetpointReachedMs(address: String, startMs: Long, endMs: Long): Long?

    /** Delete all device_status rows for a device (user-initiated history clear). */
    @Query("DELETE FROM device_status WHERE deviceAddress = :address")
    suspend fun clearAll(address: String)

    /** Delete rows older than [thresholdMs] across all devices (data retention pruning). */
    @Query("DELETE FROM device_status WHERE timestampMs < :thresholdMs")
    suspend fun deleteRowsOlderThan(thresholdMs: Long)
}
