package com.minion.scaffold.feature.ocr.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.OcrRoute
import com.minion.scaffold.core.navigation.OcrSettingsRoute
import com.minion.scaffold.core.navigation.TextToolsRoute
import com.minion.scaffold.feature.ocr.presentation.settings.OcrSettingsScreen

/**
 * This module's only public surface.
 *
 * [onNavigateToTextTools] receives a route rather than the screen navigating itself: `:app` is the
 * only module allowed to hold a `NavHostController` (see `AppNavHost`'s doc comment), and this
 * module knows the route contract exists without knowing which feature serves it.
 *
 * The text arrives already capped — the ViewModel does that, so it can also tell the user when it
 * had to shorten something rather than silently delivering less than they extracted.
 *
 * @receiver The nav graph builder to register the destination on.
 * @param onNavigateBack        Called when the user leaves the OCR screen.
 * @param onNavigateToTextTools Called with the route carrying extracted text bound for the text tools.
 * @param onNavigateToSettings  Called when the user opens the engine picker.
 */
fun NavGraphBuilder.ocrScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTextTools: (TextToolsRoute) -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    composable<OcrRoute> {
        OcrScreen(
            onNavigateBack = onNavigateBack,
            onSendToTextTools = { text -> onNavigateToTextTools(TextToolsRoute(text)) },
            onNavigateToSettings = onNavigateToSettings,
        )
    }
}

/**
 * The engine picker, a destination of its own.
 *
 * Separate from [ocrScreen] rather than nested inside it so the OCR screen stays on the back stack
 * while settings is open — which is what lets its ViewModel see the engine change and re-recognise
 * the capture it is still holding.
 *
 * @receiver The nav graph builder to register the destination on.
 * @param onNavigateBack Called when the user leaves the engine picker.
 */
fun NavGraphBuilder.ocrSettingsScreen(
    onNavigateBack: () -> Unit,
) {
    composable<OcrSettingsRoute> {
        OcrSettingsScreen(onNavigateBack = onNavigateBack)
    }
}
