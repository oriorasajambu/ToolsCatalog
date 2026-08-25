package com.minion.scaffold.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.minion.scaffold.core.designsystem.motion.slideInFromRight
import com.minion.scaffold.core.designsystem.motion.slideOutToRight
import com.minion.scaffold.core.navigation.LevelCalibrationRoute
import com.minion.scaffold.core.navigation.ExifStripSettingsRoute
import com.minion.scaffold.core.navigation.SpeedometerSettingsRoute
import com.minion.scaffold.core.navigation.OcrSettingsRoute
import com.minion.scaffold.core.navigation.SoundMeterSettingsRoute
import com.minion.scaffold.core.navigation.ToolsRoute
import com.minion.scaffold.core.navigation.WeatherSearchRoute
import com.minion.scaffold.core.navigation.WeatherSettingsRoute
import com.minion.scaffold.feature.qrcreate.presentation.qrCreateScreen
import com.minion.scaffold.feature.qrcreate.presentation.url.urlCreateScreen
import com.minion.scaffold.feature.qrcreate.presentation.vcard.vCardCreateScreen
import com.minion.scaffold.feature.qrcreate.presentation.wifi.wifiCreateScreen
import com.minion.scaffold.feature.checksum.presentation.checksumScreen
import com.minion.scaffold.feature.exifstrip.presentation.exifStripScreen
import com.minion.scaffold.feature.speedometer.presentation.speedometerScreen
import com.minion.scaffold.feature.speedometer.presentation.speedometerSettingsScreen
import com.minion.scaffold.feature.exifstrip.presentation.exifStripSettingsScreen
import com.minion.scaffold.feature.level.presentation.levelCalibrationScreen
import com.minion.scaffold.feature.level.presentation.levelScreen
import com.minion.scaffold.feature.ocr.presentation.ocrScreen
import com.minion.scaffold.feature.ocr.presentation.ocrSettingsScreen
import com.minion.scaffold.feature.qrscan.presentation.qrScanScreen
import com.minion.scaffold.feature.qrscan.presentation.qrScanSettingsScreen
import com.minion.scaffold.feature.soundmeter.presentation.soundMeterScreen
import com.minion.scaffold.feature.soundmeter.presentation.soundMeterSettingsScreen
import com.minion.scaffold.feature.texttools.presentation.generateScreen
import com.minion.scaffold.feature.texttools.presentation.textToolsScreen
import com.minion.scaffold.feature.tools.presentation.toolsScreen
import com.minion.scaffold.feature.tools.presentation.widget.widgetSettingsScreen
import com.minion.scaffold.feature.weather.presentation.weatherScreen
import com.minion.scaffold.core.navigation.AppRoute
import com.minion.scaffold.core.navigation.WidgetSettingsRoute
import com.minion.scaffold.showkase.ComponentCatalog

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
 *
 * @param modifier              The [Modifier] for the [NavHost].
 * @param navController         The controller for the graph; defaults to a remembered one.
 * @param initialRoute          A route to open once, on top of the start destination — how a
 *                              widget tap reaches a tool. Null in every other case.
 * @param onInitialRouteHandled Called after [initialRoute] has been navigated to, so the caller
 *                              can clear it and a recomposition does not navigate again.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    initialRoute: AppRoute? = null,
    onInitialRouteHandled: () -> Unit = {},
) {
    // Keyed on the route, so a second widget tap for a *different* tool navigates again while a
    // recomposition for any other reason does not.
    LaunchedEffect(initialRoute) {
        val route = initialRoute ?: return@LaunchedEffect

        // Reset to the tools home and push the tool on top. Back from a widget-launched tool
        // therefore always lands on the home, then exits — a clearer model than a back button that
        // walks through history the user never chose, and it stops repeated taps stacking
        // duplicates of the same screen.
        navController.navigate(route) {
            popUpTo(ToolsRoute) { inclusive = false }
            launchSingleTop = true
        }
        onInitialRouteHandled()
    }

    // The Showkase browser has no launcher icon of its own any more — two home-screen entries for
    // one debug install is confusing — so the home screen's brand tile opens it instead. Null in a
    // release build, where the constant folds away and the tile stays decorative.
    val context = LocalContext.current
    val openComponentCatalog: (() -> Unit)? =
        if (ComponentCatalog.IS_AVAILABLE) {
            { ComponentCatalog.open(context) }
        } else {
            null
        }

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
            onOpenWidgetSettings = { navController.navigate(WidgetSettingsRoute) },
            onOpenComponentCatalog = openComponentCatalog,
        )
        // Configures the home-screen widget, but registered by :feature:tools: it edits the tool
        // catalog, and :feature:widget draws no screens at all.
        widgetSettingsScreen(onNavigateBack = { navController.popBackStack() })
        // The scanner reports a payload the user wants to change; :app is the only place that
        // knows the editor exists, so neither feature learns about the other.
        qrScanScreen(
            onNavigateBack = { navController.popBackStack() },
            onEditPayload = { route -> navController.navigate(route) },
            onNavigateToSettings = { route -> navController.navigate(route) },
        )
        qrScanSettingsScreen(
            onNavigateBack = { navController.popBackStack() },
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
        // The OCR tool hands its extraction to the text tools; :app is the only place that knows
        // that screen exists, so neither feature learns about the other.
        ocrScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToTextTools = { route -> navController.navigate(route) },
            onNavigateToSettings = { navController.navigate(OcrSettingsRoute) },
        )
        ocrSettingsScreen(
            onNavigateBack = { navController.popBackStack() },
        )
        generateScreen(
            onNavigateBack = { navController.popBackStack() },
        )
        checksumScreen(
            onNavigateBack = { navController.popBackStack() },
        )
        levelScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToCalibration = { navController.navigate(LevelCalibrationRoute) },
        )
        levelCalibrationScreen(
            onNavigateBack = { navController.popBackStack() },
        )
        soundMeterScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToSettings = { navController.navigate(SoundMeterSettingsRoute) },
        )
        soundMeterSettingsScreen(
            onNavigateBack = { navController.popBackStack() },
        )
        exifStripScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToSettings = { navController.navigate(ExifStripSettingsRoute) },
        )
        exifStripSettingsScreen(
            onNavigateBack = { navController.popBackStack() },
        )
        speedometerScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToSettings = { navController.navigate(SpeedometerSettingsRoute) },
        )
        speedometerSettingsScreen(
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
