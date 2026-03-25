# T-043 — ProgramRepository: CRUD and Default Preset Seeding

**Phase**: Phase 3 — F-027 Session Programs/Presets
**Blocked by**: T-042
**Estimated diff**: ~120 lines across 2 files

## Goal
Create `ProgramRepository` exposing CRUD operations and seed the three built-in presets on first run.

## Read these files first
- `app/src/main/java/com/sbtracker/data/SessionProgram.kt` — entity and DAO (created in T-042)
- `app/src/main/java/com/sbtracker/data/UserPreferencesRepository.kt` — reference for repository pattern using DataStore + Room in this project
- `app/src/main/java/com/sbtracker/di/AppModule.kt` — how repositories are provided via Hilt

## Change only these files
- `app/src/main/java/com/sbtracker/data/ProgramRepository.kt` *(create new)*
- `app/src/main/java/com/sbtracker/di/AppModule.kt`

## Steps

1. **Create `ProgramRepository.kt`**:
   ```kotlin
   package com.sbtracker.data

   import javax.inject.Inject
   import javax.inject.Singleton
   import kotlinx.coroutines.flow.Flow

   @Singleton
   class ProgramRepository @Inject constructor(
       private val dao: SessionProgramDao
   ) {
       val programs: Flow<List<SessionProgram>> = dao.observeAll()

       /** Seed default presets if the table is empty. Call once at app start. */
       suspend fun seedDefaultsIfNeeded() {
           if (dao.count() > 0) return
           val defaults = listOf(
               SessionProgram(name = "Terpene Optimization", targetTempC = 170, boostOffsetC = 0,
                   boostStepsJson = "[{\"offsetSec\":0,\"boostC\":0},{\"offsetSec\":60,\"boostC\":5},{\"offsetSec\":120,\"boostC\":10}]",
                   isDefault = true),
               SessionProgram(name = "Even Step", targetTempC = 185, boostOffsetC = 5,
                   boostStepsJson = "[{\"offsetSec\":0,\"boostC\":0},{\"offsetSec\":90,\"boostC\":5},{\"offsetSec\":180,\"boostC\":10}]",
                   isDefault = true),
               SessionProgram(name = "Full Heat Max Rip", targetTempC = 210, boostOffsetC = 10,
                   boostStepsJson = "[{\"offsetSec\":0,\"boostC\":10}]",
                   isDefault = true)
           )
           defaults.forEach { dao.insertOrUpdate(it) }
       }

       suspend fun saveProgram(program: SessionProgram): Long = dao.insertOrUpdate(program)

       suspend fun deleteProgram(id: Long) = dao.deleteUserProgram(id)

       suspend fun getById(id: Long): SessionProgram? = dao.getById(id)
   }
   ```

2. **Update `AppModule.kt`**:
   - Add a `@Provides @Singleton` binding for `ProgramRepository` if Hilt doesn't auto-inject it via `@Inject constructor`. Since the class uses `@Inject constructor`, Hilt resolves it automatically — you only need to add a `@Provides` for `SessionProgramDao`:
     ```kotlin
     @Provides
     fun provideSessionProgramDao(db: AppDatabase): SessionProgramDao = db.sessionProgramDao()
     ```

3. **Trigger seeding at app startup**: Open `SBTrackerApp.kt` or `MainViewModel.kt` (whichever has an `init` or coroutine init block) and call `programRepository.seedDefaultsIfNeeded()` once. Use `viewModelScope.launch` or `applicationScope` — do NOT call from UI thread.

4. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `ProgramRepository` compiles with `@Singleton` and `@Inject constructor`
- [ ] `SessionProgramDao` is provided via Hilt through `AppModule`
- [ ] `seedDefaultsIfNeeded()` is called once at startup (in `MainViewModel.init` or `SBTrackerApp`)
- [ ] Three default presets are inserted on first fresh install (seeding logic is idempotent — second run inserts nothing)
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Modify existing DAO files other than adding the new provider in AppModule
- Store boost step parsing logic here — steps remain as raw JSON strings until consumed by the session engine (T-046)
- Expose a suspend seed function from the ViewModel directly to the UI
