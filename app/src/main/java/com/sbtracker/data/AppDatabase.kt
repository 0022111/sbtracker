package com.sbtracker.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for SBTracker.
 *
 * Schema version 1 — frozen baseline.
 *
 * Migration strategy:
 *  1. Bump version from N to N+1 (increment by 1, always).
 *  2. Write Migration(N, N+1) with ALTER TABLE / CREATE TABLE SQL.
 *  3. Add the migration to the builder via .addMigrations(...).
 *  4. Export the new schema JSON.
 *
 * fallbackToDestructiveMigration() has been intentionally omitted.
 * All schema versions 1–6 have explicit Migration objects in AppModule.
 */
@Database(
    entities = [
        DeviceStatus::class,
        ExtendedData::class,
        DeviceInfo::class,
        Session::class,
        ChargeCycle::class,
        Hit::class,
        SessionMetadata::class,
        SessionProgram::class
    ],
    version      = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun deviceStatusDao(): DeviceStatusDao
    abstract fun extendedDataDao(): ExtendedDataDao
    abstract fun deviceInfoDao():   DeviceInfoDao
    abstract fun sessionDao():      SessionDao
    abstract fun chargeCycleDao():  ChargeCycleDao
    abstract fun hitDao():          HitDao
    abstract fun sessionMetadataDao(): SessionMetadataDao
    abstract fun sessionProgramDao(): SessionProgramDao
}
