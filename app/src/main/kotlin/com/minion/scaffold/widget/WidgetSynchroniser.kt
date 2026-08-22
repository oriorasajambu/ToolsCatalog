package com.minion.scaffold.widget

import com.minion.scaffold.core.data.widget.PinnedToolsRepository
import com.minion.scaffold.core.data.widget.reconcilePinnedTools
import com.minion.scaffold.core.domain.featureflag.FeatureFlagRepository
import com.minion.scaffold.core.toolcatalog.ToolCatalog
import com.minion.scaffold.feature.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
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
 *  - **Grey and ungrey tiles as the console flips a flag.** A new `FeatureFlags` snapshot changes
 *    what a tile should look like, and nothing else would notice.
 *  - **Write back a pruned list.** The widget reconciles on every render but never writes — that
 *    is the single-writer rule. The write has to happen somewhere, and this is the app side.
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
        featureFlags.flags()
            .onEach { flags ->
                val stored = pinnedTools.currentPinnedIds()
                val reconciled = reconcilePinnedTools(stored, ToolCatalog.entries, flags)

                // Only when the reconcile actually changed something. Writing an identical list
                // would re-emit the store's flow and redraw for nothing, on every flag snapshot.
                if (reconciled.retainedIds != stored) {
                    pinnedTools.setPinned(reconciled.retainedIds)
                }

                widgetUpdater.updateAll()
            }
            .distinctUntilChanged()
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
