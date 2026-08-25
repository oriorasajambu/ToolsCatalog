package com.minion.scaffold.feature.checksum.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.ChecksumRoute

/**
 * This module's entire public surface.
 *
 * `:app` registers the destination and passes navigation in as a lambda; the screen, the ViewModel
 * and the contract behind it stay `internal`, which is what makes the module boundary real rather
 * than decorative.
 *
 * @receiver The nav graph builder to register the destination on.
 * @param onNavigateBack Called when the user leaves the screen.
 */
fun NavGraphBuilder.checksumScreen(
    onNavigateBack: () -> Unit,
) {
    composable<ChecksumRoute> {
        ChecksumScreen(onNavigateBack = onNavigateBack)
    }
}
