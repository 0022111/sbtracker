package com.sbtracker.di

import android.content.Context
import androidx.room.Room
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
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "sbtracker.db"
        ).fallbackToDestructiveMigration().build()
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
