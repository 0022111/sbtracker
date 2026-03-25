# T-042 — SessionProgram Entity, DAO, and DB Migration 3→4

**Phase**: Phase 3 — F-027 Session Programs/Presets
**Blocked by**: nothing
**Estimated diff**: ~80 lines across 3 files

## Goal
Define the `SessionProgram` Room entity with its DAO and wire it into the database, bumping the schema from v3 to v4.

## Read these files first
- `app/src/main/java/com/sbtracker/data/AppDatabase.kt` — current schema version (v3), entity list, migration builder pattern
- `app/src/main/java/com/sbtracker/data/SessionMetadata.kt` — reference for entity + DAO pattern in this project

## Change only these files
- `app/src/main/java/com/sbtracker/data/SessionProgram.kt` *(create new)*
- `app/src/main/java/com/sbtracker/data/AppDatabase.kt`

## Steps

1. **Create `SessionProgram.kt`**:
   ```kotlin
   package com.sbtracker.data

   import androidx.room.*
   import kotlinx.coroutines.flow.Flow

   /**
    * A user-defined or built-in session program (temperature preset + boost schedule).
    * boostStepsJson stores a JSON array of BoostStep: [{"offsetSec": 60, "boostC": 5}, ...]
    */
   @Entity(tableName = "session_programs")
   data class SessionProgram(
       @PrimaryKey(autoGenerate = true) val id: Long = 0,
       val name:           String,
       val targetTempC:    Int,
       val boostOffsetC:   Int = 0,
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

2. **Update `AppDatabase.kt`**:
   - Add `SessionProgram::class` to the `entities` list
   - Bump `version` from `3` to `4`
   - Add `sessionProgramDao(): SessionProgramDao` abstract function
   - Add `MIGRATION_3_4` constant and register it via `.addMigrations(MIGRATION_3_4)`:
     ```kotlin
     val MIGRATION_3_4 = Migration(3, 4) { db ->
         db.execSQL("""
             CREATE TABLE IF NOT EXISTS session_programs (
                 id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                 name TEXT NOT NULL,
                 targetTempC INTEGER NOT NULL,
                 boostOffsetC INTEGER NOT NULL DEFAULT 0,
                 boostStepsJson TEXT NOT NULL DEFAULT '[]',
                 isDefault INTEGER NOT NULL DEFAULT 0
             )
         """)
     }
     ```
   - Add the migration to the Room builder (wherever `.addMigrations(...)` is called in the codebase — check `di/AppModule.kt` if it is not inline in `AppDatabase.kt`)

3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `session_programs` table created by Migration(3, 4) with correct columns
- [ ] `SessionProgramDao` interface compiles with all five methods
- [ ] `AppDatabase.version` is `4` and `SessionProgram::class` is in entity list
- [ ] `./gradlew assembleDebug` passes with no errors

## Do NOT
- Seed default programs here — that is T-043's job
- Change any existing table or migration
- Touch UI files
