package com.sbtracker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// ── Entities ────────────────────────────────────────────────────────────────

/**
 * The god log. Every BLE status poll becomes one row.
 * Every derived value (sessions, hits, analytics) is computed from this table.
 */
@Entity(
    tableName = "device_status",
    indices   = [Index(value = ["deviceAddress", "timestampMs"])]
)
data class DeviceStatus(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs:         Long,
    val deviceAddress:       String,
    val deviceType:          String,
    val currentTempC:        Int,
    val targetTempC:         Int,
    val boostOffsetC:        Int,
    val superBoostOffsetC:   Int,
    val batteryLevel:        Int,
    val heaterMode:          Int,
    val isCharging:          Boolean,
    val setpointReached:     Boolean,
    val autoShutdownSeconds: Int,
    val isCelsius:           Boolean,
    val isSynthetic:         Boolean,
)

/** Identity — one row per device, keyed by MAC address. Upserted on each read. */
@Entity(tableName = "device_info")
data class DeviceInfo(
    @PrimaryKey val deviceAddress: String,
    val lastSeenMs:  Long,
    val serialNumber: String,
    val colorIndex:  Int,
    val deviceType:  String,
)

/** Lifetime counters from CMD 0x04. Upserted. */
@Entity(tableName = "extended_data")
data class ExtendedData(
    @PrimaryKey val deviceAddress: String,
    val lastUpdatedMs:              Long,
    val heaterRuntimeMinutes:       Int,
    val batteryChargingTimeMinutes: Int,
)

/**
 * Session boundary: the only derived record we persist, and only as an index
 * back into [DeviceStatus]. All session stats are computed from the raw log.
 */
@Entity(
    tableName = "sessions",
    indices   = [Index(value = ["deviceAddress", "startTimeMs"])]
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceAddress: String,
    val serialNumber:  String?,
    val startTimeMs:   Long,
    val endTimeMs:     Long,
)

// ── DAOs ────────────────────────────────────────────────────────────────────

@Dao
interface DeviceStatusDao {
    @Insert
    suspend fun insert(status: DeviceStatus): Long

    @Query("SELECT * FROM device_status WHERE deviceAddress = :address ORDER BY timestampMs ASC")
    suspend fun getAll(address: String): List<DeviceStatus>

    @Query("SELECT * FROM device_status WHERE deviceAddress = :address AND timestampMs BETWEEN :start AND :end ORDER BY timestampMs ASC")
    suspend fun getRange(address: String, start: Long, end: Long): List<DeviceStatus>

    @Query("SELECT * FROM device_status WHERE deviceAddress = :address ORDER BY timestampMs DESC LIMIT 1")
    fun observeLatest(address: String): Flow<DeviceStatus?>

    @Query("SELECT DISTINCT deviceAddress FROM device_status")
    suspend fun getAllAddresses(): List<String>

    @Query("DELETE FROM device_status WHERE deviceAddress = :address")
    suspend fun clear(address: String)
}

@Dao
interface DeviceInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(info: DeviceInfo)

    @Query("SELECT * FROM device_info WHERE deviceAddress = :address LIMIT 1")
    suspend fun get(address: String): DeviceInfo?

    @Query("SELECT * FROM device_info ORDER BY lastSeenMs DESC")
    fun observeAll(): Flow<List<DeviceInfo>>
}

@Dao
interface ExtendedDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(data: ExtendedData)

    @Query("SELECT * FROM extended_data WHERE deviceAddress = :address LIMIT 1")
    fun observe(address: String): Flow<ExtendedData?>
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: Session): Long

    @Query("SELECT * FROM sessions WHERE deviceAddress = :address ORDER BY startTimeMs DESC")
    fun observe(address: String): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE deviceAddress = :address AND ABS(startTimeMs - :start) < 30000 LIMIT 1")
    suspend fun findNear(address: String, start: Long): Session?

    @Query("DELETE FROM sessions WHERE deviceAddress = :address")
    suspend fun clear(address: String)
}

// ── Database ────────────────────────────────────────────────────────────────

@Database(
    entities     = [DeviceStatus::class, DeviceInfo::class, ExtendedData::class, Session::class],
    version      = 1,
    exportSchema = true,
)
abstract class Db : RoomDatabase() {
    abstract fun status():   DeviceStatusDao
    abstract fun info():     DeviceInfoDao
    abstract fun extended(): ExtendedDataDao
    abstract fun sessions(): SessionDao

    companion object {
        @Volatile private var instance: Db? = null

        fun get(context: Context): Db = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, Db::class.java, "sbtracker.db"
            ).build().also { instance = it }
        }
    }
}
