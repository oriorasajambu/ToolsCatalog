package com.minion.scaffold.feature.widget

import com.minion.scaffold.core.data.widget.PinnedToolsRepository
import com.minion.scaffold.core.domain.featureflag.FeatureFlagRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * How the widget reaches the dependency graph.
 *
 * `GlanceAppWidgetReceiver` is instantiated by the framework, so it cannot be `@AndroidEntryPoint`
 * and nothing can be injected into it. `EntryPointAccessors.fromApplication(context)` is the
 * supported way back in, and this interface is the whole of what the widget needs.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {

    /** The pinned list. Read-only from here — only the app process writes it. */
    fun pinnedToolsRepository(): PinnedToolsRepository

    /** The switches, so a withheld tool draws greyed rather than opening something absent. */
    fun featureFlagRepository(): FeatureFlagRepository

    /** Supplied by `:app`, the only module that can name the activity a tile opens. */
    fun widgetLaunchIntentFactory(): WidgetLaunchIntentFactory
}
