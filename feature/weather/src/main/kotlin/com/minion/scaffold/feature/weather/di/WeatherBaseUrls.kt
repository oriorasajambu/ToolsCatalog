package com.minion.scaffold.feature.weather.di

import javax.inject.Qualifier

/**
 * One qualifier per Open-Meteo host (SPEC.md §2), so each base URL is an injected value rather
 * than a literal buried in a `Retrofit.Builder` call — the same reasoning as `:core:network`'s
 * `@BaseUrl`, applied to the two hosts this feature talks to.
 *
 * Provided by this feature rather than by `:app`, which is where `@BaseUrl` itself comes from.
 * That one has to come from the app because it differs per build flavor and is read from
 * `BuildConfig`; these do not. Open-Meteo is a fixed public API with no dev/prod split, so routing
 * them through `:app` would only teach the application module about hosts that are this feature's
 * business — and `:app` would have to be edited every time a feature added an endpoint.
 */
@Retention(AnnotationRetention.BINARY)
@Qualifier
internal annotation class ForecastBaseUrl

/** See [ForecastBaseUrl]. Open-Meteo serves geocoding from a genuinely different host. */
@Retention(AnnotationRetention.BINARY)
@Qualifier
internal annotation class GeocodingBaseUrl
