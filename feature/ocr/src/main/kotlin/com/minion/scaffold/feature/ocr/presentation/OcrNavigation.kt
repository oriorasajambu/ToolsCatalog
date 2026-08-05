package com.minion.scaffold.feature.ocr.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.OcrRoute
import com.minion.scaffold.core.navigation.TextToolsRoute

/**
 * This module's only public surface.
 *
 * [onNavigateToTextTools] receives a route rather than the screen navigating itself: `:app` is the
 * only module allowed to hold a `NavHostController` (see `AppNavHost`'s doc comment), and this
 * module knows the route contract exists without knowing which feature serves it.
 *
 * The text arrives already capped — the ViewModel does that, so it can also tell the user when it
 * had to shorten something rather than silently delivering less than they extracted.
 */
fun NavGraphBuilder.ocrScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTextTools: (TextToolsRoute) -> Unit,
) {
    composable<OcrRoute> {
        OcrScreen(
            onNavigateBack = onNavigateBack,
            onSendToTextTools = { text -> onNavigateToTextTools(TextToolsRoute(text)) },
        )
    }
}
