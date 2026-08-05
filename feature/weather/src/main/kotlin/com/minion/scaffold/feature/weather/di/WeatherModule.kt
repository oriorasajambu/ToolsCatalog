package com.minion.scaffold.feature.weather.di

import com.minion.scaffold.feature.weather.data.local.WeatherPreferencesDataStore
import com.minion.scaffold.feature.weather.data.repository.WeatherRepositoryImpl
import com.minion.scaffold.feature.weather.domain.WeatherPreferencesRepository
import com.minion.scaffold.feature.weather.domain.WeatherRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WeatherModule {

    @Binds
    @Singleton
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository

    /**
     * `@Singleton` matters here beyond the usual reasons: DataStore throws if a second instance is
     * created for the same file within one process, and a non-scoped binding would build a new
     * wrapper — though not a new `DataStore`, since the delegate caches per `Context` — on every
     * injection.
     */
    @Binds
    @Singleton
    abstract fun bindWeatherPreferencesRepository(
        impl: WeatherPreferencesDataStore,
    ): WeatherPreferencesRepository
}
