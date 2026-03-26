package com.sbtracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "session_programs")
data class SessionProgram(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name:           String,
    val targetTempC:    Int,
    val boostStepsJson: String = "[]",
    val isDefault:      Boolean = false,
    val stayOnAtEnd:    Boolean = false
)

@Dao
interface SessionProgramDao {
    @Query("SELECT * FROM session_programs ORDER BY isDefault DESC, id ASC")
    fun observeAll(): Flow<List<SessionProgram>>

    @Query("SELECT * FROM session_programs WHERE id = :id")
    suspend fun getById(id: Long): SessionProgram?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(program: SessionProgram): Long

    @Query("DELETE FROM session_programs WHERE id = :id AND isDefault = 0")
    suspend fun deleteUserProgram(id: Long)

    @Query("SELECT COUNT(*) FROM session_programs")
    suspend fun count(): Int
}
