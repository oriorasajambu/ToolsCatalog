package com.minion.scaffold.feature.texttools.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.GenerateRoute
import com.minion.scaffold.core.navigation.TextToolsRoute
import com.minion.scaffold.feature.texttools.presentation.generate.GenerateScreen
import com.minion.scaffold.feature.texttools.presentation.transform.TextToolsScreen

/**
 * The transform half of this module's public surface.
 *
 * @receiver The nav graph builder to register the destination on.
 * @param onNavigateBack Called when the user leaves the screen.
 */
fun NavGraphBuilder.textToolsScreen(
    onNavigateBack: () -> Unit,
) {
    composable<TextToolsRoute> {
        TextToolsScreen(onNavigateBack = onNavigateBack)
    }
}

/**
 * The generator half.
 *
 * @receiver The nav graph builder to register the destination on.
 * @param onNavigateBack Called when the user leaves the screen.
 */
fun NavGraphBuilder.generateScreen(
    onNavigateBack: () -> Unit,
) {
    composable<GenerateRoute> {
        GenerateScreen(onNavigateBack = onNavigateBack)
    }
}
