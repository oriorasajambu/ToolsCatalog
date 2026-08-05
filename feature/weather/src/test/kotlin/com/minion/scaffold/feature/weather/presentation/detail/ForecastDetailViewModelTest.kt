package com.minion.scaffold.feature.weather.presentation.detail

import androidx.lifecycle.SavedStateHandle
import com.minion.scaffold.core.common.error.DomainError
import com.minion.scaffold.core.common.result.AppResult
import com.minion.scaffold.core.navigation.WeatherDetailRoute
import com.minion.scaffold.core.testing.MainDispatcherRule
import com.minion.scaffold.core.weather.model.CurrentConditions
import com.minion.scaffold.core.weather.model.Forecast
import com.minion.scaffold.core.weather.model.WeatherCondition
import com.minion.scaffold.feature.weather.domain.ForecastResult
import com.minion.scaffold.feature.weather.domain.GetForecastUseCase
import com.minion.scaffold.core.weather.model.WeatherUnit
import com.minion.scaffold.core.weather.usecase.ConvertUnitsUseCase
import com.minion.scaffold.feature.weather.domain.ObserveWeatherUnitUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class ForecastDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getForecast = mockk<GetForecastUseCase>()

    private val forecast = Forecast(
        current = CurrentConditions(28.0, 30.0, 70, 12.0, WeatherCondition.CLEAR),
        hourly = emptyList(),
        daily = emptyList(),
        notableConditions = emptyList(),
        fetchedAt = Instant.now(),
    )

    private val unit = MutableStateFlow(WeatherUnit.METRIC)

    private fun viewModel(locationId: String = "current"): ForecastDetailViewModel {
        val observeWeatherUnit = mockk<ObserveWeatherUnitUseCase>()
        every { observeWeatherUnit() } returns unit
        return ForecastDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(WeatherDetailRoute.ARG_LOCATION_ID to locationId)),
            getForecast = getForecast,
            convertUnits = ConvertUnitsUseCase(),
            observeWeatherUnit = observeWeatherUnit,
        )
    }

    @Test
    fun `loads on init`() = runTest {
        coEvery { getForecast("current", false) } returns AppResult.Success(ForecastResult(forecast, isStale = false))

        val viewModel = viewModel()
        advanceUntilIdle()

        val content = viewModel.state.value.content
        assertTrue(content is ForecastDetailState.ContentState.Success)
        coVerify(exactly = 1) { getForecast("current", false) }
    }

    @Test
    fun `failure with no cache surfaces the typed DomainError`() = runTest {
        coEvery { getForecast("current", false) } returns AppResult.Failure(DomainError.EmptyCache)

        val viewModel = viewModel()
        advanceUntilIdle()

        val content = viewModel.state.value.content
        assertTrue(content is ForecastDetailState.ContentState.Failure)
        assertEquals(DomainError.EmptyCache, (content as ForecastDetailState.ContentState.Failure).error)
    }

    @Test
    fun `pull to refresh forces a refetch`() = runTest {
        coEvery { getForecast("current", false) } returns AppResult.Success(ForecastResult(forecast, isStale = false))
        coEvery { getForecast("current", true) } returns AppResult.Success(ForecastResult(forecast, isStale = false))

        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntent(ForecastDetailIntent.PullToRefresh)
        advanceUntilIdle()

        coVerify(exactly = 1) { getForecast("current", true) }
    }

    @Test
    fun `switching to imperial reconverts the displayed forecast`() = runTest {
        val zeroCelsius = forecast.copy(current = forecast.current.copy(temperature = 0.0))
        coEvery { getForecast("current", false) } returns
            AppResult.Success(ForecastResult(zeroCelsius, isStale = false))

        val viewModel = viewModel()
        advanceUntilIdle()

        unit.value = WeatherUnit.IMPERIAL
        advanceUntilIdle()

        val content = viewModel.state.value.content as ForecastDetailState.ContentState.Success
        assertEquals(WeatherUnit.IMPERIAL, viewModel.state.value.unit)
        assertEquals(32.0, content.forecast.current.temperature, 0.001)
    }

    @Test
    fun `flipping the unit twice does not double-convert`() = runTest {
        val zeroCelsius = forecast.copy(current = forecast.current.copy(temperature = 0.0))
        coEvery { getForecast("current", false) } returns
            AppResult.Success(ForecastResult(zeroCelsius, isStale = false))

        val viewModel = viewModel()
        advanceUntilIdle()

        unit.value = WeatherUnit.IMPERIAL
        advanceUntilIdle()
        unit.value = WeatherUnit.METRIC
        advanceUntilIdle()

        val content = viewModel.state.value.content as ForecastDetailState.ContentState.Success
        assertEquals(0.0, content.forecast.current.temperature, 0.001)
    }

    @Test
    fun `reads the location id from the route`() = runTest {
        coEvery { getForecast("jakarta", false) } returns AppResult.Success(ForecastResult(forecast, isStale = false))

        viewModel(locationId = "jakarta")
        advanceUntilIdle()

        coVerify(exactly = 1) { getForecast("jakarta", false) }
    }
}
