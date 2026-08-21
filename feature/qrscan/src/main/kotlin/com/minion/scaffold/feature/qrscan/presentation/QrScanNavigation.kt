package com.minion.scaffold.feature.qrscan.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.designsystem.motion.slideDownExit
import com.minion.scaffold.core.designsystem.motion.slideUpEnter
import com.minion.scaffold.core.navigation.AppRoute
import com.minion.scaffold.core.navigation.QrScanRoute
import com.minion.scaffold.core.navigation.QrScanSettingsRoute
import com.minion.scaffold.feature.qrscan.presentation.settings.QrScanSettingsScreen

/**
 * This module's entire public surface.
 *
 * One registration for both scan purposes. Which one a destination was opened with is a route
 * argument the ViewModel reads for itself, so the screen and its wiring stay identical either way.
 *
 * @receiver The nav graph builder to register the destination on.
 * @param onNavigateBack Called when the user leaves the scanner.
 * @param onEditPayload  The route for the payload the user wants to change, handed onward. Where it
 *   goes is the host's business — this module does not know an editor exists.
 * @param onNavigateToSettings The schema settings route, carrying the scanned payload when the user
 *   arrived from a report so the placeholder reference can resolve against it.
 */
fun NavGraphBuilder.qrScanScreen(
    onNavigateBack: () -> Unit,
    onEditPayload: (AppRoute) -> Unit,
    onNavigateToSettings: (QrScanSettingsRoute) -> Unit,
) {
    // The camera is the one modal in the app: it rises from the bottom to open and drops back down
    // to close, overriding the host's default right-to-left push. Only the two directions that move
    // the camera itself are set — enter on open, pop-exit on close; being covered by or revealed
    // beneath another screen inherits the host default of holding still.
    composable<QrScanRoute>(
        enterTransition = { slideUpEnter() },
        popExitTransition = { slideDownExit() },
    ) {
        QrScanScreen(
            onNavigateBack = onNavigateBack,
            onEditPayload = onEditPayload,
            onNavigateToSettings = onNavigateToSettings,
        )
    }
}

/**
 * The schema settings, which is also the placeholder reference.
 *
 * A push rather than the scanner's modal rise: it is a screen you go to and come back from, not a
 * camera that covers the app.
 *
 * @receiver The nav graph builder to register the destination on.
 * @param onNavigateBack Called when the user leaves the settings.
 */
fun NavGraphBuilder.qrScanSettingsScreen(
    onNavigateBack: () -> Unit,
) {
    composable<QrScanSettingsRoute> {
        QrScanSettingsScreen(onNavigateBack = onNavigateBack)
    }
}
