package com.minion.scaffold.feature.tools.presentation.widget

import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.data.widget.MAX_PINNED_TOOLS
import com.minion.scaffold.core.data.widget.PinnedToolsRepository
import com.minion.scaffold.core.data.widget.WidgetPinRequester
import com.minion.scaffold.core.data.widget.reconcilePinnedTools
import com.minion.scaffold.core.domain.featureflag.FeatureFlagRepository
import com.minion.scaffold.core.toolcatalog.ToolCatalog
import com.minion.scaffold.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The widget configuration screen.
 *
 * Every mutation goes the same way round: change the list, persist it, and let the store's own
 * stream bring the new value back as state. Reducing locally *and* writing would give the screen
 * two sources for one list, and they would disagree the moment a write failed.
 *
 * The pinned block is built by the same `reconcilePinnedTools` the widget renders through, so a
 * tool the console is withholding appears here exactly as it appears there — present, holding its
 * slot, marked unavailable.
 */
@HiltViewModel
internal class WidgetSettingsViewModel @Inject constructor(
    private val pinnedTools: PinnedToolsRepository,
    private val pinRequester: WidgetPinRequester,
    featureFlags: FeatureFlagRepository,
) : MviViewModel<WidgetSettingsState, WidgetSettingsIntent, WidgetSettingsEffect>(
    WidgetSettingsState(),
) {

    init {
        combine(pinnedTools.pinnedIds, featureFlags.flags()) { ids, flags ->
            reconcilePinnedTools(ids, ToolCatalog.entries, flags)
        }
            .onEach { reconciled ->
                reduce {
                    copy(
                        pinned = reconciled.tools,
                        catalog = ToolCatalog.entries,
                        canPinToHome = pinRequester.isSupported,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: WidgetSettingsIntent) {
        when (intent) {
            is WidgetSettingsIntent.ToggleTool -> toggle(intent.toolId)
            is WidgetSettingsIntent.Reorder -> reorder(intent.from, intent.to)
            WidgetSettingsIntent.PinWidgetRequested -> requestPin()
        }
    }

    /**
     * Pins a tool, or unpins one already pinned.
     *
     * At the cap, pinning is a no-op rather than an eviction. FIFO eviction was rejected because
     * "oldest" is invisible in a checkbox list: a user reordering their picks could lose one
     * without noticing it had gone.
     */
    private fun toggle(toolId: String) {
        val current = currentState.pinned.map { it.descriptor.id }

        val next = when {
            toolId in current -> current - toolId
            current.size >= MAX_PINNED_TOOLS -> return
            else -> current + toolId
        }

        persist(next)
    }

    /**
     * Moves a pinned tool from one position to another.
     *
     * Out-of-range indices are a no-op. The gesture layer computes these from pointer positions
     * and can produce one at the edges of a drag; refusing it here means the drag code never has
     * to be the thing that gets bounds right.
     */
    private fun reorder(from: Int, to: Int) {
        val current = currentState.pinned.map { it.descriptor.id }
        if (from !in current.indices || to !in current.indices || from == to) return

        val next = current.toMutableList().apply { add(to, removeAt(from)) }
        persist(next)
    }

    private fun persist(ids: List<String>) {
        viewModelScope.launch { pinnedTools.setPinned(ids) }
    }

    /** Straight through: the dialog is the launcher's, and this screen does not change. */
    private fun requestPin() = pinRequester.requestPin()
}
