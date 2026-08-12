package com.minion.scaffold.feature.tools.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.AppRoute
import com.minion.scaffold.core.navigation.ToolsRoute

/**
 * This module's entire public surface.
 *
 * [onOpenTool] takes an [AppRoute] rather than a tool id because the destination is already a
 * route by the time it leaves here — `:app` only has to call `navigate`, and does not need a
 * `when` over tool ids that would have to be kept in step with the catalog.
 *
 * @receiver The nav graph builder to register the destination on.
 * @param onOpenTool Called with the route of the tool the user selected.
 */
fun NavGraphBuilder.toolsScreen(
    onOpenTool: (AppRoute) -> Unit,
) {
    composable<ToolsRoute> {
        ToolsScreen(onOpenTool = onOpenTool)
    }
}
