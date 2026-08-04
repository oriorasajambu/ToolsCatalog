package com.minion.scaffold.feature.weather.data.repository

import com.google.gson.Gson
import com.minion.scaffold.core.common.error.DomainError
import com.minion.scaffold.core.common.result.AppResult
import com.minion.scaffold.core.weather.mapper.WmoConditionMapper
import com.minion.scaffold.core.weather.usecase.EvaluateNotableConditionsUseCase
import com.minion.scaffold.feature.weather.data.local.CachedForecast
import com.minion.scaffold.feature.weather.data.local.CachedCurrentConditions
import com.minion.scaffold.feature.weather.data.local.ForecastCacheEntity
import com.minion.scaffold.core.weather.model.WeatherCondition
import com.minion.scaffold.feature.weather.data.location.LatLng
import com.minion.scaffold.feature.weather.data.location.LocationFixProvider
import com.minion.scaffold.feature.weather.data.location.ReverseGeocoder
import com.minion.scaffold.feature.weather.domain.LocationFixOutcome
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
internal class WeatherRepositoryImplTest {

    private val weatherApi = FakeWeatherApi()
    private val cacheDao = FakeForecastCacheDao()
    private val locationFixProvider = mockk<LocationFixProvider>()
    private val reverseGeocoder = mockk<ReverseGeocoder>()
    private lateinit var repository: WeatherRepositoryImpl

    @Before
    fun setUp() {
        repository = WeatherRepositoryImpl(
            weatherApi = weatherApi,
            forecastCacheDao = cacheDao,
            locationFixProvider = locationFixProvider,
            reverseGeocoder = reverseGeocoder,
            conditionMapper = WmoConditionMapper(),
            evaluateNotableConditions = EvaluateNotableConditionsUseCase(),
            gson = Gson(),
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun freshCacheRow(locationKey: String = "current") = ForecastCacheEntity(
        locationKey = locationKey,
        latitude = -6.2,
        longitude = 106.8,
        forecastJson = Gson().toJson(
            CachedForecast(
                current = CachedCurrentConditions(20.0, 20.0, 50, 5.0, WeatherCondition.CLEAR),
                hourly = emptyList(),
                daily = emptyList(),
                notableConditions = emptyList(),
                fetchedAtEpochMillis = System.currentTimeMillis(),
            ),
        ),
        fetchedAtEpochMillis = System.currentTimeMillis(),
    )

    @Test
    fun `fresh cache is returned without calling the network`() = runTest {
        cacheDao.seed(freshCacheRow())

        val result = repository.getForecast("current", -6.2, 106.8, forceRefresh = false)

        assertTrue(result is AppResult.Success)
        assertEquals(0, weatherApi.callCount)
    }

    @Test
    fun `no cache fetches from the network and writes the cache`() = runTest {
        val result = repository.getForecast("current", -6.2, 106.8, forceRefresh = false)

        assertTrue(result is AppResult.Success)
        assertEquals(1, weatherApi.callCount)
        assertTrue(cacheDao.getByKey("current") != null)
    }

    @Test
    fun `stale cache triggers a refetch`() = runTest {
        val staleRow = freshCacheRow().copy(
            fetchedAtEpochMillis = System.currentTimeMillis() - java.util.concurrent.TimeUnit.HOURS.toMillis(4),
        )
        cacheDao.seed(staleRow)

        repository.getForecast("current", -6.2, 106.8, forceRefresh = false)

        assertEquals(1, weatherApi.callCount)
    }

    @Test
    fun `force refresh refetches even when the cache is fresh`() = runTest {
        cacheDao.seed(freshCacheRow())

        repository.getForecast("current", -6.2, 106.8, forceRefresh = true)

        assertEquals(1, weatherApi.callCount)
    }

    @Test
    fun `a failed background refresh with an existing cache stays a Success, tagged stale`() = runTest {
        cacheDao.seed(
            freshCacheRow().copy(
                fetchedAtEpochMillis = System.currentTimeMillis() - java.util.concurrent.TimeUnit.HOURS.toMillis(4),
            ),
        )
        weatherApi.error = UnknownHostException()

        val result = repository.getForecast("current", -6.2, 106.8, forceRefresh = false)

        assertTrue(result is AppResult.Success)
        assertTrue((result as AppResult.Success).data.isStale)
    }

    @Test
    fun `a failed fetch with no cache at all surfaces the DomainError`() = runTest {
        weatherApi.error = UnknownHostException()

        val result = repository.getForecast("current", -6.2, 106.8, forceRefresh = false)

        assertTrue(result is AppResult.Failure)
        assertEquals(DomainError.NoInternet, (result as AppResult.Failure).error)
    }

    @Test
    fun `getForecastByKey with no cache row returns EmptyCache`() = runTest {
        val result = repository.getForecastByKey("unknown", forceRefresh = false)

        assertTrue(result is AppResult.Failure)
        assertEquals(DomainError.EmptyCache, (result as AppResult.Failure).error)
    }

    @Test
    fun `no GPS fix returns NoFixAvailable`() = runTest {
        coEvery { locationFixProvider.currentFix() } returns null

        val outcome = repository.getCurrentLocationCard(forceRefresh = false)

        assertEquals(LocationFixOutcome.NoFixAvailable, outcome)
    }

    @Test
    fun `a resolved fix with a reverse-geocoded name produces a Found card`() = runTest {
        coEvery { locationFixProvider.currentFix() } returns LatLng(-6.2, 106.8)
        coEvery { reverseGeocoder.displayNameFor(-6.2, 106.8) } returns "Jakarta"

        val outcome = repository.getCurrentLocationCard(forceRefresh = false)

        assertTrue(outcome is LocationFixOutcome.Found)
        assertEquals("Jakarta", (outcome as LocationFixOutcome.Found).card.displayName)
    }

    @Test
    fun `a resolved fix with no reverse-geocoded name falls back to a coordinate string`() = runTest {
        coEvery { locationFixProvider.currentFix() } returns LatLng(-6.2, 106.8)
        coEvery { reverseGeocoder.displayNameFor(-6.2, 106.8) } returns null

        val outcome = repository.getCurrentLocationCard(forceRefresh = false)

        assertTrue(outcome is LocationFixOutcome.Found)
        assertTrue((outcome as LocationFixOutcome.Found).card.displayName.contains("-6.20"))
    }
}
