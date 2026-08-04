package com.minion.scaffold.feature.weather.di

import com.google.gson.Gson
import com.minion.scaffold.feature.weather.data.remote.WeatherApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/**
 * A second [Retrofit] instance, pointed at Open-Meteo rather than `:app`'s configured `BASE_URL`.
 *
 * SPEC.md §3 calls for two base URLs (forecast host, geocoding host — only the forecast host is
 * wired for this vertical slice). No secrets or `keystore.properties`-style config are involved
 * (Open-Meteo needs no API key), so the host is a plain constant here rather than something `:app`
 * has to provide via `@BaseUrl` — that qualifier is reserved for the app's own configured backend.
 * The [OkHttpClient] and [Gson] are still the shared singletons from `:core:network`, so this
 * reuses the one connection pool and logging/Chucker setup rather than duplicating them.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object WeatherNetworkModule {

    private const val OPEN_METEO_BASE_URL = "https://api.open-meteo.com/"

    @Provides
    @Singleton
    fun provideWeatherApi(client: OkHttpClient, gson: Gson): WeatherApi = Retrofit.Builder()
        .baseUrl(OPEN_METEO_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(WeatherApi::class.java)
}
