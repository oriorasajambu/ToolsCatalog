package com.minion.scaffold.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.minion.scaffold.core.designsystem.motion.slideInFromRight
import com.minion.scaffold.core.designsystem.motion.slideOutToRight
import com.minion.scaffold.core.navigation.ToolsRoute
import com.minion.scaffold.core.navigation.WeatherSearchRoute
import com.minion.scaffold.core.navigation.WeatherSettingsRoute
import com.minion.scaffold.feature.qrcreate.presentation.qrCreateScreen
import com.minion.scaffold.feature.qrcreate.presentation.url.urlCreateScreen
import com.minion.scaffold.feature.qrcreate.presentation.vcard.vCardCreateScreen
import com.minion.scaffold.feature.qrcreate.presentation.wifi.wifiCreateScreen
import com.minion.scaffold.feature.qrscan.presentation.qrScanScreen
import com.minion.scaffold.feature.texttools.presentation.generateScreen
import com.minion.scaffold.feature.texttools.presentation.textToolsScreen
import com.minion.scaffold.feature.tools.presentation.toolsScreen
import com.minion.scaffold.feature.weather.presentation.weatherScreen

/**
 * The app's single navigation graph, assembled from every feature's entry point.
 *
 * `:app` is the only module allowed to see all the features at once — that is what lets features
 * stay ignorant of each other. Each contributes a `NavGraphBuilder` extension and nothing else;
 * its ViewModel, contract and screen stay `internal` behind it.
 *
 * Navigation lambdas are passed *down* into the feature, never a [NavHostController]. Handing a
 * feature the controller lets it navigate anywhere, which is the same as letting it know about
 * every other feature.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = ToolsRoute,
        modifier = modifier,
        // The push motion, applied once as the default so every destination inherits it — a screen
        // slides in from the right when opened and back off to the right when closed. The camera is
        // the one exception; it overrides these with the modal (vertical) motion at its own
        // `composable`. The counterpart — what the covered screen does — is left to hold still, so
        // the moving screen reads clearly against a static background.
        enterTransition = { slideInFromRight() },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { slideOutToRight() },
    ) {
        // The tools screen hands back the route of whatever the user tapped. It never learns which
        // feature owns that route, so adding a tool does not touch this file.
        toolsScreen(
            onOpenTool = { route -> navController.navigate(route) },
        )
        // The scanner reports a payload the user wants to change; :app is the only place that
        // knows the editor exists, so neither feature learns about the other.
        qrScanScreen(
            onNavigateBack = { navController.popBackStack() },
            onEditPayload = { route -> navController.navigate(route) },
        )
        qrCreateScreen(
            onNavigateBack = { navController.popBackStack() },
        )
        wifiCreateScreen(
            onNavigateBack = { navController.popBackStack() },
        )
        urlCreateScreen(
            onNavigateBack = { navController.popBackStack() },
        )
        vCardCreateScreen(
            onNavigateBack = { navController.popBackStack() },
        )
        textToolsScreen(
            onNavigateBack = { navController.popBackStack() },
        )
        generateScreen(
            onNavigateBack = { navController.popBackStack() },
        )
        weatherScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToDetail = { route -> navController.navigate(route) },
            onNavigateToSearch = { navController.navigate(WeatherSearchRoute) },
            onNavigateToSettings = { navController.navigate(WeatherSettingsRoute) },
        )
    }
}
