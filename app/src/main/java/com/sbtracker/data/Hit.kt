package com.sbtracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Individual breath/hit record belonging to a session.
 *
 * Previously encoded as a CSV string in Session.hitDetails
 * ("startMs:durSec:peakTempC,...").  Storing hits as first-class rows means:
 *  - hit detection logic can be re-run against the raw device_status log
 *    and old hit data can be replaced via a migration
 *  - new per-hit fields (e.g. avg temp, airflow proxy) can be added without
 *    touching the sessions table
 *  - queries across hits are possible (e.g. longest hit, coolest hit)
 *
 * Rows are inserted immediately after the parent Session row is committed.
 * sessionId is the Room-generated primary key of the Session row.
 */
@Entity(
    tableName = "hits",
    indices = [
        Index("sessionId"),
        Index("deviceAddress")
    ]
)
data class Hit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** ID of the parent Session row. */
    val sessionId: Long,

    /** BLE MAC address — redundant with sessionId but makes address-scoped queries fast. */
    val deviceAddress: String,

    /** Absolute epoch time when this hit started, in ms. */
    val startTimeMs: Long,

    /** Duration of this hit in ms. */
    val durationMs: Long,

    /** Peak heater temperature recorded during this hit, in °C. */
    val peakTempC: Int
)

/** Aggregate stats for a session's hits — returned by a single DAO query. */
data class HitStats(
    val hitCount:       Int,
    val totalDurationMs: Long
)

@Dao
interface HitDao {

    @Insert
    suspend fun insert(hit: Hit): Long

    @Insert
    suspend fun insertAll(hits: List<Hit>)

    @Query("SELECT * FROM hits WHERE sessionId = :sessionId ORDER BY startTimeMs ASC")
    suspend fun getHitsForSession(sessionId: Long): List<Hit>

    /** Hit count and total duration for a session — single round-trip to the DB. */
    @Query("SELECT COUNT(*) as hitCount, COALESCE(SUM(durationMs), 0) as totalDurationMs FROM hits WHERE sessionId = :sessionId")
    suspend fun getHitStatsForSession(sessionId: Long): HitStats

    /** Delete all hits for a specific session (e.g. when that session is deleted). */
    @Query("DELETE FROM hits WHERE sessionId = :sessionId")
    suspend fun deleteHitsForSession(sessionId: Long)

    /** Delete all hits for a device (user-initiated history clear). */
    @Query("DELETE FROM hits WHERE deviceAddress = :address")
    suspend fun clearAll(address: String)
}
