package com.minion.scaffold.feature.weather.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.WeatherDetailRoute
import com.minion.scaffold.core.navigation.WeatherRoute
import com.minion.scaffold.core.navigation.WeatherSearchRoute
import com.minion.scaffold.core.navigation.WeatherSettingsRoute
import com.minion.scaffold.feature.weather.presentation.detail.ForecastDetailScreen
import com.minion.scaffold.feature.weather.presentation.home.WeatherHomeScreen
import com.minion.scaffold.feature.weather.presentation.search.LocationSearchScreen
import com.minion.scaffold.feature.weather.presentation.settings.WeatherSettingsScreen

/**
 * This module's only public surface. Registers all four of the feature's screens — home (which
 * also owns the location-permission gate, SPEC.md §7.1/§7.2), forecast detail, location search and
 * settings — so `:app` sees one entry point for the whole feature, same as every other feature's
 * `NavGraphBuilder` extension.
 *
 * The navigation lambdas take route objects rather than the screens navigating directly: `:app` is
 * the only module allowed to hold a `NavHostController` (see `AppNavHost`'s doc comment), so this
 * hands back a destination and lets the host decide what reaching it means.
 *
 * @receiver The nav graph builder to register the destinations on.
 * @param onNavigateBack       Called when the user leaves a weather screen.
 * @param onNavigateToDetail   Called with the route for a location's forecast detail.
 * @param onNavigateToSearch   Called when the user opens place-name search.
 * @param onNavigateToSettings Called when the user opens the weather settings.
 */
fun NavGraphBuilder.weatherScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (WeatherDetailRoute) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    composable<WeatherRoute> {
        WeatherHomeScreen(
            onNavigateBack = onNavigateBack,
            onOpenDetail = { locationId -> onNavigateToDetail(WeatherDetailRoute(locationId)) },
            onOpenSearch = onNavigateToSearch,
            onOpenSettings = onNavigateToSettings,
        )
    }
    composable<WeatherDetailRoute> {
        ForecastDetailScreen(onNavigateBack = onNavigateBack)
    }
    composable<WeatherSearchRoute> {
        LocationSearchScreen(onNavigateBack = onNavigateBack)
    }
    composable<WeatherSettingsRoute> {
        WeatherSettingsScreen(onNavigateBack = onNavigateBack)
    }
}
