package com.minion.scaffold.feature.weather.data.repository

import com.minion.scaffold.feature.weather.data.local.ForecastCacheDao
import com.minion.scaffold.feature.weather.data.local.ForecastCacheEntity

/**
 * In-memory [ForecastCacheDao]. Room itself needs Robolectric to run in a JVM unit test — not a
 * dependency this repo carries — so the repository is tested against this instead, matching
 * SPEC.md §10's "fake API, no live network calls" approach for the DAO seam too.
 */
internal class FakeForecastCacheDao : ForecastCacheDao {

    private val rows = mutableMapOf<String, ForecastCacheEntity>()

    override suspend fun getByKey(locationKey: String): ForecastCacheEntity? = rows[locationKey]

    override suspend fun upsert(entity: ForecastCacheEntity) {
        rows[entity.locationKey] = entity
    }

    fun seed(entity: ForecastCacheEntity) {
        rows[entity.locationKey] = entity
    }
}
