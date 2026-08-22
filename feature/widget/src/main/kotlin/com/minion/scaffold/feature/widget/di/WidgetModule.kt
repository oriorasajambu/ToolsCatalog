package com.minion.scaffold.feature.widget.di

import com.minion.scaffold.core.data.widget.PinnedToolsRepository
import com.minion.scaffold.feature.widget.data.local.WidgetPreferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
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
}
