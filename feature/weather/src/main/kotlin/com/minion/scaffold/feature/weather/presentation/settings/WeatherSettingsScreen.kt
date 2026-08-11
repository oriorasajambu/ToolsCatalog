package com.minion.scaffold.feature.weather.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.weather.model.WeatherUnit
import com.minion.scaffold.feature.weather.R

/**
 * The weather settings: the metric/imperial unit toggle.
 *
 * @param onNavigateBack Called when the user leaves the settings screen.
 * @param modifier       The [Modifier] for the screen.
 * @param viewModel      The screen's ViewModel; defaults to a Hilt-provided instance.
 */
@Composable
internal fun WeatherSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeatherSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    WeatherSettingsContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherSettingsContent(
    state: WeatherSettingsState,
    onIntent: (WeatherSettingsIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.weather_spacing)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.weather_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.weather_navigate_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            Text(
                text = stringResource(R.string.weather_settings_units_header),
                modifier = Modifier.padding(horizontal = spacing, vertical = spacing / 2),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            // selectableGroup() is what makes this announce as "1 of 2" to a screen reader rather
            // than as two unrelated radio buttons that happen to sit next to each other.
            Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
                WeatherUnit.entries.forEach { unit ->
                    UnitRow(
                        unit = unit,
                        selected = state.unit == unit,
                        onSelected = { onIntent(WeatherSettingsIntent.UnitSelected(unit)) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.weather_settings_units_note),
                modifier = Modifier.padding(spacing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnitRow(
    unit: WeatherUnit,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    ListItem(
        modifier = Modifier.selectable(
            selected = selected,
            onClick = onSelected,
            role = Role.RadioButton,
        ),
        headlineContent = { Text(text = stringResource(unit.toLabelRes())) },
        supportingContent = { Text(text = stringResource(unit.toDetailRes())) },
        leadingContent = {
            // null onClick: the whole row owns the click, and a separately clickable radio would
            // make the row announce twice and give a second, smaller touch target for the same act.
            RadioButton(selected = selected, onClick = null)
        },
    )
}

@StringRes
private fun WeatherUnit.toLabelRes(): Int = when (this) {
    WeatherUnit.METRIC -> R.string.weather_unit_metric
    WeatherUnit.IMPERIAL -> R.string.weather_unit_imperial
}

@StringRes
private fun WeatherUnit.toDetailRes(): Int = when (this) {
    WeatherUnit.METRIC -> R.string.weather_unit_metric_detail
    WeatherUnit.IMPERIAL -> R.string.weather_unit_imperial_detail
}

@Preview
@Composable
internal fun WeatherSettingsPreview() {
    AppTheme {
        WeatherSettingsContent(
            state = WeatherSettingsState(unit = WeatherUnit.METRIC),
            onIntent = {},
            onNavigateBack = {},
        )
    }
}
