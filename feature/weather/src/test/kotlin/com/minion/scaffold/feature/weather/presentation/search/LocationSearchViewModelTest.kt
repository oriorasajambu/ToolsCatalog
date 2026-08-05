package com.minion.scaffold.feature.weather.presentation.search

import app.cash.turbine.test
import com.minion.scaffold.core.common.error.DomainError
import com.minion.scaffold.core.common.result.AppResult
import com.minion.scaffold.core.testing.MainDispatcherRule
import com.minion.scaffold.core.weather.model.Location
import com.minion.scaffold.core.weather.model.LocationSearchResult
import com.minion.scaffold.feature.weather.domain.AddSavedLocationUseCase
import com.minion.scaffold.feature.weather.domain.ObserveSavedLocationsUseCase
import com.minion.scaffold.feature.weather.domain.SearchLocationsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class LocationSearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val searchLocations = mockk<SearchLocationsUseCase>()
    private val addSavedLocation = mockk<AddSavedLocationUseCase>(relaxed = true)
    private val observeSavedLocations = mockk<ObserveSavedLocationsUseCase>()
    private val savedLocations = MutableStateFlow<List<Location>>(emptyList())

    private fun viewModel(): LocationSearchViewModel {
        every { observeSavedLocations() } returns savedLocations
        return LocationSearchViewModel(searchLocations, addSavedLocation, observeSavedLocations)
    }

    private fun result(id: String, name: String) =
        LocationSearchResult(id, name, "Testland", "Test Province", 1.0, 2.0)

    @Test
    fun `starts idle with an empty query`() {
        val viewModel = viewModel()

        assertEquals("", viewModel.state.value.query)
        assertEquals(LocationSearchState.ContentState.Idle, viewModel.state.value.content)
    }

    @Test
    fun `the query field updates immediately, before the debounce elapses`() = runTest {
        // Stubbed even though the assertion below expects no call: runTest drains virtual time at
        // the end of the body, which fires the debounce — an unstubbed mock would throw there.
        coEvery { searchLocations(any()) } returns AppResult.Success(emptyList())

        val viewModel = viewModel()
        viewModel.onIntent(LocationSearchIntent.QueryChanged("Jak"))

        // No advanceUntilIdle: the field has to feel responsive even though the search has not run.
        assertEquals("Jak", viewModel.state.value.query)
        coVerify(exactly = 0) { searchLocations(any()) }
    }

    @Test
    fun `typing runs one search after the debounce, not one per keystroke`() = runTest {
        coEvery { searchLocations(any()) } returns AppResult.Success(listOf(result("1", "Jakarta")))

        val viewModel = viewModel()
        "Jakarta".forEachIndexed { index, _ ->
            viewModel.onIntent(LocationSearchIntent.QueryChanged("Jakarta".take(index + 1)))
            advanceTimeBy(50)
        }
        advanceUntilIdle()

        coVerify(exactly = 1) { searchLocations("Jakarta") }
    }

    @Test
    fun `a matching search renders results`() = runTest {
        coEvery { searchLocations("Jakarta") } returns
            AppResult.Success(listOf(result("1", "Jakarta"), result("2", "Jakarta Barat")))

        val viewModel = viewModel()
        viewModel.onIntent(LocationSearchIntent.QueryChanged("Jakarta"))
        advanceUntilIdle()

        val content = viewModel.state.value.content
        assertTrue(content is LocationSearchState.ContentState.Results)
        assertEquals(2, (content as LocationSearchState.ContentState.Results).results.size)
    }

    @Test
    fun `a search matching nothing is Empty, not Failure`() = runTest {
        coEvery { searchLocations("zzzz") } returns AppResult.Success(emptyList())

        val viewModel = viewModel()
        viewModel.onIntent(LocationSearchIntent.QueryChanged("zzzz"))
        advanceUntilIdle()

        assertEquals(LocationSearchState.ContentState.Empty, viewModel.state.value.content)
    }

    @Test
    fun `a failed search surfaces the typed DomainError`() = runTest {
        coEvery { searchLocations("Jakarta") } returns AppResult.Failure(DomainError.NoInternet)

        val viewModel = viewModel()
        viewModel.onIntent(LocationSearchIntent.QueryChanged("Jakarta"))
        advanceUntilIdle()

        val content = viewModel.state.value.content
        assertTrue(content is LocationSearchState.ContentState.Failure)
        assertEquals(DomainError.NoInternet, (content as LocationSearchState.ContentState.Failure).error)
    }

    @Test
    fun `a query below the minimum length goes back to Idle without searching`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(LocationSearchIntent.QueryChanged("J"))
        advanceUntilIdle()

        assertEquals(LocationSearchState.ContentState.Idle, viewModel.state.value.content)
        coVerify(exactly = 0) { searchLocations(any()) }
    }

    @Test
    fun `selecting a result saves it and confirms`() = runTest {
        val hit = result("1", "Jakarta")
        val viewModel = viewModel()

        viewModel.effect.test {
            viewModel.onIntent(LocationSearchIntent.ResultSelected(hit))
            assertEquals(LocationSearchEffect.LocationAdded("Jakarta"), awaitItem())
        }
        coVerify(exactly = 1) { addSavedLocation(hit.toLocation()) }
    }

    @Test
    fun `already-saved ids are reflected in state`() = runTest {
        val viewModel = viewModel()
        savedLocations.value = listOf(Location("1", "Jakarta", 1.0, 2.0, isCurrentLocation = false))
        advanceUntilIdle()

        assertEquals(setOf("1"), viewModel.state.value.savedIds)
    }
}
