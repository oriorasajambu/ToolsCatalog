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
 * SPEC.md §2 calls for one `@BaseUrl`-style qualifier per host, and they are genuinely different
 * hosts — forecasts come from `api.open-meteo.com`, place-name search from
 * `geocoding-api.open-meteo.com` — so one Retrofit cannot serve both. No secrets or
 * `keystore.properties`-style config are involved; Open-Meteo needs no API key.
 *
 * The [OkHttpClient] and [Gson] are still the shared singletons from `:core:network`, so both of
 * these reuse the one connection pool and logging/Chucker setup rather than duplicating them.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object WeatherNetworkModule {

    @Provides
    @ForecastBaseUrl
    fun provideForecastBaseUrl(): String = "https://api.open-meteo.com/"

    @Provides
    @GeocodingBaseUrl
    fun provideGeocodingBaseUrl(): String = "https://geocoding-api.open-meteo.com/"

    @Provides
    @Singleton
    fun provideWeatherApi(
        @ForecastBaseUrl baseUrl: String,
        client: OkHttpClient,
        gson: Gson,
    ): WeatherApi = retrofit(baseUrl, client, gson).create(WeatherApi::class.java)

    @Provides
    @Singleton
    fun provideGeocodingApi(
        @GeocodingBaseUrl baseUrl: String,
        client: OkHttpClient,
        gson: Gson,
    ): GeocodingApi = retrofit(baseUrl, client, gson).create(GeocodingApi::class.java)

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
