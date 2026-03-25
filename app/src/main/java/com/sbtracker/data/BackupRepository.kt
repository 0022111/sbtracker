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
