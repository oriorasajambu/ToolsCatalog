package com.minion.scaffold.feature.level.di

import com.minion.scaffold.feature.level.data.AndroidGravitySource
import com.minion.scaffold.feature.level.data.local.LevelPreferencesDataStore
import com.minion.scaffold.feature.level.domain.GravitySource
import com.minion.scaffold.feature.level.domain.LevelPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** This feature's bindings, `internal` and living beside the implementations they bind. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class LevelModule {

    /**
     * `@Singleton` because the sensor lookup happens once at construction, and because
     * [GravitySource.sensor] is read by the UI to explain which stream is running — two instances
     * disagreeing about that would be quietly confusing.
     *
     * @param impl The Android sensor-backed implementation.
     * @return The [GravitySource] binding.
     */
    @Binds
    @Singleton
    abstract fun bindGravitySource(impl: AndroidGravitySource): GravitySource

    /**
     * `@Singleton` matters beyond the usual reasons: DataStore throws if a second instance is
     * created for the same file within one process, and a non-scoped binding would build a new
     * wrapper — though not a new `DataStore`, since the delegate caches per `Context` — on every
     * injection.
     *
     * @param impl The DataStore-backed implementation.
     * @return The [LevelPreferencesRepository] binding.
     */
    @Binds
    @Singleton
    abstract fun bindLevelPreferencesRepository(
        impl: LevelPreferencesDataStore,
    ): LevelPreferencesRepository
}
