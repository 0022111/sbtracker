package com.sbtracker.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sbtracker.BleManager
import com.sbtracker.analytics.AnalyticsRepository
import com.sbtracker.data.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No actual schema changes between v1 and v2, just version bump.
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `session_metadata` (" +
                            "`sessionId` INTEGER NOT NULL, " +
                            "`isCapsule` INTEGER NOT NULL, " +
                            "`capsuleWeightGrams` REAL NOT NULL, " +
                            "`notes` TEXT, " +
                            "PRIMARY KEY(`sessionId`))"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `session_programs` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`targetTempC` INTEGER NOT NULL, " +
                            "`boostStepsJson` TEXT NOT NULL, " +
                            "`isDefault` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "ALTER TABLE `session_metadata` ADD COLUMN `appliedProgramId` INTEGER"
                )
            }
        }

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "sbtracker.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }

    @Provides
    fun provideDeviceStatusDao(db: AppDatabase): DeviceStatusDao = db.deviceStatusDao()

    @Provides
    fun provideExtendedDataDao(db: AppDatabase): ExtendedDataDao = db.extendedDataDao()

    @Provides
    fun provideDeviceInfoDao(db: AppDatabase): DeviceInfoDao = db.deviceInfoDao()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideChargeCycleDao(db: AppDatabase): ChargeCycleDao = db.chargeCycleDao()

    @Provides
    fun provideHitDao(db: AppDatabase): HitDao = db.hitDao()

    @Provides
    fun provideSessionMetadataDao(db: AppDatabase): SessionMetadataDao = db.sessionMetadataDao()

    @Provides
    fun provideSessionProgramDao(db: AppDatabase): SessionProgramDao = db.sessionProgramDao()

    @Provides
    @Singleton
    fun provideBleManager(@ApplicationContext context: Context): BleManager {
        return BleManager(context)
    }

    @Provides
    @Singleton
    fun provideAnalyticsRepository(db: AppDatabase): AnalyticsRepository {
        return AnalyticsRepository(db)
    }
}
