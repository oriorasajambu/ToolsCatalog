package com.minion.scaffold.feature.weather.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.WeatherDetailRoute
import com.minion.scaffold.core.navigation.WeatherRoute
import com.minion.scaffold.feature.weather.presentation.detail.ForecastDetailScreen
import com.minion.scaffold.feature.weather.presentation.home.WeatherHomeScreen

/**
 * This module's only public surface. Registers both of this slice's screens — home (which also
 * owns the location-permission gate, SPEC.md §7.1/§7.2) and the forecast detail — so `:app` sees
 * one entry point for the whole feature, same as every other feature's `NavGraphBuilder` extension.
 *
 * [onNavigateToDetail] rather than the home screen navigating directly: `:app` is the only module
 * that is allowed to hold a `NavHostController` (see `AppNavHost`'s doc comment) — this hands back
 * a location id and lets the host decide what "open detail" means.
 */
fun NavGraphBuilder.weatherScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (WeatherDetailRoute) -> Unit,
) {
    composable<WeatherRoute> {
        WeatherHomeScreen(
            onNavigateBack = onNavigateBack,
            onOpenDetail = { locationId -> onNavigateToDetail(WeatherDetailRoute(locationId)) },
        )
    }
    composable<WeatherDetailRoute> {
        ForecastDetailScreen(onNavigateBack = onNavigateBack)
    }
}
