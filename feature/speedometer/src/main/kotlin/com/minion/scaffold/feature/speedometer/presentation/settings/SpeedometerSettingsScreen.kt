package com.minion.scaffold.feature.speedometer.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.gnss.model.CoordinateFormat
import com.minion.scaffold.core.gnss.model.DistanceUnit
import com.minion.scaffold.core.gnss.model.SpeedUnit
import com.minion.scaffold.feature.speedometer.R
import com.minion.scaffold.feature.speedometer.presentation.component.labelRes

/**
 * The speedometer's settings: unit and coordinate-format selectors, and the accuracy notes.
 *
 * @param onNavigateBack Called when the user leaves the settings screen.
 * @param modifier       The [Modifier] for the screen.
 * @param viewModel      The screen's ViewModel; defaults to a Hilt-provided instance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpeedometerSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpeedometerSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = dimensionResource(R.dimen.speedometer_spacing)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.speedometer_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.speedometer_navigate_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            // Speed is chosen separately from altitude and distance. Knots pairs with metres at sea
            // and with feet in aviation but never with miles, so one metric-or-imperial switch cannot
            // express the real combinations.
            ChoiceCard(titleRes = R.string.speedometer_settings_speed_unit) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SpeedUnit.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = entry == state.speedUnit,
                            onClick = { viewModel.onSpeedUnitChange(entry) },
                            shape = SegmentedButtonDefaults.itemShape(index, SpeedUnit.entries.size),
                        ) {
                            Text(text = stringResource(entry.labelRes()))
                        }
                    }
                }
            }

            ChoiceCard(titleRes = R.string.speedometer_settings_distance_unit) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    DistanceUnit.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = entry == state.distanceUnit,
                            onClick = { viewModel.onDistanceUnitChange(entry) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index,
                                DistanceUnit.entries.size,
                            ),
                        ) {
                            Text(
                                text = stringResource(
                                    when (entry) {
                                        DistanceUnit.Metric ->
                                            R.string.speedometer_settings_distance_metric

                                        DistanceUnit.Imperial ->
                                            R.string.speedometer_settings_distance_imperial
                                    },
                                ),
                            )
                        }
                    }
                }
            }

            ChoiceCard(titleRes = R.string.speedometer_settings_coordinate_format) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CoordinateFormat.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = entry == state.coordinateFormat,
                            onClick = { viewModel.onCoordinateFormatChange(entry) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index,
                                CoordinateFormat.entries.size,
                            ),
                        ) {
                            Text(
                                text = stringResource(
                                    when (entry) {
                                        CoordinateFormat.Decimal ->
                                            R.string.speedometer_settings_coordinates_decimal

                                        CoordinateFormat.DegreesMinutesSeconds ->
                                            R.string.speedometer_settings_coordinates_dms
                                    },
                                ),
                            )
                        }
                    }
                }
            }

            ExplanationCard(
                titleRes = R.string.speedometer_settings_accuracy_title,
                bodyRes = R.string.speedometer_settings_accuracy_body,
            )

            // The single most useful thing this screen says. Without it, a 6 km/h disagreement with
            // the dashboard reads as the app being broken.
            ExplanationCard(
                titleRes = R.string.speedometer_settings_dashboard_title,
                bodyRes = R.string.speedometer_settings_dashboard_body,
            )

            ExplanationCard(
                titleRes = R.string.speedometer_settings_altitude_title,
                bodyRes = R.string.speedometer_settings_altitude_body,
            )
        }
    }
}

@Composable
private fun ChoiceCard(
    titleRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val spacing = dimensionResource(R.dimen.speedometer_spacing)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.speedometer_spacing_tight),
            ),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
            content()
        }
    }
}

@Composable
private fun ExplanationCard(
    titleRes: Int,
    bodyRes: Int,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.speedometer_spacing)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.speedometer_spacing_tight),
            ),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
