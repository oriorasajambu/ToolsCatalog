package com.minion.scaffold.feature.level.presentation.calibration

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.designsystem.component.AppOutlinedButton
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.level.usecase.CalibrationRejection
import com.minion.scaffold.core.ui.mvi.ObserveAsEvents
import com.minion.scaffold.feature.level.R

/**
 * The guided two-point calibration screen.
 *
 * @param onNavigateBack Called when the user leaves the calibration flow.
 * @param modifier       The [Modifier] for the screen.
 * @param viewModel      The screen's ViewModel; defaults to a Hilt-provided instance.
 */
@Composable
internal fun CalibrationScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalibrationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onIntent(CalibrationIntent.ScreenResumed)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        viewModel.onIntent(CalibrationIntent.ScreenPaused)
    }

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            CalibrationEffect.Saved -> onNavigateBack()
        }
    }

    CalibrationContent(
        state = state,
        onIntent = viewModel::onIntent,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalibrationContent(
    state: CalibrationState,
    onIntent: (CalibrationIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.level_spacing)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.level_calibration_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.level_navigate_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Text(
                text = stringResource(R.string.level_calibration_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.rejection?.let { rejection ->
                Text(
                    text = stringResource(rejection.messageRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            when (state.step) {
                CalibrationState.Step.First, CalibrationState.Step.Second -> CaptureStep(
                    state = state,
                    onIntent = onIntent,
                )

                CalibrationState.Step.Done -> DoneStep(state = state, onIntent = onIntent)
            }
        }
    }
}

@Composable
private fun CaptureStep(state: CalibrationState, onIntent: (CalibrationIntent) -> Unit) {
    // The wording of step two is load-bearing. Turning the phone over like a page instead of
    // spinning it flat negates a different pair of components, and on an already-level surface no
    // validation can detect it — so the instruction has to prevent what arithmetic cannot catch.
    Text(
        text = stringResource(
            if (state.step == CalibrationState.Step.First) {
                R.string.level_calibration_step_one
            } else {
                R.string.level_calibration_step_two
            },
        ),
        style = MaterialTheme.typography.titleMedium,
    )

    Text(
        text = stringResource(
            when {
                state.capturing -> R.string.level_calibration_capturing
                !state.steady -> R.string.level_calibration_hold_still
                // Not the button's own label, which is what this said before and which read as
                // the word "Capture" printed twice, one above the other.
                else -> R.string.level_calibration_ready
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    AppButton(
        text = stringResource(R.string.level_calibration_capture),
        onClick = { onIntent(CalibrationIntent.CaptureRequested) },
        enabled = state.steady && !state.capturing,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DoneStep(state: CalibrationState, onIntent: (CalibrationIntent) -> Unit) {
    val calibration = state.result ?: return
    val locale = LocalLocale.current.platformLocale

    // The surface's own tilt is shown alongside the device bias. It costs nothing — it falls out of
    // the same two readings — and it is the best confirmation available that the flip worked.
    Text(
        text = stringResource(
            R.string.level_calibration_done,
            String.format(locale, "%.2f", calibration.angleDegrees),
            String.format(locale, "%.2f", calibration.surfaceTiltDegrees),
        ),
        style = MaterialTheme.typography.bodyLarge,
    )

    AppButton(
        text = stringResource(R.string.level_calibration_save),
        onClick = { onIntent(CalibrationIntent.SaveRequested) },
        modifier = Modifier.fillMaxWidth(),
    )

    AppOutlinedButton(
        text = stringResource(R.string.level_calibration_retry),
        onClick = { onIntent(CalibrationIntent.Restarted) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@StringRes
private fun CalibrationRejection.messageRes(): Int = when (this) {
    CalibrationRejection.NotSteady -> R.string.level_calibration_failed_not_steady
    CalibrationRejection.NotAFlip -> R.string.level_calibration_failed_not_a_flip
    CalibrationRejection.ImplausibleResult -> R.string.level_calibration_failed_implausible
    CalibrationRejection.ImplausibleReading -> R.string.level_calibration_failed_reading
}

@Preview
@Composable
internal fun CalibrationPreview() {
    AppTheme {
        CalibrationContent(
            state = CalibrationState(steady = true),
            onIntent = {},
            onNavigateBack = {},
        )
    }
}
