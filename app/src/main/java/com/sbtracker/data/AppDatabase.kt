package com.sbtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for SBTracker.
 *
 * Current schema version: 2.
 *
 * Migration strategy:
 *  1. Bump version from N to N+1 (increment by 1, always).
 *  2. Write Migration(N, N+1) with ALTER TABLE / CREATE TABLE SQL in Migrations.kt.
 *  3. Add the migration to the builder via .addMigrations(...).
 *  4. Export the new schema JSON.
 *  5. Update MIGRATIONS.md with the version history entry.
 */
@Database(
    entities = [
        DeviceStatus::class,
        ExtendedData::class,
        DeviceInfo::class,
        Session::class,
        ChargeCycle::class,
        Hit::class
    ],
    version      = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun deviceStatusDao(): DeviceStatusDao
    abstract fun extendedDataDao(): ExtendedDataDao
    abstract fun deviceInfoDao():   DeviceInfoDao
    abstract fun sessionDao():      SessionDao
    abstract fun chargeCycleDao():  ChargeCycleDao
    abstract fun hitDao():          HitDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sbtracker.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
