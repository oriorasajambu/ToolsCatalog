package com.minion.scaffold.feature.qrcreate.presentation.vcard

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.VCardCreateRoute

/** The contact-card half of this module's public surface. */
fun NavGraphBuilder.vCardCreateScreen(
    onNavigateBack: () -> Unit,
) {
    composable<VCardCreateRoute> {
        VCardCreateScreen(onNavigateBack = onNavigateBack)
    }
}
