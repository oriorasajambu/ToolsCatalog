package com.minion.scaffold.di

import android.content.Context
import android.content.Intent
import com.minion.scaffold.MainActivity
import com.minion.scaffold.feature.widget.WidgetLaunchIntentFactory
import com.minion.scaffold.widget.WidgetLaunch
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the one thing the widget cannot supply for itself.
 *
 * `:feature:widget` declares [WidgetLaunchIntentFactory] but cannot implement it: naming
 * `MainActivity` would mean a feature module depending on `:app`. This is the same shape as
 * [FeatureFlagModule] — the feature owns the interface, `:app` owns the fact.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object WidgetLaunchModule {

    /**
     * An explicit component intent, never a deep link.
     *
     * A deep link would need `MainActivity` to accept an app-scheme VIEW intent, which is a public
     * surface any installed app could fire. Naming the component keeps the launch reachable only
     * from this app's own widget.
     *
     * `NEW_TASK` because a widget tap comes from the launcher's process, with no task of ours to
     * join. `SINGLE_TOP` so a second tap delivers through `onNewIntent` instead of stacking a
     * duplicate activity — this app has exactly one activity, so it is always the top of its own
     * task and the flag always applies.
     *
     * **The identifier is load-bearing, and its absence is silent.** A `PendingIntent` is matched
     * by `Intent.filterEquals`, which compares action, data, type, component, categories and
     * identifier — and *not* extras. Five tiles differing only by their tool-id extra are therefore
     * five equal intents, and the system hands every one of them the first `PendingIntent` it made:
     * every tile opens whichever tool happened to be drawn first, with nothing failing anywhere to
     * say so. `setIdentifier` exists for exactly this and has been available since API 29, which is
     * this app's minSdk.
     *
     * @param context The application context, for the component name.
     * @return A factory that builds the intent for a tool id, or for the tools home when null.
     */
    @Provides
    @Singleton
    fun provideWidgetLaunchIntentFactory(
        @ApplicationContext context: Context,
    ): WidgetLaunchIntentFactory = WidgetLaunchIntentFactory { toolId ->
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            identifier = toolId
            if (toolId != null) putExtra(WidgetLaunch.EXTRA_TOOL_ID, toolId)
        }
    }
}
