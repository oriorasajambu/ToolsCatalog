package com.minion.scaffold.feature.qrcreate.presentation.wifi

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.WifiCreateRoute

/**
 * The Wi-Fi half of this module's public surface.
 *
 * @receiver The nav graph builder to register the destination on.
 * @param onNavigateBack Called when the user leaves the screen.
 */
fun NavGraphBuilder.wifiCreateScreen(
    onNavigateBack: () -> Unit,
) {
    composable<WifiCreateRoute> {
        WifiCreateScreen(onNavigateBack = onNavigateBack)
    }
}
