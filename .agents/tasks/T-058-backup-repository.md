# T-058 — BackupRepository: Close WAL, Copy DB to Cache, Emit FileProvider URI

**Phase**: Phase 3 — F-026 Data Backup/Restore
**Blocked by**: nothing
**Estimated diff**: ~80 lines changed across 2 files

## Goal
Create a `BackupRepository` that safely checkpoints the Room WAL, copies `sbtracker.db` to the app's cache directory, wraps it in a `FileProvider` URI, and emits that URI via a `SharedFlow` so `MainActivity` can fire a share intent.

## Read these files first
- `app/src/main/java/com/sbtracker/HistoryViewModel.kt` — study the existing `exportHistoryCsv()` pattern: how `FileProvider.getUriForFile()` is called, how `_exportUri: MutableSharedFlow<Uri>` is declared and emitted.
- `app/src/main/java/com/sbtracker/di/AppModule.kt` — understand how `AppDatabase` is provided via Hilt `@Singleton` so you can inject it.
- `app/src/main/res/xml/file_paths.xml` — confirm the `<cache-path name="cache" path="." />` entry already covers the cache directory (no XML change needed).

## Change only these files
- `app/src/main/java/com/sbtracker/data/BackupRepository.kt` ← **new file**
- `app/src/main/java/com/sbtracker/di/AppModule.kt`

## Steps

1. **Create `BackupRepository.kt`** at `app/src/main/java/com/sbtracker/data/BackupRepository.kt`.

   ```
   package com.sbtracker.data

   import android.content.Context
   import android.net.Uri
   import androidx.core.content.FileProvider
   import dagger.hilt.android.qualifiers.ApplicationContext
   import kotlinx.coroutines.Dispatchers
   import kotlinx.coroutines.flow.MutableSharedFlow
   import kotlinx.coroutines.flow.asSharedFlow
   import kotlinx.coroutines.withContext
   import java.io.File
   import javax.inject.Inject
   import javax.inject.Singleton

   @Singleton
   class BackupRepository @Inject constructor(
       @ApplicationContext private val context: Context,
       private val db: AppDatabase
   ) {
       private val _backupUri = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
       val backupUri = _backupUri.asSharedFlow()

       suspend fun createBackup() {
           withContext(Dispatchers.IO) {
               // 1. Checkpoint the WAL so all pending writes are flushed to the main db file.
               db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()

               // 2. Source: the live database file managed by Room.
               val dbFile = context.getDatabasePath("sbtracker.db")

               // 3. Destination: a timestamped copy in the cache dir (covered by file_paths.xml).
               val timestamp = System.currentTimeMillis()
               val dest = File(context.cacheDir, "sbtracker_backup_$timestamp.db")
               dbFile.copyTo(dest, overwrite = true)

               // 4. Wrap in a FileProvider URI and emit.
               val uri = FileProvider.getUriForFile(
                   context,
                   "${context.packageName}.provider",
                   dest
               )
               _backupUri.emit(uri)
           }
       }
   }
   ```

2. **Register `BackupRepository` in `AppModule.kt`.**
   Add a `@Provides @Singleton` method after `provideAnalyticsRepository`:

   ```kotlin
   @Provides
   @Singleton
   fun provideBackupRepository(
       @ApplicationContext context: Context,
       db: AppDatabase
   ): BackupRepository = BackupRepository(context, db)
   ```

   (Alternatively, because `BackupRepository` uses `@Inject constructor` with `@Singleton`, Hilt can provide it without an explicit `@Provides`. Either approach is acceptable — use whichever is consistent with existing style in `AppModule.kt`.)

3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `BackupRepository` compiles with no errors.
- [ ] `AppModule` (or Hilt constructor injection) can supply a `BackupRepository` instance.
- [ ] `BackupRepository.createBackup()` emits a non-null `Uri` to `backupUri` when called.
- [ ] `./gradlew assembleDebug` passes.

## Do NOT
- Do not close or invalidate the Room database instance — only checkpoint the WAL.
- Do not add UI code in this task.
- Do not copy the `-shm` or `-wal` sidecar files; the `PRAGMA wal_checkpoint(FULL)` call ensures the main `.db` file is self-contained before the copy.
- Do not bump the Room schema version.
