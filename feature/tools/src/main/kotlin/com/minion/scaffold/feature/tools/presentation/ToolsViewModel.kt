package com.minion.scaffold.feature.tools.presentation

import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.domain.featureflag.FeatureFlagRepository
import com.minion.scaffold.core.domain.featureflag.FeatureFlags
import com.minion.scaffold.core.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Filters the tool catalog by the remote switches, and turns a tap into a navigation effect.
 *
 * The screen used to be stateless and read [ToolCatalog] directly, which was right while the list
 * was a compile-time constant. It stopped being one when the catalog became something the Firebase
 * console can withhold entries from, so the list is state now and this is what owns it.
 *
 * The ViewModel knows nothing about Firebase. It depends on [FeatureFlagRepository] in
 * `:core:domain`; the Remote Config implementation is bound in `:app`, which is the only module
 * that may know which Firebase project this app belongs to.
 */
@HiltViewModel
internal class ToolsViewModel @Inject constructor(
    featureFlags: FeatureFlagRepository,
) : MviViewModel<ToolsState, ToolsIntent, ToolsEffect>(ToolsState()) {

    init {
        // Collected for the ViewModel's lifetime rather than per composition: the flow's first
        // value is synchronous (the activated or default configuration) and its second, if any,
        // follows a network fetch that should not be restarted every time the screen is recreated.
        featureFlags.flags()
            .onEach { flags -> reduce { copy(tools = ToolCatalog.entries.enabledBy(flags)) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: ToolsIntent) {
        when (intent) {
            is ToolsIntent.ToolSelected -> viewModelScope.launch {
                emitEffect(ToolsEffect.OpenTool(intent.tool.route))
            }
        }
    }
}

/**
 * The tools [flags] currently allows, in catalog order.
 *
 * Filtering the catalog rather than asking the configuration what exists is what keeps the switches
 * fail-open at the list level too: a console that has never heard of a tool cannot remove it, it
 * can only fail to hide it.
 *
 * @receiver The full catalog.
 * @param flags The configuration snapshot to filter against.
 * @return The subset the user may be offered.
 */
private fun List<Tool>.enabledBy(flags: FeatureFlags): List<Tool> =
    filter { flags.isEnabled(it.id) }
