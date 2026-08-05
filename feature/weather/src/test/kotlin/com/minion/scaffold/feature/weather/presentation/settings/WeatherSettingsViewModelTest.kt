package com.minion.scaffold.feature.weather.presentation.settings

import com.minion.scaffold.core.testing.MainDispatcherRule
import com.minion.scaffold.core.weather.model.WeatherUnit
import com.minion.scaffold.feature.weather.domain.ObserveWeatherUnitUseCase
import com.minion.scaffold.feature.weather.domain.SetWeatherUnitUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class WeatherSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val setWeatherUnit = mockk<SetWeatherUnitUseCase>(relaxed = true)
    private val observeWeatherUnit = mockk<ObserveWeatherUnitUseCase>()
    private val unit = MutableStateFlow(WeatherUnit.METRIC)

    private fun viewModel(): WeatherSettingsViewModel {
        every { observeWeatherUnit() } returns unit
        return WeatherSettingsViewModel(observeWeatherUnit, setWeatherUnit)
    }

    @Test
    fun `defaults to metric`() {
        assertEquals(WeatherUnit.METRIC, viewModel().state.value.unit)
    }

    @Test
    fun `renders whatever the preference reports`() = runTest {
        val viewModel = viewModel()
        unit.value = WeatherUnit.IMPERIAL
        advanceUntilIdle()

        assertEquals(WeatherUnit.IMPERIAL, viewModel.state.value.unit)
    }

    @Test
    fun `selecting a unit writes it`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(WeatherSettingsIntent.UnitSelected(WeatherUnit.IMPERIAL))
        advanceUntilIdle()

        coVerify(exactly = 1) { setWeatherUnit(WeatherUnit.IMPERIAL) }
    }

    @Test
    fun `state follows the store, not the tap`() = runTest {
        val viewModel = viewModel()

        // The write is stubbed out and never feeds back into the flow, so the state must not move.
        // This is what stops the toggle showing a setting that failed to save.
        viewModel.onIntent(WeatherSettingsIntent.UnitSelected(WeatherUnit.IMPERIAL))
        advanceUntilIdle()

        assertEquals(WeatherUnit.METRIC, viewModel.state.value.unit)
    }
}
