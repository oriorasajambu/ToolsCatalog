package com.minion.scaffold.feature.qrcreate.presentation.wifi

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.WifiCreateRoute

/** The Wi-Fi half of this module's public surface. */
fun NavGraphBuilder.wifiCreateScreen(
    onNavigateBack: () -> Unit,
) {
    composable<WifiCreateRoute> {
        WifiCreateScreen(onNavigateBack = onNavigateBack)
    }
}
