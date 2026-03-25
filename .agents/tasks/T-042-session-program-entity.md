# T-042 — SessionProgram Entity, DAO, and DB Migration 3→4

**Phase**: Phase 3 — F-027 Session Programs/Presets
**Blocked by**: nothing
**Estimated diff**: ~100 lines across 4 files

## Goal
Define the `SessionProgram` Room entity with its DAO, wire it into the database, AND add
`appliedProgramId` to `session_metadata` — all in a single Migration 3→4. The two changes
are bundled here so the schema version only bumps once.

## Read these files first
- `app/src/main/java/com/sbtracker/data/AppDatabase.kt` — current version (3), entity list, note that migrations live in `di/AppModule.kt`
- `app/src/main/java//com/sbtracker/data/SessionMetadata.kt` — the entity you will extend
- `app/src/main/java/com/sbtracker/di/AppModule.kt` — where `MIGRATION_2_3` lives; pattern to follow for MIGRATION_3_4

## Change only these files
- `app/src/main/java/com/sbtracker/data/SessionProgram.kt` *(create new)*
- `app/src/main/java/com/sbtracker/data/SessionMetadata.kt`
- `app/src/main/java/com/sbtracker/data/AppDatabase.kt`
- `app/src/main/java/com/sbtracker/di/AppModule.kt`

## Steps

### 1. Create `SessionProgram.kt`

```kotlin
package com.sbtracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * A user-defined or built-in session program (temperature preset + boost schedule).
 * boostStepsJson stores a JSON array of BoostStep: [{"offsetSec": 60, "boostC": 5}, ...]
 * boostC in each step is the OFFSET applied via setBoost(), not an absolute temperature.
 */
@Entity(tableName = "session_programs")
data class SessionProgram(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name:           String,
    val targetTempC:    Int,
    val boostStepsJson: String = "[]", // JSON array of timed boost steps
    val isDefault:      Boolean = false // true = built-in preset, cannot be deleted
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
```

> **Note**: The old `boostOffsetC: Int` top-level field is removed. A single base boost is
> better expressed as a step at offsetSec=0. This keeps the schema flat — all boost
> behaviour lives in `boostStepsJson`.

### 2. Extend `SessionMetadata.kt`

Add a nullable `appliedProgramId` field. This records which program (if any) was active
when the session was started. It survives `backcompileSessionsFromLogs()` because
`session_metadata` is user-sourced, never rebuilt.

```kotlin
@Entity(tableName = "session_metadata")
data class SessionMetadata(
    @PrimaryKey val sessionId: Long,
    val isCapsule: Boolean = false,
    val capsuleWeightGrams: Float = 0.0f,
    val notes: String? = null,
    val appliedProgramId: Long? = null   // ← new field
)
```

Also update `SessionMetadataDao` — add one query to fetch by program:
```kotlin
@Query("SELECT * FROM session_metadata WHERE appliedProgramId = :programId")
suspend fun getSessionsForProgram(programId: Long): List<SessionMetadata>
```

### 3. Update `AppDatabase.kt`

- Add `SessionProgram::class` to the `entities` list
- Bump `version` from `3` to `4`
- Add `abstract fun sessionProgramDao(): SessionProgramDao`

### 4. Add `MIGRATION_3_4` to `AppModule.kt`

Inside `provideDatabase()`, add the following migration constant and register it:

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create session_programs table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS session_programs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                targetTempC INTEGER NOT NULL,
                boostStepsJson TEXT NOT NULL DEFAULT '[]',
                isDefault INTEGER NOT NULL DEFAULT 0
            )
        """)
        // Add appliedProgramId column to session_metadata
        db.execSQL(
            "ALTER TABLE session_metadata ADD COLUMN appliedProgramId INTEGER"
        )
    }
}
```

Then add it to `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`.

Also add a DAO provider:
```kotlin
@Provides
fun provideSessionProgramDao(db: AppDatabase): SessionProgramDao = db.sessionProgramDao()
```

### 5. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `session_programs` table created by Migration(3, 4) with correct columns (no `boostOffsetC` top-level column)
- [ ] `session_metadata` gains `appliedProgramId INTEGER` (nullable) column
- [ ] `SessionProgramDao` compiles with all five methods
- [ ] `SessionMetadataDao` has new `getSessionsForProgram()` query
- [ ] `AppDatabase.version` is `4` and `SessionProgram::class` is in entity list
- [ ] `SessionProgramDao` is provided via `AppModule`
- [ ] `./gradlew assembleDebug` passes with no errors

## Do NOT
- Seed default programs here — that is T-043's job
- Change any other existing table or migration (1→2, 2→3)
- Add a top-level `boostOffsetC` field to `SessionProgram` — use boostStepsJson exclusively
- Touch UI files
