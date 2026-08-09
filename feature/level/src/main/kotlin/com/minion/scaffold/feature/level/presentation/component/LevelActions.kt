package com.minion.scaffold.feature.level.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.minion.scaffold.core.designsystem.component.AppButton
import com.minion.scaffold.core.designsystem.component.AppOutlinedButton
import com.minion.scaffold.core.level.usecase.Steadiness
import com.minion.scaffold.feature.level.R
import com.minion.scaffold.feature.level.domain.GravitySensor
import com.minion.scaffold.feature.level.presentation.LevelIntent
import com.minion.scaffold.feature.level.presentation.LevelState

/** Freeze, reference and calibration — the controls that need a press rather than a glance. */
@Composable
internal fun LevelActions(
    state: State<LevelState>,
    onIntent: (LevelIntent) -> Unit,
    onNavigateToCalibration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val frozen by remember(state) { derivedStateOf { state.value.frozen != null } }
    val hasReference by remember(state) { derivedStateOf { state.value.reference != null } }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
            dimensionResource(R.dimen.level_spacing_tight),
        ),
    ) {
        AppButton(
            text = stringResource(if (frozen) R.string.level_unfreeze else R.string.level_freeze),
            onClick = { onIntent(LevelIntent.FreezeToggled) },
            modifier = Modifier.fillMaxWidth(),
        )

        AppOutlinedButton(
            text = stringResource(
                if (hasReference) R.string.level_reference_clear else R.string.level_reference_set,
            ),
            onClick = {
                onIntent(
                    if (hasReference) LevelIntent.ReferenceCleared
                    else LevelIntent.ReferenceCaptured,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        CalibrationSummary(
            state = state,
            onIntent = onIntent,
            onNavigateToCalibration = onNavigateToCalibration,
        )
    }
}

/**
 * What the app knows about its own accuracy, said out loud.
 *
 * An uncalibrated phone is off by a few tenths of a degree for reasons the user cannot see — which
 * is more than the tolerance the display goes green at. Rather than quietly rendering a verdict it
 * cannot support, the tool says so and offers the twenty seconds that fixes it.
 */
@Composable
private fun CalibrationSummary(
    state: State<LevelState>,
    onIntent: (LevelIntent) -> Unit,
    onNavigateToCalibration: () -> Unit,
) {
    val calibrated by remember(state) { derivedStateOf { state.value.isCalibrated } }
    val calibration by remember(state) { derivedStateOf { state.value.calibration } }
    val locale = LocalLocale.current.platformLocale

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(dimensionResource(R.dimen.level_spacing)),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.level_spacing_tight),
            ),
        ) {
            Text(
                text = if (calibrated) {
                    stringResource(
                        R.string.level_calibrated_summary,
                        String.format(locale, "%.2f", calibration.angleDegrees),
                        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, locale)
                            .format(java.util.Date(calibration.takenAtMillis)),
                    )
                } else {
                    stringResource(R.string.level_uncalibrated_summary)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Stated plainly rather than buried: a flat flip observes only two of the three bias
            // components, and the one it misses is the one a plumb reading leans on.
            if (calibrated && !calibration.correctsEdgePose) {
                Text(
                    text = stringResource(R.string.level_calibrated_flat_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(onClick = onNavigateToCalibration) {
                Text(
                    text = stringResource(
                        if (calibrated) R.string.level_recalibrate else R.string.level_calibrate,
                    ),
                )
            }

            if (calibrated) {
                TextButton(onClick = { onIntent(LevelIntent.CalibrationCleared) }) {
                    Text(text = stringResource(R.string.level_calibration_reset))
                }
            }
        }
    }
}

/** The transient banners: hold-still, frozen, relative mode, and the first-use calibration nudge. */
@Composable
internal fun LevelNotices(
    state: State<LevelState>,
    onIntent: (LevelIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val steadiness by remember(state) { derivedStateOf { state.value.steadiness } }
    val frozen by remember(state) { derivedStateOf { state.value.frozen != null } }
    val hasReference by remember(state) { derivedStateOf { state.value.reference != null } }
    val sensor by remember(state) { derivedStateOf { state.value.sensor } }
    val showPrompt by remember(state) {
        derivedStateOf { state.value.showCalibrationPrompt && !state.value.isCalibrated }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(
            dimensionResource(R.dimen.level_spacing_tight),
        ),
    ) {
        // Frozen first: a held reading that looks live is the worst thing this screen can show.
        if (frozen) Banner(textRes = R.string.level_frozen_banner, emphasised = true)

        if (hasReference) Banner(textRes = R.string.level_reference_banner, emphasised = true)

        if (steadiness == Steadiness.Moving) Banner(textRes = R.string.level_hold_still)

        if (sensor == GravitySensor.Accelerometer) {
            Banner(textRes = R.string.level_accelerometer_fallback)
        }

        if (showPrompt) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(dimensionResource(R.dimen.level_spacing)),
                ) {
                    Text(
                        text = stringResource(R.string.level_calibration_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    TextButton(
                        onClick = { onIntent(LevelIntent.CalibrationPromptDismissed) },
                    ) {
                        Text(text = stringResource(R.string.level_calibration_prompt_dismiss))
                    }
                }
            }
        }
    }
}

@Composable
private fun Banner(textRes: Int, emphasised: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasised) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Text(
            text = stringResource(textRes),
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.level_spacing)),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = if (emphasised) MaterialTheme.colorScheme.onTertiaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
