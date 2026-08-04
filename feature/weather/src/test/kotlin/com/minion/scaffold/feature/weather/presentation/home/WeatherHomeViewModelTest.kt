package com.minion.scaffold.feature.weather.presentation.home

import app.cash.turbine.test
import com.minion.scaffold.core.common.error.DomainError
import com.minion.scaffold.core.testing.MainDispatcherRule
import com.minion.scaffold.core.weather.model.CurrentConditions
import com.minion.scaffold.core.weather.model.Forecast
import com.minion.scaffold.core.weather.model.WeatherCondition
import com.minion.scaffold.feature.weather.domain.ForecastResult
import com.minion.scaffold.feature.weather.domain.GetCurrentLocationForecastUseCase
import com.minion.scaffold.feature.weather.domain.LocationCard
import com.minion.scaffold.feature.weather.domain.LocationFixOutcome
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class WeatherHomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val getCurrentLocationForecast = mockk<GetCurrentLocationForecastUseCase>()
    private val viewModel = WeatherHomeViewModel(getCurrentLocationForecast)

    private val card = LocationCard(
        locationId = "current",
        displayName = "Jakarta",
        latitude = -6.2,
        longitude = 106.8,
        result = ForecastResult(
            forecast = Forecast(
                current = CurrentConditions(28.0, 30.0, 70, 12.0, WeatherCondition.CLEAR),
                hourly = emptyList(),
                daily = emptyList(),
                notableConditions = emptyList(),
                fetchedAt = Instant.now(),
            ),
            isStale = false,
        ),
    )

    @Test
    fun `starts unknown, permission not yet checked`() {
        assertEquals(PermissionState.Unknown, viewModel.state.value.permission)
    }

    @Test
    fun `denied result with rationale still available becomes Denied`() = runTest {
        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = false, shouldShowRationale = true))

        assertEquals(PermissionState.Denied, viewModel.state.value.permission)
    }

    @Test
    fun `denied result with no more rationale becomes PermanentlyDenied`() = runTest {
        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = false, shouldShowRationale = false))

        assertEquals(PermissionState.PermanentlyDenied, viewModel.state.value.permission)
    }

    @Test
    fun `granted permission loads the pinned card`() = runTest {
        coEvery { getCurrentLocationForecast(false) } returns LocationFixOutcome.Found(card)

        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = true, shouldShowRationale = false))
        advanceUntilIdle()

        val content = viewModel.state.value.content
        assertTrue(content is WeatherHomeState.ContentState.Success)
        assertEquals("Jakarta", (content as WeatherHomeState.ContentState.Success).card.displayName)
    }

    @Test
    fun `no fix available shows the NoFix state, not a failure`() = runTest {
        coEvery { getCurrentLocationForecast(false) } returns LocationFixOutcome.NoFixAvailable

        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = true, shouldShowRationale = false))
        advanceUntilIdle()

        assertEquals(WeatherHomeState.ContentState.NoFix, viewModel.state.value.content)
    }

    @Test
    fun `a fetch failure with no cache surfaces the typed DomainError`() = runTest {
        coEvery { getCurrentLocationForecast(false) } returns LocationFixOutcome.Failed(DomainError.NoInternet)

        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = true, shouldShowRationale = false))
        advanceUntilIdle()

        val content = viewModel.state.value.content
        assertTrue(content is WeatherHomeState.ContentState.Failure)
        assertEquals(DomainError.NoInternet, (content as WeatherHomeState.ContentState.Failure).error)
    }

    @Test
    fun `stale cache stays visible while a background refresh is in flight`() = runTest {
        val staleCard = card.copy(
            result = card.result.copy(isStale = true),
        )
        coEvery { getCurrentLocationForecast(false) } returns LocationFixOutcome.Found(staleCard)

        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = true, shouldShowRationale = false))
        advanceUntilIdle()

        val content = viewModel.state.value.content as WeatherHomeState.ContentState.Success
        assertEquals(0L, content.card.staleHoursAgo)
    }

    @Test
    fun `fresh cache carries no stale label`() = runTest {
        coEvery { getCurrentLocationForecast(false) } returns LocationFixOutcome.Found(card)

        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = true, shouldShowRationale = false))
        advanceUntilIdle()

        val content = viewModel.state.value.content as WeatherHomeState.ContentState.Success
        assertNull(content.card.staleHoursAgo)
    }

    @Test
    fun `pull to refresh forces a refetch`() = runTest {
        coEvery { getCurrentLocationForecast(true) } returns LocationFixOutcome.Found(card)

        viewModel.onIntent(WeatherHomeIntent.PullToRefresh)
        advanceUntilIdle()

        val content = viewModel.state.value.content
        assertTrue(content is WeatherHomeState.ContentState.Success)
    }

    @Test
    fun `card click on a loaded card emits NavigateToDetail`() = runTest {
        coEvery { getCurrentLocationForecast(false) } returns LocationFixOutcome.Found(card)
        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = true, shouldShowRationale = false))
        advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onIntent(WeatherHomeIntent.CardClicked)
            assertEquals(WeatherHomeEffect.NavigateToDetail("current"), awaitItem())
        }
    }

    @Test
    fun `app settings requested emits OpenAppSettings`() = runTest {
        viewModel.effect.test {
            viewModel.onIntent(WeatherHomeIntent.AppSettingsRequested)
            assertEquals(WeatherHomeEffect.OpenAppSettings, awaitItem())
        }
    }
}
