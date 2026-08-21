package com.minion.scaffold.feature.qrscan.presentation.compare

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.minion.scaffold.core.emv.model.DiffStatus
import com.minion.scaffold.feature.qrscan.R

/**
 * The shapes every comparison row is built from, whichever format it came from.
 *
 * A payment code and a Wi-Fi code decompose differently but read the same way once decomposed: a
 * label, a status, and the two values stacked. Keeping the card here rather than in each view is
 * what stops the two drifting into looking like different tools.
 */

/**
 * The colour a status answers to.
 *
 * `error` for a change is deliberate even though a difference is a legitimate finding rather than a
 * fault: the reason anybody opened this screen is to catch a code that is *not* the one they
 * expected, so the differing rows are the alarm and have to read as one. Unchanged rows stay on the
 * surface colour so they recede.
 */
@Composable
internal fun DiffStatus.accentColor(): Color = when (this) {
    DiffStatus.SAME -> MaterialTheme.colorScheme.surfaceVariant
    DiffStatus.CHANGED -> MaterialTheme.colorScheme.error
    DiffStatus.ONLY_IN_BASELINE -> MaterialTheme.colorScheme.tertiary
    DiffStatus.ONLY_IN_CANDIDATE -> MaterialTheme.colorScheme.primary
}

/**
 * The same status, in a colour that can be *read*.
 *
 * Separate from [accentColor] because that one is a fill. `surfaceVariant` is a surface role, and
 * an unchanged subtag heading painted with it is grey text on a grey card — legible in a preview
 * against white, invisible on a device in the dark theme. The other three are already content
 * colours and pass straight through.
 */
@Composable
internal fun DiffStatus.labelColor(): Color = when (this) {
    DiffStatus.SAME -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> accentColor()
}

/**
 * One row of a comparison: a coloured edge, a heading, and whatever the caller stacks beneath it.
 *
 * The edge carries the status as well as the words do. Sixty rows of "Unchanged" is not something
 * anybody reads, but a column with three coloured stripes in it is something they can find.
 *
 * @param label      What the field is called.
 * @param status     How the two sides relate.
 * @param annotation An extra note under the heading — a tag that moved slot, and nothing else so far.
 */
@Composable
internal fun DiffCard(
    label: String,
    status: DiffStatus,
    modifier: Modifier = Modifier,
    annotation: String? = null,
    content: @Composable () -> Unit,
) {
    val resources = LocalResources.current
    val spacing = dimensionResource(R.dimen.qrscan_spacing)
    val tight = dimensionResource(R.dimen.qrscan_spacing_tight)
    val accent = status.accentColor()

    Card(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // The status stripe. Full height so a long row is marked down its whole length rather
            // than only beside its heading.
            Box(
                modifier = Modifier
                    .width(dimensionResource(R.dimen.qrscan_compare_status_bar))
                    .fillMaxHeight()
                    .background(accent),
            )

            Column(
                modifier = Modifier.padding(spacing),
                verticalArrangement = Arrangement.spacedBy(tight),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )

                if (annotation != null) {
                    Text(
                        text = annotation,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (status != DiffStatus.SAME) {
                    Text(
                        text = status.describe(resources),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                }

                content()
            }
        }
    }
}

/**
 * The two values, stacked and labelled.
 *
 * Stacked rather than side by side because a nineteen-digit PAN and a merchant name in monospace do
 * not survive half a phone's width. Unchanged rows print the value once — repeating a string
 * verbatim under itself is noise that makes the changed rows harder to spot.
 *
 * @param baselineValue The first code's value, or null when it has none.
 * @param candidateValue The second code's value, or null when it has none.
 */
@Composable
internal fun DiffValues(
    baselineValue: String?,
    candidateValue: String?,
    status: DiffStatus,
    modifier: Modifier = Modifier,
    monospace: Boolean = true,
) {
    val tight = dimensionResource(R.dimen.qrscan_spacing_tight)
    val family = if (monospace) FontFamily.Monospace else FontFamily.Default

    if (status == DiffStatus.SAME) {
        Text(
            text = baselineValue.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = family,
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(tight)) {
        SideValue(
            sideRes = R.string.qrscan_compare_side_baseline,
            value = baselineValue,
            family = family,
        )
        SideValue(
            sideRes = R.string.qrscan_compare_side_candidate,
            value = candidateValue,
            family = family,
        )
    }
}

@Composable
private fun SideValue(@StringRes sideRes: Int, value: String?, family: FontFamily) {
    val tight = dimensionResource(R.dimen.qrscan_spacing_tight)

    Row(horizontalArrangement = Arrangement.spacedBy(tight)) {
        Text(
            text = stringResource(sideRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            // An absent value gets a dash rather than a blank, so the two sides stay legible as a
            // pair and "this field is not there" reads as a statement instead of a rendering gap.
            text = value ?: stringResource(R.string.qrscan_compare_absent),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = family,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
