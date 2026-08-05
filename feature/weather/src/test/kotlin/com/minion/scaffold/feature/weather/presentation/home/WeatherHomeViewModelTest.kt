package com.minion.scaffold.feature.weather.presentation.home

import app.cash.turbine.test
import com.minion.scaffold.core.common.error.DomainError
import com.minion.scaffold.core.common.result.AppResult
import com.minion.scaffold.core.ui.permission.PermissionState
import com.minion.scaffold.core.testing.MainDispatcherRule
import com.minion.scaffold.core.weather.model.CurrentConditions
import com.minion.scaffold.core.weather.model.Forecast
import com.minion.scaffold.core.weather.model.Location
import com.minion.scaffold.core.weather.model.WeatherCondition
import com.minion.scaffold.core.weather.model.WeatherUnit
import com.minion.scaffold.core.weather.usecase.ConvertUnitsUseCase
import com.minion.scaffold.feature.weather.domain.ForecastResult
import com.minion.scaffold.feature.weather.domain.GetCurrentLocationForecastUseCase
import com.minion.scaffold.feature.weather.domain.GetForecastUseCase
import com.minion.scaffold.feature.weather.domain.LocationCard
import com.minion.scaffold.feature.weather.domain.LocationFixOutcome
import com.minion.scaffold.feature.weather.domain.ObserveSavedLocationsUseCase
import com.minion.scaffold.feature.weather.domain.ObserveWeatherUnitUseCase
import com.minion.scaffold.feature.weather.domain.RemoveSavedLocationUseCase
import com.minion.scaffold.feature.weather.domain.ReorderSavedLocationsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val getForecast = mockk<GetForecastUseCase>(relaxed = true)
    private val removeSavedLocation = mockk<RemoveSavedLocationUseCase>(relaxed = true)
    private val reorderSavedLocations = mockk<ReorderSavedLocationsUseCase>(relaxed = true)
    private val observeSavedLocations = mockk<ObserveSavedLocationsUseCase>()
    private val observeWeatherUnit = mockk<ObserveWeatherUnitUseCase>()

    private val savedLocations = MutableStateFlow<List<Location>>(emptyList())
    private val unit = MutableStateFlow(WeatherUnit.METRIC)

    private fun forecast(temperatureCelsius: Double = 28.0) = Forecast(
        current = CurrentConditions(temperatureCelsius, 30.0, 70, 12.0, WeatherCondition.CLEAR),
        hourly = emptyList(),
        daily = emptyList(),
        notableConditions = emptyList(),
        fetchedAt = Instant.now(),
    )

    private val card = LocationCard(
        locationId = "current",
        displayName = "Jakarta",
        latitude = -6.2,
        longitude = 106.8,
        result = ForecastResult(forecast(), isStale = false),
    )

    private fun viewModel(): WeatherHomeViewModel {
        every { observeSavedLocations() } returns savedLocations
        every { observeWeatherUnit() } returns unit
        return WeatherHomeViewModel(
            getCurrentLocationForecast = getCurrentLocationForecast,
            getForecast = getForecast,
            removeSavedLocation = removeSavedLocation,
            reorderSavedLocations = reorderSavedLocations,
            convertUnits = ConvertUnitsUseCase(),
            observeSavedLocations = observeSavedLocations,
            observeWeatherUnit = observeWeatherUnit,
        )
    }

    private fun location(id: String, name: String) =
        Location(id, name, 1.0, 2.0, isCurrentLocation = false)

    @Test
    fun `starts unknown, permission not yet checked`() {
        assertEquals(PermissionState.Unknown, viewModel().state.value.permission)
    }

    @Test
    fun `denied result with rationale still available becomes Denied`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = false, shouldShowRationale = true))

        assertEquals(PermissionState.Denied, viewModel.state.value.permission)
    }

    @Test
    fun `denied result with no more rationale becomes PermanentlyDenied`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = false, shouldShowRationale = false))

        assertEquals(PermissionState.PermanentlyDenied, viewModel.state.value.permission)
    }

    @Test
    fun `granted permission loads the pinned card`() = runTest {
        coEvery { getCurrentLocationForecast(false) } returns LocationFixOutcome.Found(card)

        val viewModel = viewModel()
        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = true, shouldShowRationale = false))
        advanceUntilIdle()

        val content = viewModel.state.value.content
        assertTrue(content is WeatherHomeState.ContentState.Success)
        assertEquals("Jakarta", (content as WeatherHomeState.ContentState.Success).card.displayName)
    }

    @Test
    fun `no fix available shows the NoFix state, not a failure`() = runTest {
        coEvery { getCurrentLocationForecast(false) } returns LocationFixOutcome.NoFixAvailable

        val viewModel = viewModel()
        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = true, shouldShowRationale = false))
        advanceUntilIdle()

        assertEquals(WeatherHomeState.ContentState.NoFix, viewModel.state.value.content)
    }

    @Test
    fun `a fetch failure with no cache surfaces the typed DomainError`() = runTest {
        coEvery { getCurrentLocationForecast(false) } returns LocationFixOutcome.Failed(DomainError.NoInternet)

        val viewModel = viewModel()
        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = true, shouldShowRationale = false))
        advanceUntilIdle()

        val content = viewModel.state.value.content
        assertTrue(content is WeatherHomeState.ContentState.Failure)
        assertEquals(DomainError.NoInternet, (content as WeatherHomeState.ContentState.Failure).error)
    }

    @Test
    fun `stale cache stays visible, tagged with its age`() = runTest {
        val staleCard = card.copy(result = card.result.copy(isStale = true))
        coEvery { getCurrentLocationForecast(false) } returns LocationFixOutcome.Found(staleCard)

        val viewModel = viewModel()
        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = true, shouldShowRationale = false))
        advanceUntilIdle()

        val content = viewModel.state.value.content as WeatherHomeState.ContentState.Success
        assertEquals(0L, content.card.staleHoursAgo)
    }

    @Test
    fun `fresh cache carries no stale label`() = runTest {
        coEvery { getCurrentLocationForecast(false) } returns LocationFixOutcome.Found(card)

        val viewModel = viewModel()
        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = true, shouldShowRationale = false))
        advanceUntilIdle()

        val content = viewModel.state.value.content as WeatherHomeState.ContentState.Success
        assertNull(content.card.staleHoursAgo)
    }

    @Test
    fun `pull to refresh forces a refetch`() = runTest {
        coEvery { getCurrentLocationForecast(true) } returns LocationFixOutcome.Found(card)

        val viewModel = viewModel()
        viewModel.onIntent(WeatherHomeIntent.PullToRefresh)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.content is WeatherHomeState.ContentState.Success)
    }

    @Test
    fun `card click on a loaded card emits NavigateToDetail`() = runTest {
        coEvery { getCurrentLocationForecast(false) } returns LocationFixOutcome.Found(card)
        val viewModel = viewModel()
        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = true, shouldShowRationale = false))
        advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onIntent(WeatherHomeIntent.CardClicked)
            assertEquals(WeatherHomeEffect.NavigateToDetail("current"), awaitItem())
        }
    }

    @Test
    fun `app settings requested emits OpenAppSettings`() = runTest {
        val viewModel = viewModel()
        viewModel.effect.test {
            viewModel.onIntent(WeatherHomeIntent.AppSettingsRequested)
            assertEquals(WeatherHomeEffect.OpenAppSettings, awaitItem())
        }
    }

    @Test
    fun `saved locations become cards, each loading its own forecast`() = runTest {
        coEvery { getForecast(any(), any(), any(), any()) } returns
            AppResult.Success(ForecastResult(forecast(), isStale = false))

        val viewModel = viewModel()
        savedLocations.value = listOf(location("berlin", "Berlin"), location("cairo", "Cairo"))
        advanceUntilIdle()

        val cards = viewModel.state.value.savedCards
        assertEquals(listOf("Berlin", "Cairo"), cards.map { it.displayName })
        assertTrue(cards.all { it.forecast is SavedCardUi.ForecastState.Ready })
    }

    @Test
    fun `one saved location failing leaves the others readable`() = runTest {
        coEvery { getForecast("berlin", any(), any(), any()) } returns
            AppResult.Success(ForecastResult(forecast(), isStale = false))
        coEvery { getForecast("cairo", any(), any(), any()) } returns
            AppResult.Failure(DomainError.NoInternet)

        val viewModel = viewModel()
        savedLocations.value = listOf(location("berlin", "Berlin"), location("cairo", "Cairo"))
        advanceUntilIdle()

        val cards = viewModel.state.value.savedCards.associateBy { it.locationId }
        assertTrue(cards.getValue("berlin").forecast is SavedCardUi.ForecastState.Ready)
        assertEquals(SavedCardUi.ForecastState.Failed, cards.getValue("cairo").forecast)
    }

    @Test
    fun `a saved card click emits NavigateToDetail for that location`() = runTest {
        val viewModel = viewModel()
        viewModel.effect.test {
            viewModel.onIntent(WeatherHomeIntent.SavedCardClicked("berlin"))
            assertEquals(WeatherHomeEffect.NavigateToDetail("berlin"), awaitItem())
        }
    }

    @Test
    fun `moving a card reorders state without persisting until commit`() = runTest {
        coEvery { getForecast(any(), any(), any(), any()) } returns
            AppResult.Success(ForecastResult(forecast(), isStale = false))

        val viewModel = viewModel()
        savedLocations.value = listOf(location("a", "Alpha"), location("b", "Bravo"))
        advanceUntilIdle()

        viewModel.onIntent(WeatherHomeIntent.SavedCardMoved(fromIndex = 0, toIndex = 1))

        assertEquals(listOf("Bravo", "Alpha"), viewModel.state.value.savedCards.map { it.displayName })
        coVerify(exactly = 0) { reorderSavedLocations(any()) }
    }

    @Test
    fun `committing a drag persists the order once`() = runTest {
        coEvery { getForecast(any(), any(), any(), any()) } returns
            AppResult.Success(ForecastResult(forecast(), isStale = false))

        val viewModel = viewModel()
        savedLocations.value = listOf(location("a", "Alpha"), location("b", "Bravo"))
        advanceUntilIdle()

        viewModel.onIntent(WeatherHomeIntent.SavedCardMoved(fromIndex = 0, toIndex = 1))
        viewModel.onIntent(WeatherHomeIntent.SavedCardOrderCommitted)
        advanceUntilIdle()

        coVerify(exactly = 1) { reorderSavedLocations(listOf("b", "a")) }
    }

    @Test
    fun `removing a saved card delegates to the use case`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(WeatherHomeIntent.SavedCardRemoved("berlin"))
        advanceUntilIdle()

        coVerify(exactly = 1) { removeSavedLocation("berlin") }
    }

    @Test
    fun `switching to imperial reconverts every displayed temperature`() = runTest {
        coEvery { getCurrentLocationForecast(false) } returns
            LocationFixOutcome.Found(card.copy(result = ForecastResult(forecast(0.0), isStale = false)))
        coEvery { getForecast(any(), any(), any(), any()) } returns
            AppResult.Success(ForecastResult(forecast(100.0), isStale = false))

        val viewModel = viewModel()
        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = true, shouldShowRationale = false))
        savedLocations.value = listOf(location("berlin", "Berlin"))
        advanceUntilIdle()

        unit.value = WeatherUnit.IMPERIAL
        advanceUntilIdle()

        val pinned = viewModel.state.value.content as WeatherHomeState.ContentState.Success
        val saved = viewModel.state.value.savedCards.single().forecast as SavedCardUi.ForecastState.Ready
        assertEquals(WeatherUnit.IMPERIAL, viewModel.state.value.unit)
        assertEquals(32.0, pinned.card.temperature, 0.001)
        assertEquals(212.0, saved.temperature, 0.001)
    }

    @Test
    fun `flipping the unit twice does not double-convert`() = runTest {
        coEvery { getCurrentLocationForecast(false) } returns
            LocationFixOutcome.Found(card.copy(result = ForecastResult(forecast(0.0), isStale = false)))

        val viewModel = viewModel()
        viewModel.onIntent(WeatherHomeIntent.PermissionResult(granted = true, shouldShowRationale = false))
        advanceUntilIdle()

        unit.value = WeatherUnit.IMPERIAL
        advanceUntilIdle()
        unit.value = WeatherUnit.METRIC
        advanceUntilIdle()

        val pinned = viewModel.state.value.content as WeatherHomeState.ContentState.Success
        assertEquals(0.0, pinned.card.temperature, 0.001)
    }

    @Test
    fun `add and settings clicks emit their navigation effects`() = runTest {
        val viewModel = viewModel()
        viewModel.effect.test {
            viewModel.onIntent(WeatherHomeIntent.AddLocationClicked)
            assertEquals(WeatherHomeEffect.NavigateToSearch, awaitItem())
            viewModel.onIntent(WeatherHomeIntent.SettingsClicked)
            assertEquals(WeatherHomeEffect.NavigateToSettings, awaitItem())
        }
    }

    @Test
    fun `a location removed from the list drops its card`() = runTest {
        coEvery { getForecast(any(), any(), any(), any()) } returns
            AppResult.Success(ForecastResult(forecast(), isStale = false))

        val viewModel = viewModel()
        savedLocations.value = listOf(location("berlin", "Berlin"), location("cairo", "Cairo"))
        advanceUntilIdle()

        savedLocations.value = listOf(location("berlin", "Berlin"))
        advanceUntilIdle()

        assertEquals(listOf("Berlin"), viewModel.state.value.savedCards.map { it.displayName })
    }
}
