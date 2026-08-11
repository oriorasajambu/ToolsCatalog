package com.minion.scaffold.feature.soundmeter.presentation.settings

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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.sound.model.SoundReference
import com.minion.scaffold.feature.soundmeter.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SoundMeterSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SoundMeterSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = dimensionResource(R.dimen.soundmeter_spacing)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.soundmeter_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                R.string.soundmeter_navigate_back,
                            ),
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
            OffsetCard(
                offsetDb = state.offsetDb,
                onOffsetChange = viewModel::onOffsetChange,
                onReset = viewModel::onOffsetReset,
            )

            ExplanationCard(
                titleRes = R.string.soundmeter_settings_accuracy_title,
                bodyRes = R.string.soundmeter_settings_accuracy_body,
            )

            ExplanationCard(
                titleRes = R.string.soundmeter_settings_privacy_title,
                bodyRes = R.string.soundmeter_settings_privacy_body,
            )
        }
    }
}

/**
 * The offset, and an honest account of what it is.
 *
 * The slider changes the number. It does **not** produce a calibrated instrument, and the copy says
 * so in as many words — dragging it until the reading looks plausible is the failure mode this text
 * exists to head off. Establishing a real offset needs a reference outside the phone, and the body
 * text says how.
 *
 * Reset is offered because zero is a meaningful position — it is "the generic assumption, untouched"
 * — and getting back to it by dragging is fiddly enough that people would not bother.
 */
@Composable
private fun OffsetCard(
    offsetDb: Double,
    onOffsetChange: (Double) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.soundmeter_spacing)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.soundmeter_spacing_tight),
            ),
        ) {
            Text(
                text = stringResource(R.string.soundmeter_settings_offset_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.soundmeter_settings_offset_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringResource(R.string.soundmeter_settings_offset_value, offsetDb),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Slider(
                value = offsetDb.toFloat(),
                onValueChange = { onOffsetChange(it.toDouble()) },
                valueRange = -SoundReference.MAX_USER_OFFSET_DB.toFloat()..
                    SoundReference.MAX_USER_OFFSET_DB.toFloat(),
                // One step per 0.5 dB. A continuous slider cannot be returned to a chosen value,
                // and half a decibel is already finer than anything this can resolve.
                steps = OFFSET_STEPS,
            )

            TextButton(
                onClick = onReset,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = stringResource(R.string.soundmeter_settings_offset_reset))
            }
        }
    }
}

@Composable
private fun ExplanationCard(
    titleRes: Int,
    bodyRes: Int,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.soundmeter_spacing)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.soundmeter_spacing_tight),
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

/** ±20 dB in 0.5 dB steps: 80 intervals, so 79 stops between the ends. */
private const val OFFSET_STEPS = 79
