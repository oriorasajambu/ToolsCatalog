package com.minion.scaffold.feature.qrcreate.presentation.vcard

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.VCardCreateRoute

/**
 * The contact-card half of this module's public surface.
 *
 * @receiver The nav graph builder to register the destination on.
 * @param onNavigateBack Called when the user leaves the screen.
 */
fun NavGraphBuilder.vCardCreateScreen(
    onNavigateBack: () -> Unit,
) {
    composable<VCardCreateRoute> {
        VCardCreateScreen(onNavigateBack = onNavigateBack)
    }
}
