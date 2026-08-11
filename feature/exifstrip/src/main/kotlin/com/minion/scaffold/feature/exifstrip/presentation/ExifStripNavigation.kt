package com.minion.scaffold.feature.exifstrip.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.ExifStripRoute
import com.minion.scaffold.core.navigation.ExifStripSettingsRoute
import com.minion.scaffold.feature.exifstrip.presentation.settings.ExifStripSettingsScreen

/**
 * This module's only public surface.
 *
 * Navigation arrives as lambdas rather than a `NavHostController`: `:app` is the only module allowed
 * to hold one, so this module knows the route contract exists without knowing which feature serves
 * it.
 *
 * @receiver The nav graph builder to register the destination on.
 * @param onNavigateBack       Called when the user leaves the stripper.
 * @param onNavigateToSettings Called when the user opens the stripper's settings.
 */
fun NavGraphBuilder.exifStripScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    composable<ExifStripRoute> {
        ExifStripScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToSettings = onNavigateToSettings,
        )
    }
}

/**
 * The colour-profile choice, and the boundary of what this tool can promise.
 *
 * A destination rather than a panel because the second half needs room: the statement of what is
 * *not* removed — anything hidden in the pixels themselves — matters more than the toggle, and a
 * privacy tool that implies a completeness it does not have is worse than one that draws its line
 * clearly.
 *
 * @receiver The nav graph builder to register the destination on.
 * @param onNavigateBack Called when the user leaves the settings screen.
 */
fun NavGraphBuilder.exifStripSettingsScreen(
    onNavigateBack: () -> Unit,
) {
    composable<ExifStripSettingsRoute> {
        ExifStripSettingsScreen(onNavigateBack = onNavigateBack)
    }
}
