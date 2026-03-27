package com.sbtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Boundary record for a single heater session.
 *
 * Stores only what is needed to locate the session in the device_status log:
 * the device address, optional serial, and the start/end timestamps.
 *
 * All derived stats (hit count, battery drain, temperatures, heat-up time, wear)
 * are computed at query time from the raw device_status, hits, and extended_data
 * tables and returned as a [SessionSummary].  This means improving any algorithm
 * retroactively improves all history without migrations or re-ingestion.
 *
 * Separate indices on deviceAddress and serialNumber because observeSessions
 * uses an OR predicate — a composite index can't satisfy OR; two single-column
 * indices let SQLite use an index merge or pick whichever is more selective.
 */
@Entity(
    tableName = "sessions",
    indices   = [
        Index(name = "index_sessions_deviceAddress", value = ["deviceAddress"]),
        Index(name = "index_sessions_serialNumber",  value = ["serialNumber"])
    ]
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceAddress: String,
    val serialNumber:  String?,
    val startTimeMs:   Long,
    val endTimeMs:     Long
) {
    val durationMs: Long get() = endTimeMs - startTimeMs
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: Session): Long

    @Query("SELECT id FROM sessions WHERE deviceAddress = :address AND startTimeMs = :startMs AND endTimeMs = :endMs LIMIT 1")
    suspend fun getIdForBoundary(address: String, startMs: Long, endMs: Long): Long?

    /**
     * Finds an existing session that starts within a small tolerance of [startMs].
     * Useful for deduplicating live-recorded vs reconstructed sessions which may
     * have slightly different marker arrival times.
     */
    @Query("SELECT id FROM sessions WHERE deviceAddress = :address AND ABS(startTimeMs - :startMs) < 30000 LIMIT 1")
    suspend fun findExistingSessionNear(address: String, startMs: Long): Long?

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun countAll(): Int

    @Delete
    suspend fun delete(session: Session)

    @Query("DELETE FROM sessions WHERE id IN (:sessionIds)")
    suspend fun deleteByIds(sessionIds: List<Long>)

    @Query("DELETE FROM sessions WHERE serialNumber = :serial OR deviceAddress = :address")
    suspend fun clearHistory(serial: String, address: String)

    @Query("SELECT * FROM sessions WHERE serialNumber = :serial OR deviceAddress = :address ORDER BY startTimeMs DESC")
    fun observeSessions(serial: String, address: String): Flow<List<Session>>

    @Query("SELECT * FROM sessions ORDER BY startTimeMs DESC")
    fun observeAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions ORDER BY startTimeMs DESC")
    suspend fun getAllSessionsSync(): List<Session>

    @Query("SELECT * FROM sessions WHERE serialNumber = :serial OR deviceAddress = :address ORDER BY startTimeMs DESC LIMIT :limit")
    suspend fun getRecentSessions(serial: String, address: String, limit: Int): List<Session>

    @Query("SELECT * FROM sessions WHERE deviceAddress = :address AND endTimeMs >= :startMs AND startTimeMs <= :endMs ORDER BY startTimeMs ASC")
    suspend fun getSessionsOverlapping(address: String, startMs: Long, endMs: Long): List<Session>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Session?
}
