package com.minion.scaffold.feature.weather.di

import com.google.gson.Gson
import com.minion.scaffold.feature.weather.data.remote.GeocodingApi
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
 * Two [Retrofit] instances, pointed at Open-Meteo rather than `:app`'s configured `BASE_URL`.
 *
 * SPEC.md §3 calls for two base URLs, and they are genuinely different hosts — forecasts come from
 * `api.open-meteo.com`, place-name search from `geocoding-api.open-meteo.com` — so one Retrofit
 * cannot serve both. No secrets or `keystore.properties`-style config are involved (Open-Meteo
 * needs no API key), so the hosts are plain constants here rather than something `:app` has to
 * provide via `@BaseUrl` — that qualifier is reserved for the app's own configured backend.
 *
 * The [OkHttpClient] and [Gson] are still the shared singletons from `:core:network`, so both of
 * these reuse the one connection pool and logging/Chucker setup rather than duplicating them.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object WeatherNetworkModule {

    private const val FORECAST_BASE_URL = "https://api.open-meteo.com/"
    private const val GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/"

    @Provides
    @Singleton
    fun provideWeatherApi(client: OkHttpClient, gson: Gson): WeatherApi =
        retrofit(FORECAST_BASE_URL, client, gson).create(WeatherApi::class.java)

    @Provides
    @Singleton
    fun provideGeocodingApi(client: OkHttpClient, gson: Gson): GeocodingApi =
        retrofit(GEOCODING_BASE_URL, client, gson).create(GeocodingApi::class.java)

    /**
     * Deliberately a private helper rather than a `@Provides Retrofit`: providing it would put a
     * second unqualified `Retrofit` into the graph alongside `:core:network`'s, and Hilt would
     * reject the duplicate binding.
     */
    private fun retrofit(baseUrl: String, client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
}
