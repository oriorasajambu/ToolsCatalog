package com.minion.scaffold.feature.weather.data.repository

import com.minion.scaffold.core.common.dispatcher.IoDispatcher
import com.minion.scaffold.core.common.error.DomainError
import com.minion.scaffold.core.common.result.AppResult
import com.minion.scaffold.core.network.error.safeCall
import com.minion.scaffold.core.weather.mapper.WmoConditionMapper
import com.minion.scaffold.core.weather.model.Forecast
import com.minion.scaffold.core.weather.usecase.EvaluateNotableConditionsUseCase
import com.minion.scaffold.feature.weather.data.local.CachedForecast
import com.minion.scaffold.feature.weather.data.local.ForecastCacheDao
import com.minion.scaffold.feature.weather.data.local.ForecastCacheEntity
import com.minion.scaffold.feature.weather.data.local.toCache
import com.minion.scaffold.feature.weather.data.local.toDomain
import com.minion.scaffold.feature.weather.data.location.LocationFixProvider
import com.minion.scaffold.feature.weather.data.location.ReverseGeocoder
import com.minion.scaffold.feature.weather.data.remote.ForecastFields
import com.minion.scaffold.feature.weather.data.remote.WeatherApi
import com.minion.scaffold.feature.weather.data.remote.toCurrentConditions
import com.minion.scaffold.feature.weather.data.remote.toDailyEntries
import com.minion.scaffold.feature.weather.data.remote.toHourlyEntries
import com.minion.scaffold.feature.weather.domain.ForecastResult
import com.minion.scaffold.feature.weather.domain.LocationCard
import com.minion.scaffold.feature.weather.domain.LocationFixOutcome
import com.minion.scaffold.feature.weather.domain.WeatherRepository
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

internal const val CURRENT_LOCATION_KEY = "current"

/**
 * Orchestrates the cache-then-network flow SPEC.md §6 describes: fresh cache wins outright, a
 * stale or missing cache triggers a fetch, and a fetch failure only ever reaches the UI as a
 * [DomainError] when there is nothing cached to fall back to.
 */
internal class WeatherRepositoryImpl @Inject constructor(
    private val weatherApi: WeatherApi,
    private val forecastCacheDao: ForecastCacheDao,
    private val locationFixProvider: LocationFixProvider,
    private val reverseGeocoder: ReverseGeocoder,
    private val conditionMapper: WmoConditionMapper,
    private val evaluateNotableConditions: EvaluateNotableConditionsUseCase,
    private val gson: Gson,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WeatherRepository {

    override suspend fun getCurrentLocationCard(forceRefresh: Boolean): LocationFixOutcome =
        withContext(ioDispatcher) {
            val fix = locationFixProvider.currentFix() ?: return@withContext LocationFixOutcome.NoFixAvailable

            val displayName = reverseGeocoder.displayNameFor(fix.latitude, fix.longitude)
                ?: "%.2f, %.2f".format(fix.latitude, fix.longitude)

            val result = getForecast(CURRENT_LOCATION_KEY, fix.latitude, fix.longitude, forceRefresh)

            when (result) {
                is AppResult.Success -> LocationFixOutcome.Found(
                    LocationCard(
                        locationId = CURRENT_LOCATION_KEY,
                        displayName = displayName,
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        result = result.data,
                    ),
                )
                is AppResult.Failure -> LocationFixOutcome.Failed(result.error)
            }
        }

    override suspend fun getForecast(
        locationKey: String,
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean,
    ): AppResult<ForecastResult> = withContext(ioDispatcher) {
        val cached = forecastCacheDao.getByKey(locationKey)

        if (cached != null && !forceRefresh && !cached.isStale()) {
            return@withContext AppResult.Success(ForecastResult(cached.toForecast(), isStale = false))
        }

        val fetchResult = safeCall {
            weatherApi.getForecast(
                latitude = latitude,
                longitude = longitude,
                current = ForecastFields.CURRENT,
                hourly = ForecastFields.HOURLY,
                daily = ForecastFields.DAILY,
                timezone = ForecastFields.TIMEZONE,
                forecastDays = ForecastFields.FORECAST_DAYS,
            )
        }

        when (fetchResult) {
            is AppResult.Success -> {
                val dto = fetchResult.data
                val current = dto.toCurrentConditions(conditionMapper)
                val hourly = dto.toHourlyEntries(conditionMapper)
                val daily = dto.toDailyEntries(conditionMapper)
                val forecast = Forecast(
                    current = current,
                    hourly = hourly,
                    daily = daily,
                    notableConditions = evaluateNotableConditions(current, hourly, daily),
                    fetchedAt = Instant.now(),
                )
                forecastCacheDao.upsert(
                    ForecastCacheEntity(
                        locationKey = locationKey,
                        latitude = latitude,
                        longitude = longitude,
                        forecastJson = gson.toJson(forecast.toCache()),
                        fetchedAtEpochMillis = forecast.fetchedAt.toEpochMilli(),
                    ),
                )
                AppResult.Success(ForecastResult(forecast, isStale = false))
            }

            is AppResult.Failure -> if (cached != null) {
                // Never surface the failure while a cache exists (SPEC.md §8) — silently keep
                // showing what's on disk, tagged stale.
                AppResult.Success(ForecastResult(cached.toForecast(), isStale = true))
            } else {
                fetchResult
            }
        }
    }

    override suspend fun getForecastByKey(locationKey: String, forceRefresh: Boolean): AppResult<ForecastResult> =
        withContext(ioDispatcher) {
            val cached = forecastCacheDao.getByKey(locationKey)
                ?: return@withContext AppResult.Failure(DomainError.EmptyCache)

            getForecast(locationKey, cached.latitude, cached.longitude, forceRefresh)
        }

    private fun ForecastCacheEntity.isStale(): Boolean =
        System.currentTimeMillis() - fetchedAtEpochMillis > STALE_AFTER_MS

    private fun ForecastCacheEntity.toForecast(): Forecast =
        gson.fromJson(forecastJson, CachedForecast::class.java).toDomain()

    private companion object {
        const val STALE_AFTER_MS = 3 * 60 * 60 * 1000L
    }
}
