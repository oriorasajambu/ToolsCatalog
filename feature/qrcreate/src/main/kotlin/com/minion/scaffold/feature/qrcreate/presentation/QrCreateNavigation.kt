package com.minion.scaffold.feature.qrcreate.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.QrCreateRoute

/**
 * The EMV authoring destination.
 *
 * @receiver The nav graph builder to register the destination on.
 * @param onNavigateBack Called when the user leaves the screen.
 */
fun NavGraphBuilder.qrCreateScreen(
    onNavigateBack: () -> Unit,
) {
    composable<QrCreateRoute> {
        QrCreateScreen(onNavigateBack = onNavigateBack)
    }
}
