package com.sbtracker.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version bump 1 → 2: schema structure was unchanged; identity hashes are equal.
 * The migration is a no-op but must be registered so Room accepts existing databases.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No DDL changes between v1 and v2.
    }
}
