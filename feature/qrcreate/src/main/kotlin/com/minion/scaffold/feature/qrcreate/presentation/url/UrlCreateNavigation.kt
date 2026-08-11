package com.minion.scaffold.feature.qrcreate.presentation.url

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.UrlCreateRoute

/**
 * The link half of this module's public surface.
 *
 * @receiver The nav graph builder to register the destination on.
 * @param onNavigateBack Called when the user leaves the screen.
 */
fun NavGraphBuilder.urlCreateScreen(
    onNavigateBack: () -> Unit,
) {
    composable<UrlCreateRoute> {
        UrlCreateScreen(onNavigateBack = onNavigateBack)
    }
}
