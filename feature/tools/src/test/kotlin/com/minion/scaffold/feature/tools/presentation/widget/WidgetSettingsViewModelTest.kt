package com.minion.scaffold.feature.tools.presentation.widget

import com.minion.scaffold.core.data.widget.MAX_PINNED_TOOLS
import com.minion.scaffold.core.data.widget.WidgetPinRequester
import com.minion.scaffold.core.domain.featureflag.FeatureFlagRepository
import com.minion.scaffold.core.domain.featureflag.FeatureFlags
import com.minion.scaffold.core.testing.MainDispatcherRule
import com.minion.scaffold.core.toolcatalog.ToolCatalog
import com.minion.scaffold.core.toolcatalog.ToolDescriptor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class WidgetSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `selecting at the cap changes nothing`() = runTest {
        val repository = FakePinnedToolsRepository(firstIds(MAX_PINNED_TOOLS))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        val before = viewModel.state.value.pinned
        val writesBefore = repository.writes

        viewModel.onIntent(WidgetSettingsIntent.ToggleTool(catalogIds[MAX_PINNED_TOOLS]))
        advanceUntilIdle()

        assertEquals(before, viewModel.state.value.pinned)
        assertEquals("a refused pin must not persist", writesBefore, repository.writes)
    }

    @Test
    fun `deselecting frees a slot and re-enables the unselected rows`() = runTest {
        val repository = FakePinnedToolsRepository(firstIds(MAX_PINNED_TOOLS))
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isAtCap)

        viewModel.onIntent(WidgetSettingsIntent.ToggleTool(catalogIds[0]))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isAtCap)
        assertEquals(MAX_PINNED_TOOLS - 1, viewModel.state.value.pinnedCount)
    }

    @Test
    fun `reorder produces the expected order for every in-range pair`() = runTest {
        val size = 4
        for (from in 0 until size) {
            for (to in 0 until size) {
                val repository = FakePinnedToolsRepository(firstIds(size))
                val viewModel = viewModel(repository)
                advanceUntilIdle()

                viewModel.onIntent(WidgetSettingsIntent.Reorder(from, to))
                advanceUntilIdle()

                val expected = firstIds(size).toMutableList()
                    .apply { if (from != to) add(to, removeAt(from)) }

                assertEquals("from=$from to=$to", expected, viewModel.state.value.pinnedIds())
            }
        }
    }

    @Test
    fun `reorder out of range is a no-op and does not persist`() = runTest {
        val repository = FakePinnedToolsRepository(firstIds(3))
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        val writesBefore = repository.writes

        listOf(-1 to 0, 0 to 3, 5 to 1, 0 to 0).forEach { (from, to) ->
            viewModel.onIntent(WidgetSettingsIntent.Reorder(from, to))
        }
        advanceUntilIdle()

        assertEquals(firstIds(3), viewModel.state.value.pinnedIds())
        assertEquals(writesBefore, repository.writes)
    }

    @Test
    fun `each accepted mutation persists exactly once`() = runTest {
        val repository = FakePinnedToolsRepository(firstIds(2))
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        val writesBefore = repository.writes

        viewModel.onIntent(WidgetSettingsIntent.ToggleTool(catalogIds[3]))
        advanceUntilIdle()
        assertEquals(writesBefore + 1, repository.writes)

        viewModel.onIntent(WidgetSettingsIntent.Reorder(0, 1))
        advanceUntilIdle()
        assertEquals(writesBefore + 2, repository.writes)
    }

    @Test
    fun `an unavailable pinned tool stays in the pinned block, not the catalog block`() = runTest {
        val withheld = catalogIds[1]
        val repository = FakePinnedToolsRepository(firstIds(3))
        val viewModel = viewModel(repository, FeatureFlags { it != withheld })
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(withheld in state.pinnedIds())
        assertFalse(withheld in state.unpinned.map { it.id })
        assertFalse(state.pinned.single { it.descriptor.id == withheld }.isAvailable)
    }

    @Test
    fun `unpinning an unavailable tool frees its slot`() = runTest {
        val withheld = catalogIds[0]
        val repository = FakePinnedToolsRepository(firstIds(MAX_PINNED_TOOLS))
        val viewModel = viewModel(repository, FeatureFlags { it != withheld })
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isAtCap)

        viewModel.onIntent(WidgetSettingsIntent.ToggleTool(withheld))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isAtCap)
        assertFalse(withheld in viewModel.state.value.pinnedIds())
    }

    private fun viewModel(
        repository: FakePinnedToolsRepository,
        flags: FeatureFlags = FeatureFlags { true },
    ) = WidgetSettingsViewModel(
        pinnedTools = repository,
        pinRequester = FakePinRequester(),
        featureFlags = object : FeatureFlagRepository {
            override fun flags(): Flow<FeatureFlags> = flowOf(flags)
        },
    )

    private fun WidgetSettingsState.pinnedIds(): List<String> = pinned.map { it.descriptor.id }

    /** Reports unsupported, so nothing here depends on a launcher that does not exist in a test. */
    private class FakePinRequester : WidgetPinRequester {
        override val isSupported: Boolean = false
        override fun requestPin() = Unit
    }

    private companion object {
        val catalogIds: List<String> = ToolCatalog.entries.map(ToolDescriptor::id)

        fun firstIds(count: Int): List<String> = catalogIds.take(count)
    }
}
