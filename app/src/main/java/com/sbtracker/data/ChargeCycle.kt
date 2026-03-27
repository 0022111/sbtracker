package com.sbtracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Aggregated data for a single charging event.
 *
 * Used to analyze battery health over time and to provide more accurate
 * "time to full" estimates by learning the device's actual charging rate.
 *
 * Separate indices on deviceAddress and serialNumber for the same reason
 * as the sessions table: OR predicates require individual indices.
 */
@Entity(
    tableName = "charge_cycles",
    indices   = [
        Index(name = "index_charge_cycles_deviceAddress", value = ["deviceAddress"]),
        Index(name = "index_charge_cycles_serialNumber",  value = ["serialNumber"])
    ]
)
data class ChargeCycle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceAddress: String,
    val serialNumber:  String?,
    val startTimeMs:   Long,
    val endTimeMs:     Long,
    val startBattery:  Int,
    val endBattery:    Int,
    /** Average rate observed during this cycle (% per minute). */
    val avgRatePctPerMin: Float,
    val chargeVoltageLimit: Boolean = false,
    val chargeCurrentOptimization: Boolean = false
) {
    val durationMs: Long get() = endTimeMs - startTimeMs
    val batteryGained: Int get() = (endBattery - startBattery).coerceAtLeast(0)
}

@Dao
interface ChargeCycleDao {
    @Insert
    suspend fun insert(cycle: ChargeCycle): Long

    @Query("SELECT id FROM charge_cycles WHERE deviceAddress = :address AND ABS(startTimeMs - :startMs) < 30000 LIMIT 1")
    suspend fun findExistingCycleNear(address: String, startMs: Long): Long?

    @Query("SELECT * FROM charge_cycles WHERE serialNumber = :serial OR deviceAddress = :address ORDER BY startTimeMs DESC")
    fun observeCycles(serial: String, address: String): Flow<List<ChargeCycle>>

    @Query("SELECT * FROM charge_cycles ORDER BY startTimeMs DESC")
    fun observeAllCycles(): Flow<List<ChargeCycle>>

    @Query("SELECT * FROM charge_cycles WHERE (serialNumber = :serial OR deviceAddress = :address) AND endBattery > startBattery ORDER BY startTimeMs DESC LIMIT :limit")
    suspend fun getRecentCycles(serial: String, address: String, limit: Int): List<ChargeCycle>

    /** Delete all charge cycles for a device (user-initiated history clear). */
    @Query("DELETE FROM charge_cycles WHERE deviceAddress = :address")
    suspend fun clearAll(address: String)
}
