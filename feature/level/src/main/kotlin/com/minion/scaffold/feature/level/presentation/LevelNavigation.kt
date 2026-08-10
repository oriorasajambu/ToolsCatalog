package com.minion.scaffold.feature.level.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.LevelCalibrationRoute
import com.minion.scaffold.core.navigation.LevelRoute
import com.minion.scaffold.feature.level.presentation.calibration.CalibrationScreen

/**
 * This module's only public surface.
 *
 * Navigation arrives as lambdas rather than a `NavHostController`: `:app` is the only module allowed
 * to hold one — see `AppNavHost`'s doc comment — so this module knows the route contract exists
 * without knowing which feature serves it.
 */
fun NavGraphBuilder.levelScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCalibration: () -> Unit,
) {
    composable<LevelRoute> {
        LevelScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToCalibration = onNavigateToCalibration,
        )
    }
}

/**
 * The guided flip, a destination of its own.
 *
 * Separate from [levelScreen] rather than a dialog over it because the procedure needs the phone
 * left alone on a surface: a sheet the user has to reach past, or a layout that shifts under a
 * dialog, works against the one thing the flow is asking them to do.
 */
fun NavGraphBuilder.levelCalibrationScreen(
    onNavigateBack: () -> Unit,
) {
    composable<LevelCalibrationRoute> {
        CalibrationScreen(onNavigateBack = onNavigateBack)
    }
}
