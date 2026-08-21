package com.minion.scaffold.feature.qrscan.presentation.compare

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.tooling.preview.Preview
import com.minion.scaffold.core.designsystem.component.OffsetGridText
import com.minion.scaffold.core.designsystem.component.OffsetSpan
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.feature.qrscan.R
import com.minion.scaffold.feature.qrscan.domain.compare.DiffSpan
import com.minion.scaffold.feature.qrscan.domain.compare.PayloadCharDiff
import com.minion.scaffold.feature.qrscan.presentation.RawDiffState

/**
 * The two payloads themselves, with the runs that differ picked out.
 *
 * The view that still works when the fields cannot explain what happened — a stray character, a
 * length digit that shifted everything after it, an encoding that re-ordered nothing but re-spelled
 * a value. It is also the only place the *bytes* are visible, which is what a reader falls back to
 * when the decoded fields say "equivalent" and they want to see why they are not identical.
 *
 * @param state Whether the alignment has been computed yet.
 */
@Composable
internal fun RawDiffView(
    baselinePayload: String,
    candidatePayload: String,
    state: RawDiffState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        when (state) {
            // Both mean "not on screen yet". NotComputed is the frame between selecting the tab and
            // the intent being handled, and showing the same spinner keeps that frame from flashing
            // an empty column.
            RawDiffState.NotComputed, RawDiffState.Computing -> item(key = COMPUTING_KEY) {
                ComputingCard()
            }

            is RawDiffState.Ready -> {
                if (state.diff.truncated) {
                    item(key = NOTE_KEY) {
                        NoteCard(text = stringResource(R.string.qrscan_compare_raw_truncated))
                    }
                }
                if (state.diff.identical) {
                    item(key = IDENTICAL_KEY) {
                        NoteCard(text = stringResource(R.string.qrscan_compare_raw_identical))
                    }
                }

                item(key = BASELINE_KEY) {
                    PayloadCard(
                        headingRes = R.string.qrscan_compare_side_baseline,
                        payload = baselinePayload,
                        spans = state.diff.baselineSpans,
                        highlight = MaterialTheme.colorScheme.errorContainer,
                    )
                }

                item(key = CANDIDATE_KEY) {
                    PayloadCard(
                        headingRes = R.string.qrscan_compare_side_candidate,
                        payload = candidatePayload,
                        spans = state.diff.candidateSpans,
                        highlight = MaterialTheme.colorScheme.tertiaryContainer,
                    )
                }
            }
        }
    }
}

/**
 * One payload, with its differing runs banded.
 *
 * The two sides get different bands — what is missing from the first and what is new in the second
 * are not the same finding, and one colour for both would make a substitution look like a single
 * edit that happened twice.
 */
@Composable
private fun PayloadCard(
    @StringRes headingRes: Int,
    payload: String,
    spans: List<DiffSpan>,
    highlight: Color,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)
    val onHighlight = MaterialTheme.colorScheme.onErrorContainer

    val offsetSpans = remember(spans, highlight, onHighlight) {
        spans.map { span ->
            OffsetSpan(
                start = span.start,
                endExclusive = span.endExclusive,
                style = SpanStyle(background = highlight, color = onHighlight),
            )
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing),
            verticalArrangement = Arrangement.spacedBy(
                dimensionResource(R.dimen.qrscan_spacing_tight),
            ),
        ) {
            Text(
                text = stringResource(headingRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            OffsetGridText(
                text = payload,
                spans = offsetSpans,
                contentDescription = stringResource(R.string.qrscan_diagnostic_title),
            )

            Text(
                text = stringResource(R.string.qrscan_diagnostic_offsets_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ComputingCard(modifier: Modifier = Modifier) {
    val spacing = dimensionResource(R.dimen.qrscan_spacing)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.qrscan_compare_raw_computing),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun NoteCard(text: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(dimensionResource(R.dimen.qrscan_spacing)),
        )
    }
}

private const val COMPUTING_KEY = "computing"
private const val NOTE_KEY = "note"
private const val IDENTICAL_KEY = "identical"
private const val BASELINE_KEY = "baseline"
private const val CANDIDATE_KEY = "candidate"

@Preview(showBackground = true)
@Composable
internal fun RawDiffViewPreview() {
    AppTheme {
        RawDiffView(
            baselinePayload = "0002010102125204078053033605802ID",
            candidatePayload = "0002010102115204059853033605802SG",
            state = RawDiffState.Ready(
                PayloadCharDiff(
                    baselineSpans = listOf(DiffSpan(11, 12), DiffSpan(18, 22), DiffSpan(31, 33)),
                    candidateSpans = listOf(DiffSpan(11, 12), DiffSpan(18, 22), DiffSpan(31, 33)),
                    truncated = false,
                ),
            ),
        )
    }
}
