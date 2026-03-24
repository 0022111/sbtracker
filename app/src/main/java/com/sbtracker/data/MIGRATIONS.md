# SBTracker Database Migration Guide

## Schema Version History

| Version | Description |
|---------|-------------|
| 1       | Frozen baseline (2026-03). All pre-release iterations collapsed. |
| 2       | Version bump (No schema changes). |
| 3       | Added `session_metadata` table to unblock Health & Dosage tracking without destructive loss during reconstructs. |

## How to Add or Modify Schema

### Adding a Column to an Existing Table

1. Add the field to the `@Entity` data class with a `NOT NULL DEFAULT` value:
   ```kotlin
   val newField: Int = 0
   ```

2. Bump `version` in `@Database(version = N+1)` inside `AppDatabase.kt`.

3. Write the migration in `AppModule.kt`:
   ```kotlin
   val MIGRATION_N_N1 = object : Migration(N, N+1) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL("ALTER TABLE device_status ADD COLUMN newField INTEGER NOT NULL DEFAULT 0")
       }
   }
   ```

4. Register it in the builder inside `AppModule.kt`:
   ```kotlin
   .addMigrations(MIGRATION_N_N1)
   ```

5. Build the project to generate the new exported schema JSON under `app/schemas/`.

6. Commit the new schema JSON alongside your code changes.

### Adding a New Table

Same process, but use `CREATE TABLE IF NOT EXISTS` with all columns and indices. See `MIGRATION_2_3` in `AppModule.kt` for an example.

### Important Rules

- **Always increment by 1** — never jump versions.
- **Explicit migrations are strictly enforced** since `fallbackToDestructiveMigration()` has been removed for production stability.
- **`device_status` is the god log** — all analytics derive from it at query time. Adding columns here retroactively applies defaults to old rows. No re-ingestion needed.
