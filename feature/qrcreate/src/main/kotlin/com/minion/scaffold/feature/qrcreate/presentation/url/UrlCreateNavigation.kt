package com.minion.scaffold.feature.qrcreate.presentation.url

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.UrlCreateRoute

/** The link half of this module's public surface. */
fun NavGraphBuilder.urlCreateScreen(
    onNavigateBack: () -> Unit,
) {
    composable<UrlCreateRoute> {
        UrlCreateScreen(onNavigateBack = onNavigateBack)
    }
}
