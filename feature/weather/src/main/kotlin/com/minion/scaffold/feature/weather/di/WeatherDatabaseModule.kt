package com.minion.scaffold.feature.weather.di

import android.content.Context
import androidx.room.Room
import com.minion.scaffold.feature.weather.data.local.ForecastCacheDao
import com.minion.scaffold.feature.weather.data.local.MIGRATION_1_2
import com.minion.scaffold.feature.weather.data.local.SavedLocationDao
import com.minion.scaffold.feature.weather.data.local.WeatherDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** This feature's own Room database — see [WeatherDatabase] for why it isn't shared. */
@Module
@InstallIn(SingletonComponent::class)
internal object WeatherDatabaseModule {

    private const val DATABASE_NAME = "weather.db"

    @Provides
    @Singleton
    fun provideWeatherDatabase(@ApplicationContext context: Context): WeatherDatabase =
        Room.databaseBuilder(context, WeatherDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    @Singleton
    fun provideForecastCacheDao(database: WeatherDatabase): ForecastCacheDao =
        database.forecastCacheDao()

    @Provides
    @Singleton
    fun provideSavedLocationDao(database: WeatherDatabase): SavedLocationDao =
        database.savedLocationDao()
}
