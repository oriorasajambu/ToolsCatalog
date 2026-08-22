package com.minion.scaffold.widget

import com.minion.scaffold.core.data.widget.PinnedToolsRepository
import com.minion.scaffold.core.data.widget.reconcilePinnedTools
import com.minion.scaffold.core.domain.featureflag.FeatureFlagRepository
import com.minion.scaffold.core.toolcatalog.ToolCatalog
import com.minion.scaffold.feature.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the widget honest about things the widget itself cannot see.
 *
 * Two jobs, both application-scoped rather than screen-scoped:
 *
 *  - **Redraw whenever what the widget should show changes.** That is either half of the pair it
 *    renders from: a new `FeatureFlags` snapshot greys or ungreys a tile, and a new pinned list
 *    means the user just changed the arrangement on the configuration screen.
 *  - **Write back a pruned list.** The widget reconciles on every render but never writes — that
 *    is the single-writer rule. The write has to happen somewhere, and this is the app side.
 *
 * Collecting the pinned list here rather than calling `updateAll` from the configuration screen's
 * ViewModel is what keeps `:feature:tools` from needing to know a widget exists at all. It also
 * means any future writer gets the redraw for free instead of having to remember it.
 *
 * **Deliberately not in `ToolsViewModel`.** It already collects the same flow, and putting this
 * there would make the widget's correctness depend on whether the user happened to visit the home
 * screen.
 */
@Singleton
class WidgetSynchroniser @Inject constructor(
    private val pinnedTools: PinnedToolsRepository,
    private val featureFlags: FeatureFlagRepository,
    private val widgetUpdater: WidgetUpdater,
) {

    /**
     * Starts collecting. Called once, from the application.
     *
     * @param scope An application-lifetime scope. The collection is meant to outlive every screen.
     */
    fun start(scope: CoroutineScope) {
        combine(pinnedTools.pinnedIds, featureFlags.flags(), ::Pair)
            .onEach { (stored, flags) ->
                val reconciled = reconcilePinnedTools(stored, ToolCatalog.entries, flags)

                // Only when the reconcile actually changed something. Writing an identical list
                // re-emits the store's flow, which would arrive back here and write again — the
                // check is what makes that converge instead of looping.
                if (reconciled.retainedIds != stored) {
                    pinnedTools.setPinned(reconciled.retainedIds)
                    return@onEach
                }

                widgetUpdater.updateAll()
            }
            .launchIn(scope)
    }

    /**
     * Redraws after a configuration change.
     *
     * The emulator run that verified step 4 found this: below API 31 Glance resolves colours into
     * the `RemoteViews` when it renders, so a widget already on the home screen keeps its old
     * palette through a light/dark switch — the app recomposes, the widget does not. SPEC.md §7
     * left theme changes out on the assumption the platform repaints; it does not, at least not
     * here.
     *
     * Not a manifest receiver, because `ACTION_CONFIGURATION_CHANGED` cannot be declared in one —
     * the system only delivers it to registered receivers. The application is the longest-lived
     * thing that can hear it.
     *
     * @param scope An application-lifetime scope to render on.
     */
    fun onConfigurationChanged(scope: CoroutineScope) {
        scope.launch { widgetUpdater.updateAll() }
    }
}
