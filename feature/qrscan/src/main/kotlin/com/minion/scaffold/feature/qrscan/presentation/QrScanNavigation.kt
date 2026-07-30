package com.minion.scaffold.feature.qrscan.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.AppRoute
import com.minion.scaffold.core.navigation.QrScanRoute

/**
 * This module's entire public surface.
 *
 * One registration for both scan purposes. Which one a destination was opened with is a route
 * argument the ViewModel reads for itself, so the screen and its wiring stay identical either way.
 *
 * @param onEditPayload the payload the user wants to change, handed onward. Where it goes is the
 *   host's business — this module does not know an editor exists.
 */
fun NavGraphBuilder.qrScanScreen(
    onNavigateBack: () -> Unit,
    onEditPayload: (AppRoute) -> Unit,
) {
    composable<QrScanRoute> {
        QrScanScreen(
            onNavigateBack = onNavigateBack,
            onEditPayload = onEditPayload,
        )
    }
}
