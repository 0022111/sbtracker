# T-059 — RestoreRepository: Validate, Close, Overwrite DB, Signal Restart

**Phase**: Phase 3 — F-026 Data Backup/Restore
**Blocked by**: T-058
**Estimated diff**: ~90 lines changed across 2 files

## Goal
Create a `RestoreRepository` that accepts a content URI pointing to a user-selected `.db` file, validates it is a real SQLite database, closes Room, overwrites `sbtracker.db`, and emits a signal for `MainActivity` to recreate the process (force-restart the app so Room reopens cleanly).

## Read these files first
- `app/src/main/java/com/sbtracker/data/BackupRepository.kt` — read the pattern established in T-058 (SharedFlow emission, WAL checkpoint, FileProvider).
- `app/src/main/java/com/sbtracker/di/AppModule.kt` — understand how `AppDatabase` is provided; note the `Room.databaseBuilder(…, "sbtracker.db")` call that names the file.

## Change only these files
- `app/src/main/java/com/sbtracker/data/RestoreRepository.kt` ← **new file**
- `app/src/main/java/com/sbtracker/di/AppModule.kt`

## Steps

1. **Create `RestoreRepository.kt`** at `app/src/main/java/com/sbtracker/data/RestoreRepository.kt`.

   ```
   package com.sbtracker.data

   import android.content.Context
   import android.net.Uri
   import dagger.hilt.android.qualifiers.ApplicationContext
   import kotlinx.coroutines.Dispatchers
   import kotlinx.coroutines.flow.MutableSharedFlow
   import kotlinx.coroutines.flow.asSharedFlow
   import kotlinx.coroutines.withContext
   import javax.inject.Inject
   import javax.inject.Singleton

   /** Result sealed type so the UI can show success or a descriptive error. */
   sealed class RestoreResult {
       object Success : RestoreResult()
       data class Failure(val reason: String) : RestoreResult()
   }

   @Singleton
   class RestoreRepository @Inject constructor(
       @ApplicationContext private val context: Context,
       private val db: AppDatabase
   ) {
       private val _restoreResult = MutableSharedFlow<RestoreResult>(extraBufferCapacity = 1)
       val restoreResult = _restoreResult.asSharedFlow()

       suspend fun restoreFrom(sourceUri: Uri) {
           withContext(Dispatchers.IO) {
               try {
                   // 1. Read bytes from the content URI.
                   val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                       ?: run {
                           _restoreResult.emit(RestoreResult.Failure("Cannot open selected file."))
                           return@withContext
                       }

                   // 2. Validate SQLite magic header (first 16 bytes = "SQLite format 3\000").
                   val magic = "SQLite format 3\u0000".toByteArray(Charsets.UTF_8)
                   if (bytes.size < magic.size || !bytes.sliceArray(magic.indices).contentEquals(magic)) {
                       _restoreResult.emit(RestoreResult.Failure("Selected file is not a valid SQLite database."))
                       return@withContext
                   }

                   // 3. Close Room (flushes all pending transactions).
                   db.close()

                   // 4. Overwrite the database file on disk.
                   val dbFile = context.getDatabasePath("sbtracker.db")
                   dbFile.parentFile?.mkdirs()
                   dbFile.writeBytes(bytes)

                   // Also remove stale WAL/SHM sidecar files if present.
                   File(dbFile.path + "-wal").delete()
                   File(dbFile.path + "-shm").delete()

                   _restoreResult.emit(RestoreResult.Success)
               } catch (e: Exception) {
                   _restoreResult.emit(RestoreResult.Failure("Restore failed: ${e.message}"))
               }
           }
       }
   }
   ```

   Note the `import java.io.File` is needed for the WAL/SHM cleanup lines.

2. **Register `RestoreRepository` in `AppModule.kt`** (same approach as `BackupRepository` in T-058 — add a `@Provides @Singleton` method or rely on `@Inject constructor` + `@Singleton`).

3. Run `./gradlew assembleDebug` and confirm it passes.

## Acceptance criteria
- [ ] `RestoreRepository` compiles with no errors.
- [ ] Calling `restoreFrom()` with a non-SQLite URI emits `RestoreResult.Failure` with a descriptive message.
- [ ] Calling `restoreFrom()` with a valid SQLite URI emits `RestoreResult.Success` and overwrites `sbtracker.db`.
- [ ] Stale `-wal` and `-shm` sidecar files are deleted on successful restore.
- [ ] `./gradlew assembleDebug` passes.

## Do NOT
- Do not re-open the Room database after closing it — the app must be restarted for Room to reopen cleanly (the restart signal is handled in T-062).
- Do not add UI code in this task.
- Do not bump the Room schema version.
- Do not assume the incoming file matches the current schema — Room will handle migration or fall back to destructive migration on reopen (the existing `fallbackToDestructiveMigration()` covers this during dev).
