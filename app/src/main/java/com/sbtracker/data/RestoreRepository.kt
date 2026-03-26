package com.sbtracker.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
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
