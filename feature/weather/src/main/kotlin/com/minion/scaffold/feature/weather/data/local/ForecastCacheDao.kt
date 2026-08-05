package com.minion.scaffold.feature.weather.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
internal interface ForecastCacheDao {

    @Query("SELECT * FROM forecast_cache WHERE locationKey = :locationKey")
    suspend fun getByKey(locationKey: String): ForecastCacheEntity?

    @Upsert
    suspend fun upsert(entity: ForecastCacheEntity)

    /** Called when a saved location is removed, so its forecast does not outlive it. */
    @Query("DELETE FROM forecast_cache WHERE locationKey = :locationKey")
    suspend fun deleteByKey(locationKey: String)
}
