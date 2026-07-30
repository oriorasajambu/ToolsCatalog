package com.minion.scaffold.feature.qrcreate.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.QrCreateRoute

/** This module's entire public surface. */
fun NavGraphBuilder.qrCreateScreen(
    onNavigateBack: () -> Unit,
) {
    composable<QrCreateRoute> {
        QrCreateScreen(onNavigateBack = onNavigateBack)
    }
}
