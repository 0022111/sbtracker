package com.sbtracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * User-provided metadata for a [Session].
 * 
 * This is stored in a separate table so that if a session is ever deleted 
 * and reconstructed from the raw device_status log, we can attempt to 
 * preserve or re-link user-entered data (e.g., if we match the reconstructed 
 * session by start/end times, we can restore its ID and metadata).
 */
@Entity(tableName = "session_metadata")
data class SessionMetadata(
    @PrimaryKey val sessionId: Long,
    val isCapsule: Boolean = false,
    val capsuleWeightGrams: Float = 0.0f,
    val notes: String? = null
)

@Dao
interface SessionMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: SessionMetadata)

    @Query("SELECT * FROM session_metadata WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getMetadataForSession(sessionId: Long): SessionMetadata?

    @Query("SELECT * FROM session_metadata WHERE sessionId = :sessionId LIMIT 1")
    fun observeMetadataForSession(sessionId: Long): Flow<SessionMetadata?>

    @Query("DELETE FROM session_metadata WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Query("SELECT * FROM session_metadata WHERE sessionId IN (:sessionIds)")
    suspend fun getMetadataForSessions(sessionIds: List<Long>): List<SessionMetadata>
}
