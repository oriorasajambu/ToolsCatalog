package com.minion.scaffold.feature.speedometer.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.SpeedometerRoute
import com.minion.scaffold.core.navigation.SpeedometerSettingsRoute
import com.minion.scaffold.feature.speedometer.presentation.settings.SpeedometerSettingsScreen

/**
 * This module's only public surface.
 *
 * Navigation arrives as lambdas rather than a `NavHostController`: `:app` is the only module allowed
 * to hold one, so this module knows the route contract exists without knowing which feature serves
 * it.
 */
fun NavGraphBuilder.speedometerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    composable<SpeedometerRoute> {
        SpeedometerScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToSettings = onNavigateToSettings,
        )
    }
}

/**
 * Units, coordinate format, and what the numbers mean.
 *
 * A destination rather than controls on the main screen: three selectors on a display meant to be
 * read at a glance from a car mount is three too many, and the accuracy explanations — particularly
 * why this disagrees with a car dashboard — need somewhere to live that is not the speedometer.
 */
fun NavGraphBuilder.speedometerSettingsScreen(
    onNavigateBack: () -> Unit,
) {
    composable<SpeedometerSettingsRoute> {
        SpeedometerSettingsScreen(onNavigateBack = onNavigateBack)
    }
}
