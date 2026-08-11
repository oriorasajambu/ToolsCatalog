package com.minion.scaffold.feature.soundmeter.presentation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.minion.scaffold.core.navigation.SoundMeterRoute
import com.minion.scaffold.core.navigation.SoundMeterSettingsRoute
import com.minion.scaffold.feature.soundmeter.presentation.settings.SoundMeterSettingsScreen

/**
 * This module's only public surface.
 *
 * Navigation arrives as lambdas rather than a `NavHostController`: `:app` is the only module allowed
 * to hold one, so this module knows the route contract exists without knowing which feature serves
 * it.
 */
fun NavGraphBuilder.soundMeterScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    composable<SoundMeterRoute> {
        SoundMeterScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToSettings = onNavigateToSettings,
        )
    }
}

/**
 * The calibration offset and the statements about what this tool is.
 *
 * A destination rather than a panel on the meter, and deliberately so. The offset is set once and
 * then left alone, while a slider sitting beside a live number invites dragging it until the reading
 * looks agreeable — which feels like calibrating and is the opposite of it. Putting it behind a
 * navigation step also gives the accuracy and privacy statements room to say what they mean.
 */
fun NavGraphBuilder.soundMeterSettingsScreen(
    onNavigateBack: () -> Unit,
) {
    composable<SoundMeterSettingsRoute> {
        SoundMeterSettingsScreen(onNavigateBack = onNavigateBack)
    }
}
