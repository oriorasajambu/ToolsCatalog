package com.minion.scaffold.feature.widget.di

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.minion.scaffold.core.data.widget.PinnedToolsRepository
import com.minion.scaffold.core.data.widget.WidgetPinRequester
import com.minion.scaffold.feature.widget.WidgetUpdater
import com.minion.scaffold.feature.widget.data.local.WidgetPreferencesDataStore
import com.minion.scaffold.feature.widget.glance.GlanceWidgetUpdater
import com.minion.scaffold.feature.widget.glance.QuickAccessWidgetReceiver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** This feature's bindings, `internal` and living beside the implementation they bind. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetModule {

    /**
     * `@Singleton` is load-bearing here beyond the usual reasons: DataStore throws if a second
     * instance is created for the same file within one process, and this store is read from both
     * the app and the widget's own broadcast receivers.
     *
     * @param impl The DataStore-backed implementation.
     * @return The [PinnedToolsRepository] binding.
     */
    @Binds
    @Singleton
    abstract fun bindPinnedToolsRepository(impl: WidgetPreferencesDataStore): PinnedToolsRepository

    /**
     * @param impl The Glance-backed implementation.
     * @return The [WidgetUpdater] binding `:app` asks for a redraw through.
     */
    @Binds
    abstract fun bindWidgetUpdater(impl: GlanceWidgetUpdater): WidgetUpdater

    /**
     * Pin-to-home lives here rather than in `:app`, unlike the launch-intent factory.
     *
     * The difference is what each has to name. The intent factory names `MainActivity`, which only
     * `:app` can see; this names the widget provider, which is this module's own component — so
     * binding it here keeps the receiver `internal` instead of widening it for one caller.
     *
     * @param context The application context, for the widget manager and the component name.
     * @return A requester bound to this app's own widget provider.
     */
    @Module
    @InstallIn(SingletonComponent::class)
    internal object PinProvider {

        @Provides
        @Singleton
        fun provideWidgetPinRequester(
            @ApplicationContext context: Context,
        ): WidgetPinRequester = object : WidgetPinRequester {

            private val manager = AppWidgetManager.getInstance(context)
            private val provider = ComponentName(context, QuickAccessWidgetReceiver::class.java)

            // Launcher-dependent rather than merely version-dependent: launchers on API levels
            // that support the call still report false, which is why the screen asks.
            override val isSupported: Boolean get() = manager.isRequestPinAppWidgetSupported

            override fun requestPin() {
                if (!isSupported) return
                manager.requestPinAppWidget(provider, null, null)
            }
        }
    }
}
