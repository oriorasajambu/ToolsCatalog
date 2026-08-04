package com.minion.scaffold.feature.weather.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One cached forecast, keyed by [locationKey] — `"current"` for the pinned GPS card, a geocoding
 * result id for anything else. [forecastJson] is a Gson-serialized [CachedForecast]; kept as one
 * JSON column rather than fully flattened, since nothing here needs to query into individual
 * forecast fields — only read-the-whole-row-by-key and write-the-whole-row.
 */
@Entity(tableName = "forecast_cache")
internal data class ForecastCacheEntity(
    @PrimaryKey val locationKey: String,
    val latitude: Double,
    val longitude: Double,
    val forecastJson: String,
    val fetchedAtEpochMillis: Long,
)
