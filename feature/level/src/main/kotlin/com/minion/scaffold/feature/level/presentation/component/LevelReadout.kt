package com.minion.scaffold.feature.level.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.minion.scaffold.core.level.model.SlopeUnit
import com.minion.scaffold.core.level.model.convertSlope
import com.minion.scaffold.feature.level.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The numbers.
 *
 * ## Why everything here goes through `derivedStateOf`
 *
 * The state updates at ~50Hz. Reading a field directly would recompose this text fifty times a
 * second, which is both wasteful and — more to the point — unreadable: digits changing that fast
 * blur into an unreadable smear. Quantising *inside* `derivedStateOf` means recomposition happens
 * only when the displayed value actually changes, which doubles as the throttle and as the
 * deadband.
 *
 * ## The deadband
 *
 * One decimal place is already at the edge of what a phone can resolve, so the last digit would
 * flicker between neighbouring values indefinitely even with a perfect filter. Rounding to a tenth
 * *and* refusing to move until the underlying value has travelled half a step is the difference
 * between something that reads like an instrument and something that reads like a toy.
 */
@Composable
internal fun LevelReadout(
    degrees: State<Double>,
    status: State<LevelStatus>,
    modifier: Modifier = Modifier,
) {
    val quantised by remember(degrees) {
        derivedStateOf { quantise(degrees.value) }
    }

    val grade by remember(degrees) {
        derivedStateOf { convertSlope(quantise(degrees.value), SlopeUnit.PercentGrade) }
    }

    val unavailable = stringResource(R.string.level_value_unavailable)

    // LocalLocale, not Locale.getDefault(): a plain read is not observable, so the numbers would
    // keep their old formatting after a locale change until something else forced recomposition.
    val locale = LocalLocale.current.platformLocale

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.level_spacing_tight)),
    ) {
        Text(
            text = stringResource(R.string.level_degrees_format, formatDegrees(quantised, locale)),
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            // Announced politely and only when the quantised value changes — a 50Hz live region
            // would make TalkBack completely unusable.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(
            text = grade
                ?.let { stringResource(R.string.level_grade_format, formatGrade(it, locale)) }
                ?: unavailable,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LevelStatusLine(status = status)
    }
}

/**
 * One line saying whether the number above it can be believed.
 *
 * **Always occupies its line**, even when there is nothing remarkable to say — that is the whole
 * point. These used to be banners stacked above the bullseye, which pushed the entire screen down
 * and back up as the phone was moved, so the thing you were reading kept sliding out from under
 * your eyes.
 *
 * Deliberately *not* a snackbar, unlike the one-shot notices. "Held" and "measuring against a
 * reference" are persistent modes, and a message that dismisses itself after four seconds would
 * leave a frozen reading looking live — the single most dangerous thing this screen could do.
 */
@Composable
private fun LevelStatusLine(status: State<LevelStatus>) {
    val current by status

    Text(
        text = stringResource(current.labelRes()),
        style = MaterialTheme.typography.labelLarge,
        color = when (current) {
            LevelStatus.Held -> MaterialTheme.colorScheme.tertiary
            LevelStatus.Relative -> MaterialTheme.colorScheme.tertiary
            LevelStatus.Moving -> MaterialTheme.colorScheme.error
            LevelStatus.Live -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.Center,
    )
}

/**
 * What the reading currently is, in priority order.
 *
 * Held outranks everything: a stale number presented as live is worse than any other confusion
 * here. Moving outranks Relative because it says the number is meaningless right now, where
 * Relative only says what it is measured against.
 */
internal enum class LevelStatus { Held, Moving, Relative, Live }

private fun LevelStatus.labelRes(): Int = when (this) {
    LevelStatus.Held -> R.string.level_status_held
    LevelStatus.Moving -> R.string.level_status_moving
    LevelStatus.Relative -> R.string.level_status_relative
    LevelStatus.Live -> R.string.level_status_live
}

/** Pitch and roll side by side, for the flat pose where one number is not enough. */
@Composable
internal fun AxisReadout(
    tiltX: State<Double>,
    tiltY: State<Double>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        AxisValue(labelRes = R.string.level_axis_pitch, value = tiltY)
        AxisValue(labelRes = R.string.level_axis_roll, value = tiltX)
    }
}

@Composable
private fun AxisValue(labelRes: Int, value: State<Double>) {
    val quantised by remember(value) { derivedStateOf { quantise(value.value) } }
    val locale = LocalLocale.current.platformLocale

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.level_degrees_format, formatDegrees(quantised, locale)),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

/** Rounded to a tenth of a degree — more decimal places than the hardware can justify. */
private fun quantise(degrees: Double): Double = (degrees * 10.0).roundToInt() / 10.0

/**
 * Formats to one decimal, with the sign of a near-zero value suppressed.
 *
 * Without the clamp the app ships **"-0.0°"**, which is the single most common cosmetic bug in
 * this class of tool: a value a hair below zero rounds to zero but keeps its sign.
 */
private fun formatDegrees(degrees: Double, locale: java.util.Locale): String {
    val safe = if (abs(degrees) < ZERO_EPSILON) 0.0 else degrees
    return String.format(locale, "%.1f", safe)
}

private fun formatGrade(percent: Double, locale: java.util.Locale): String {
    val safe = if (abs(percent) < ZERO_EPSILON) 0.0 else percent
    return String.format(locale, "%.1f", safe)
}

private const val ZERO_EPSILON = 0.05
