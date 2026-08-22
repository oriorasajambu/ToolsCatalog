package com.minion.scaffold.feature.tools.presentation.widget

import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.WidgetSettingsRoute

/**
 * The widget configuration screen's entry point.
 *
 * A sub-screen of this feature with its own route, contract and ViewModel, registered by this
 * feature's own navigation — the same shape `qrScanSettingsScreen` and `ocrSettingsScreen` follow,
 * rather than a mode flag on the home screen.
 *
 * @receiver The nav graph builder to register the destination on.
 * @param onNavigateBack Leaves the screen.
 */
fun NavGraphBuilder.widgetSettingsScreen(onNavigateBack: () -> Unit) {
    composable<WidgetSettingsRoute> {
        val viewModel: WidgetSettingsViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()

        WidgetSettingsScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onNavigateBack = onNavigateBack,
            canPinToHome = state.canPinToHome,
        )
    }
}
