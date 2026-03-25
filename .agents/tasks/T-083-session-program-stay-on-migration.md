# T-083 — DB Migration v4→5: stayOnAtEnd field on SessionProgram

**Phase**: Phase 3 — F-027 Session Programs/Presets
**Blocked by**: —
**Estimated diff**: ~25 lines across 3 files

## Goal
Add `stayOnAtEnd: Boolean` to the `SessionProgram` entity and ship the corresponding
Room migration so the device keeps heating after all boost steps complete when this flag
is set. The execution logic that *reads* the flag belongs to T-046.

## Read these files first
- `app/src/main/java/com/sbtracker/data/SessionProgram.kt` — entity to modify
- `app/src/main/java/com/sbtracker/data/AppDatabase.kt` — bump version 4 → 5
- `app/src/main/java/com/sbtracker/di/AppModule.kt` — add MIGRATION_4_5 following the
  exact same pattern used for MIGRATION_3_4

## Change only these files
- `app/src/main/java/com/sbtracker/data/SessionProgram.kt`
- `app/src/main/java/com/sbtracker/data/AppDatabase.kt`
- `app/src/main/java/com/sbtracker/di/AppModule.kt`

## Steps

### 1. `SessionProgram.kt` — add field

```kotlin
data class SessionProgram(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name:           String,
    val targetTempC:    Int,
    val boostStepsJson: String = "[]",
    val isDefault:      Boolean = false,
    val stayOnAtEnd:    Boolean = false   // ← new; keep heater on after boost steps complete
)
```

### 2. `AppDatabase.kt` — bump version

```kotlin
version = 5,   // was 4
```

### 3. `AppModule.kt` — add MIGRATION_4_5

Inside `provideDatabase()`, after the MIGRATION_3_4 block:

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `session_programs` ADD COLUMN `stayOnAtEnd` INTEGER NOT NULL DEFAULT 0"
        )
    }
}
```

Then add `MIGRATION_4_5` to the `.addMigrations(...)` call.

### 4. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `SessionProgram.stayOnAtEnd` field exists with default `false`
- [ ] `AppDatabase.version` is 5
- [ ] `MIGRATION_4_5` is defined and wired into `.addMigrations()`
- [ ] `./gradlew assembleDebug` passes

## Do NOT
- Implement any execution logic reading `stayOnAtEnd` — that is part of T-046
- Add a UI toggle for `stayOnAtEnd` — that is a future editor enhancement
- Skip straight from 4 to 5 without an explicit Migration object
- Modify `ProgramRepository`, `SessionFragment`, or any ViewModel
