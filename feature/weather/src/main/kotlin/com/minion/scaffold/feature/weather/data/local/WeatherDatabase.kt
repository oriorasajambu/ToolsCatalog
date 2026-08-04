package com.minion.scaffold.feature.weather.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * This feature's own Room database — not the app's, since nothing else needs a `forecast_cache`
 * table yet. `saved_locations` (SPEC.md §6) is added here once search/add ships.
 */
@Database(entities = [ForecastCacheEntity::class], version = 1, exportSchema = false)
internal abstract class WeatherDatabase : RoomDatabase() {
    abstract fun forecastCacheDao(): ForecastCacheDao
}
